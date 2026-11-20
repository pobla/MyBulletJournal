package com.pobla.cuaderno.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.ImportItem
import com.pobla.cuaderno.model.ImportParser
import com.pobla.cuaderno.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

class ImportViewModel(
    private val repository: JournalRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val habits: StateFlow<List<HabitEntity>> = repository.observeActiveHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Analiza el texto y reconoce los hábitos marcados como hechos ("× Creatina"). */
    fun parse(text: String, defaultDate: LocalDate, habits: List<HabitEntity>): List<ImportItem> {
        val byName = habits.associateBy { ImportParser.normalize(it.name.trim()) }
        return ImportParser.parse(text, defaultDate, LocalDate.now()).map { item ->
            val habit = if (item.type == EntryType.TASK && item.status == TaskStatus.DONE) {
                byName[ImportParser.normalize(item.text.trim())]
            } else {
                null
            }
            item.copy(habitId = habit?.id)
        }
    }

    fun import(items: List<ImportItem>, onDone: () -> Unit) {
        appScope.launch {
            repository.importItems(items)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CuadernoApp
                ImportViewModel(app.container.repository, app.container.appScope)
            }
        }
    }
}
