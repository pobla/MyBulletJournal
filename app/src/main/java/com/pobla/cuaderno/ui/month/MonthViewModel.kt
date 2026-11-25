package com.pobla.cuaderno.ui.month

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.db.MonthEntryEntity
import com.pobla.cuaderno.data.toYm
import com.pobla.cuaderno.data.ymToYearMonth
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.ui.components.TranscribeStatus
import com.pobla.cuaderno.ui.components.transcribeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import com.pobla.cuaderno.model.Mood
import com.pobla.cuaderno.model.Weather

enum class MonthMode { MONTH, FUTURE }

data class MonthEntryUi(
    val id: Long,
    val type: EntryType,
    val status: TaskStatus,
    val text: String,
    val migratedTo: YearMonth?,
)

/** Una línea del calendario del mes: eventos del día, frase y estado. */
data class MonthDayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val events: List<String>,
    val openTasks: Int,
    val summary: String,
    val status: TranscribeStatus,
    val weather: Weather? = null,
    val mood: Mood? = null,
)

data class MonthUiState(
    val month: YearMonth,
    val mode: MonthMode = MonthMode.MONTH,
    val isCurrentMonth: Boolean = true,
    val entries: List<MonthEntryUi> = emptyList(),
    val days: List<MonthDayUi> = emptyList(),
    /** Registro futuro: los próximos meses (desde el siguiente al actual) con sus entradas. */
    val future: List<Pair<YearMonth, List<MonthEntryUi>>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val ymFlow = savedStateHandle.getStateFlow(KEY_YM, YearMonth.now().toYm())
    private val modeFlow = savedStateHandle.getStateFlow(KEY_MODE, MonthMode.MONTH.name)
    private val current: YearMonth get() = ymToYearMonth(ymFlow.value)

    val uiState: StateFlow<MonthUiState> = combine(ymFlow, modeFlow) { ym, mode -> ym to mode }
        .flatMapLatest { (ym, mode) ->
            val month = ymToYearMonth(ym)
            val first = month.atDay(1)
            val last = month.atEndOfMonth()
            val futureFrom = YearMonth.now().plusMonths(1)
            val futureTo = futureFrom.plusMonths(FUTURE_MONTHS - 1)
            combine(
                repository.observeRange(first, last),
                repository.observeMonthEntries(month, month),
                repository.observeMonthEntries(futureFrom, futureTo),
            ) { range, monthEntries, futureEntries ->
                val today = LocalDate.now()
                val daysById = range.days.associateBy { it.epochDay }
                val entriesByDay = range.entries.groupBy { it.epochDay }
                val doneByDay = range.checks.filter { it.done }.groupBy { it.epochDay }
                MonthUiState(
                    month = month,
                    mode = MonthMode.valueOf(mode),
                    isCurrentMonth = month == YearMonth.now(),
                    entries = monthEntries.map { it.toUi() },
                    days = (1..month.lengthOfMonth()).map { dayOfMonth ->
                        val date = month.atDay(dayOfMonth)
                        val key = date.toEpochDay()
                        val day = daysById[key]
                        val dayEntries = entriesByDay[key].orEmpty()
                        val summary = day?.summary.orEmpty()
                        MonthDayUi(
                            date = date,
                            isToday = date == today,
                            events = dayEntries.filter { it.type == EntryType.EVENT }.map { it.text },
                            openTasks = dayEntries.count { it.type == EntryType.TASK && it.status == TaskStatus.OPEN },
                            summary = summary,
                            weather = Weather.fromKey(day?.weather),
                            mood = Mood.fromLevel(day?.mood),
                            status = transcribeStatus(
                                hasContent = dayEntries.isNotEmpty() || !doneByDay[key].isNullOrEmpty() || summary.isNotBlank() ||
                                    day?.weather != null || day?.mood != null,
                                transcribed = day?.transcribed ?: false,
                                lastTranscribedAt = day?.lastTranscribedAt,
                            ),
                        )
                    },
                    future = (0 until FUTURE_MONTHS).map { i ->
                        val m = futureFrom.plusMonths(i.toLong())
                        m to futureEntries.filter { it.ym == m.toYm() }.map { it.toUi() }
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthUiState(YearMonth.now()))

    fun previousMonth() = goTo(current.minusMonths(1))
    fun nextMonth() = goTo(current.plusMonths(1))
    fun thisMonth() = goTo(YearMonth.now())
    private fun goTo(month: YearMonth) { savedStateHandle[KEY_YM] = month.toYm() }

    fun setMode(mode: MonthMode) { savedStateHandle[KEY_MODE] = mode.name }

    fun add(month: YearMonth, type: EntryType, text: String) {
        appScope.launch { repository.addMonthEntry(month, type, text) }
    }

    fun cycle(id: Long) { appScope.launch { repository.cycleMonthEntry(id) } }

    fun update(id: Long, type: EntryType, status: TaskStatus, text: String) {
        appScope.launch { repository.updateMonthEntry(id, type, status, text) }
    }

    fun delete(id: Long) { appScope.launch { repository.deleteMonthEntry(id) } }

    fun moveToNextMonth(id: Long, from: YearMonth) {
        appScope.launch { repository.migrateMonthEntry(id, from.plusMonths(1)) }
    }

    companion object {
        private const val KEY_YM = "ym"
        private const val KEY_MODE = "modo"
        const val FUTURE_MONTHS = 6L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CuadernoApp
                MonthViewModel(app.container.repository, app.container.appScope, createSavedStateHandle())
            }
        }
    }
}

private fun MonthEntryEntity.toUi() =
    MonthEntryUi(id, type, status, text, migratedTo?.let(::ymToYearMonth))
