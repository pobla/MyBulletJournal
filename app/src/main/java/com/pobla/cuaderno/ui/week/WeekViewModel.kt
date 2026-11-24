package com.pobla.cuaderno.ui.week

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.RangeData
import com.pobla.cuaderno.ui.components.TranscribeStatus
import com.pobla.cuaderno.ui.components.transcribeStatus
import com.pobla.cuaderno.ui.day.EntryUi
import com.pobla.cuaderno.util.startOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.pobla.cuaderno.model.Mood
import com.pobla.cuaderno.model.Weather

data class WeekHabit(val id: Long, val name: String)

data class WeekDayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val summary: String,
    val entries: List<EntryUi>,
    val doneHabitIds: Set<Long>,
    val status: TranscribeStatus,
    val weather: Weather? = null,
    val mood: Mood? = null,
)

data class WeekUiState(
    val weekStart: LocalDate,
    val isCurrentWeek: Boolean,
    val loaded: Boolean = false,
    val habits: List<WeekHabit> = emptyList(),
    val days: List<WeekDayUi> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val startFlow = savedStateHandle.getStateFlow(
        KEY_START,
        LocalDate.now().startOfWeek().toEpochDay(),
    )
    private val currentStart: LocalDate get() = LocalDate.ofEpochDay(startFlow.value)

    val uiState: StateFlow<WeekUiState> = startFlow
        .flatMapLatest { epochDay ->
            val start = LocalDate.ofEpochDay(epochDay)
            repository.observeRange(start, start.plusDays(6)).map { it.toUiState(start) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LocalDate.now().startOfWeek().let { WeekUiState(it, isCurrentWeek = true) },
        )

    fun previousWeek() = goTo(currentStart.minusWeeks(1))
    fun nextWeek() = goTo(currentStart.plusWeeks(1))
    fun thisWeek() = goTo(LocalDate.now().startOfWeek())

    private fun goTo(start: LocalDate) {
        savedStateHandle[KEY_START] = start.toEpochDay()
    }

    fun toggleHabit(date: LocalDate, habitId: Long) {
        appScope.launch { repository.toggleHabit(date, habitId) }
    }

    companion object {
        private const val KEY_START = "weekStart"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CuadernoApp
                WeekViewModel(app.container.repository, app.container.appScope, createSavedStateHandle())
            }
        }
    }
}

private fun RangeData.toUiState(start: LocalDate): WeekUiState {
    val today = LocalDate.now()
    val daysById = days.associateBy { it.epochDay }
    val entriesByDay = entries.groupBy { it.epochDay }
    val doneByDay = checks.filter { it.done }.groupBy({ it.epochDay }, { it.habitId })

    return WeekUiState(
        weekStart = start,
        isCurrentWeek = start == today.startOfWeek(),
        loaded = true,
        habits = habits.map { WeekHabit(it.id, it.name) },
        days = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            val key = date.toEpochDay()
            val day = daysById[key]
            val transcribed = day?.transcribed ?: false
            val last = day?.lastTranscribedAt
            val dayEntries = entriesByDay[key].orEmpty()
            val done = doneByDay[key].orEmpty().toSet()
            val summary = day?.summary.orEmpty()
            WeekDayUi(
                date = date,
                isToday = date == today,
                summary = summary,
                entries = dayEntries.map {
                    EntryUi(
                        id = it.id,
                        type = it.type,
                        status = it.status,
                        text = it.text,
                        changed = !transcribed && last != null && it.updatedAt > last,
                        migratedTo = it.migratedTo?.let(LocalDate::ofEpochDay),
                    )
                },
                doneHabitIds = done,
                weather = Weather.fromKey(day?.weather),
                mood = Mood.fromLevel(day?.mood),
                status = transcribeStatus(
                    hasContent = dayEntries.isNotEmpty() || done.isNotEmpty() || summary.isNotBlank() ||
                        day?.weather != null || day?.mood != null,
                    transcribed = transcribed,
                    lastTranscribedAt = last,
                ),
            )
        },
    )
}
