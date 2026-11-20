package com.pobla.cuaderno.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.JournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PendingDayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val summary: String,
    val entryCount: Int,
    val habitCount: Int,
    /** Ya se había pasado y luego cambió. */
    val hasChanges: Boolean,
)

data class PendingUiState(
    val loaded: Boolean = false,
    val days: List<PendingDayUi> = emptyList(),
)

class PendingViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val uiState: StateFlow<PendingUiState> = repository.observePendingDays()
        .map { rows ->
            val today = LocalDate.now()
            PendingUiState(
                loaded = true,
                days = rows.map {
                    val date = LocalDate.ofEpochDay(it.epochDay)
                    PendingDayUi(
                        date = date,
                        isToday = date == today,
                        summary = it.summary,
                        entryCount = it.entryCount,
                        habitCount = it.habitCount,
                        hasChanges = it.lastTranscribedAt != null,
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingUiState())

    fun markTranscribed(date: LocalDate) {
        appScope.launch { repository.setTranscribed(date, true) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CuadernoApp
                PendingViewModel(app.container.repository, app.container.appScope)
            }
        }
    }
}
