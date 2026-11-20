package com.pobla.cuaderno.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.ui.components.SectionTitle

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    SettingsScreen(
        habits = habits,
        onAdd = viewModel::addHabit,
        onRename = viewModel::renameHabit,
        onArchive = viewModel::archiveHabit,
        onMove = viewModel::moveHabit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    habits: List<HabitEntity>,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onArchive: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
) {
    var renaming by remember { mutableStateOf<HabitEntity?>(null) }
    var archiving by remember { mutableStateOf<HabitEntity?>(null) }
    var newHabit by rememberSaveable { mutableStateOf("") }

    fun submitNew() {
        if (newHabit.isNotBlank()) {
            onAdd(newHabit)
            newHabit = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "habits_title") { SectionTitle("Hábitos diarios") }
            item(key = "habits_hint") {
                Text(
                    "Toca un hábito para renombrarlo. Al quitarlo se conserva lo que ya marcaste en días pasados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(habits, key = { _, h -> h.id }) { index, habit ->
                HabitRow(
                    habit = habit,
                    canMoveUp = index > 0,
                    canMoveDown = index < habits.lastIndex,
                    onClick = { renaming = habit },
                    onMoveUp = { onMove(habit.id, -1) },
                    onMoveDown = { onMove(habit.id, 1) },
                    onArchive = { archiving = habit },
                )
            }
            item(key = "add") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = newHabit,
                        onValueChange = { newHabit = it },
                        placeholder = { Text("Nuevo hábito") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitNew() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { submitNew() }, enabled = newHabit.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir hábito")
                    }
                }
            }
        }
    }

    renaming?.let { habit ->
        var name by rememberSaveable(habit.id) { mutableStateOf(habit.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar hábito") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(habit.id, name)
                        renaming = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }

    archiving?.let { habit ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text("Quitar \"${habit.name}\"") },
            text = { Text("Dejará de aparecer en los días nuevos. Lo que ya marcaste se conserva.") },
            confirmButton = {
                TextButton(onClick = {
                    onArchive(habit.id)
                    archiving = null
                }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HabitRow(
    habit: HabitEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar")
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Filled.Delete, contentDescription = "Quitar")
            }
        }
    }
}
