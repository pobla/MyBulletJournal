package com.pobla.cuaderno.ui.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.DayData
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.pobla.cuaderno.model.Mood
import com.pobla.cuaderno.model.Weather

data class HabitUi(val id: Long, val name: String, val done: Boolean, val changed: Boolean)

data class EntryUi(
    val id: Long,
    val type: EntryType,
    val status: TaskStatus,
    val text: String,
    val changed: Boolean,
    /** Día al que se migró (si es una tarea ">"). */
    val migratedTo: LocalDate? = null,
)

data class DayUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val loaded: Boolean = false,
    val habits: List<HabitUi> = emptyList(),
    val entries: List<EntryUi> = emptyList(),
    val summary: String = "",
    val summaryChanged: Boolean = false,
    val transcribed: Boolean = false,
    val lastTranscribedAt: Long? = null,
    val hasContent: Boolean = false,
    val weather: Weather? = null,
    val mood: Mood? = null,
    /** Tiempo o ánimo cambiados desde que se pasó al cuaderno. */
    val trackersChanged: Boolean = false,
) {
    /** Se pasó al cuaderno alguna vez y después hubo cambios. */
    val changedSinceTranscribed: Boolean get() = !transcribed && lastTranscribedAt != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dateFlow = savedStateHandle.getStateFlow(KEY_DATE, LocalDate.now().toEpochDay())
    private val currentDate: LocalDate get() = LocalDate.ofEpochDay(dateFlow.value)

    val uiState: StateFlow<DayUiState> = dateFlow
        .flatMapLatest { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            repository.observeDay(date).map { it.toUiState(date) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LocalDate.now().let { DayUiState(date = it, isToday = true) },
        )

    // Navegación
    fun previousDay() = goTo(currentDate.minusDays(1))
    fun nextDay() = goTo(currentDate.plusDays(1))
    fun today() = goTo(LocalDate.now())

    fun goTo(date: LocalDate) {
        flushSummary()
        savedStateHandle[KEY_DATE] = date.toEpochDay()
    }

    // Entradas
    fun addEntry(type: EntryType, text: String) {
        val date = currentDate
        appScope.launch { repository.addEntry(date, type, text) }
    }

    fun cycleTask(entryId: Long) {
        appScope.launch { repository.cycleTaskStatus(entryId) }
    }

    fun updateEntry(entryId: Long, type: EntryType, status: TaskStatus, text: String) {
        appScope.launch { repository.updateEntry(entryId, type, status, text) }
    }

    fun migrateTask(entryId: Long, toDate: LocalDate) {
        appScope.launch { repository.migrateTask(entryId, toDate) }
    }

    fun deleteEntry(entryId: Long) {
        appScope.launch { repository.deleteEntry(entryId) }
    }

    // Hábitos
    fun toggleHabit(habitId: Long) {
        val date = currentDate
        appScope.launch { repository.toggleHabit(date, habitId) }
    }

    // Tiempo y ánimo
    fun setWeather(weather: Weather?) {
        val date = currentDate
        appScope.launch { repository.setWeather(date, weather) }
    }

    fun setMood(mood: Mood?) {
        val date = currentDate
        appScope.launch { repository.setMood(date, mood) }
    }

    // Cuaderno
    fun setTranscribed(value: Boolean) {
        flushSummary()
        val date = currentDate
        appScope.launch { repository.setTranscribed(date, value) }
    }

    // Resumen: se guarda con un pequeño retardo para no escribir en cada tecla.
    private var summaryJob: Job? = null
    private var pendingSummary: Pair<LocalDate, String>? = null

    fun onSummaryChange(text: String) {
        pendingSummary = currentDate to text
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            delay(600)
            flushSummary()
        }
    }

    private fun flushSummary() {
        summaryJob?.cancel()
        val (date, text) = pendingSummary ?: return
        pendingSummary = null
        appScope.launch { repository.setSummary(date, text) }
    }

    override fun onCleared() {
        flushSummary()
    }

    companion object {
        private const val KEY_DATE = "epochDay"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CuadernoApp
                DayViewModel(
                    repository = app.container.repository,
                    appScope = app.container.appScope,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

private fun DayData.toUiState(date: LocalDate): DayUiState {
    val transcribed = day?.transcribed ?: false
    val last = day?.lastTranscribedAt
    // Resaltamos lo modificado después de la última vez que se pasó al cuaderno.
    fun isNew(updatedAt: Long) = !transcribed && last != null && updatedAt > last

    val checksByHabit = checks.associateBy { it.habitId }
    val summary = day?.summary.orEmpty()
    return DayUiState(
        date = date,
        isToday = date == LocalDate.now(),
        loaded = true,
        habits = habits.map { habit ->
            val check = checksByHabit[habit.id]
            HabitUi(
                id = habit.id,
                name = habit.name,
                done = check?.done ?: false,
                changed = check != null && isNew(check.updatedAt),
            )
        },
        entries = entries.map {
            EntryUi(
                it.id, it.type, it.status, it.text,
                changed = isNew(it.updatedAt),
                migratedTo = it.migratedTo?.let(LocalDate::ofEpochDay),
            )
        },
        summary = summary,
        summaryChanged = day != null && isNew(day.summaryUpdatedAt),
        transcribed = transcribed,
        lastTranscribedAt = last,
        hasContent = entries.isNotEmpty() || checks.any { it.done } || summary.isNotBlank() ||
            day?.weather != null || day?.mood != null,
        weather = Weather.fromKey(day?.weather),
        mood = Mood.fromLevel(day?.mood),
        trackersChanged = day != null && isNew(day.trackersUpdatedAt),
    )
}
