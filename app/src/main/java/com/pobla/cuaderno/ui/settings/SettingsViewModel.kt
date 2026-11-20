package com.pobla.cuaderno.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.db.HabitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

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
                SettingsViewModel(app.container.repository, app.container.appScope)
            }
        }
    }
}
