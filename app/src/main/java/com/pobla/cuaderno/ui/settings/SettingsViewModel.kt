package com.pobla.cuaderno.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.AppSettings
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.ReminderKind
import com.pobla.cuaderno.data.ReminderSetting
import com.pobla.cuaderno.reminders.ReminderScheduler
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.update.UpdateManager
import com.pobla.cuaderno.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
    private val settings: AppSettings,
    val updates: UpdateManager,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val reminders: StateFlow<Map<ReminderKind, ReminderSetting>> = settings.reminders

    fun setReminderEnabled(kind: ReminderKind, enabled: Boolean) {
        settings.setReminder(kind, settings.reminder(kind).copy(enabled = enabled))
        scheduler.reschedule(kind)
    }

    fun setReminderDays(kind: ReminderKind, days: Set<java.time.DayOfWeek>) {
        if (days.isEmpty()) return
        settings.setReminder(kind, settings.reminder(kind).copy(days = days))
        scheduler.reschedule(kind)
    }

    fun setReminderTime(kind: ReminderKind, hour: Int, minute: Int) {
        settings.setReminder(kind, settings.reminder(kind).copy(minutesOfDay = hour * 60 + minute))
        scheduler.reschedule(kind)
    }

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
    val updateState: StateFlow<UpdateState> = updates.state

    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun checkForUpdates() = updates.check()
    fun downloadUpdate() = updates.download()

    val habits: StateFlow<List<HabitEntity>> = repository.observeActiveHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addHabit(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        appScope.launch { repository.addHabit(clean) }
    }

    fun renameHabit(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        appScope.launch { repository.renameHabit(id, clean) }
    }

    fun archiveHabit(id: Long) {
        appScope.launch { repository.archiveHabit(id) }
    }

    fun moveHabit(id: Long, offset: Int) {
        appScope.launch { repository.moveHabit(id, offset) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CuadernoApp
                SettingsViewModel(
                    repository = app.container.repository,
                    appScope = app.container.appScope,
                    settings = app.container.settings,
                    updates = app.container.updates,
                    scheduler = app.container.reminders,
                )
            }
        }
    }
}
