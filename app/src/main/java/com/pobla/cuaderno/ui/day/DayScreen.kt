package com.pobla.cuaderno.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.model.EntryShortcuts
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.model.symbolFor
import com.pobla.cuaderno.util.formatTimestamp
import com.pobla.cuaderno.util.longDate
import com.pobla.cuaderno.util.weekdayName

@Composable
fun DayRoute(viewModel: DayViewModel = viewModel(factory = DayViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DayScreen(
        state = state,
        onPrevious = viewModel::previousDay,
        onNext = viewModel::nextDay,
        onToday = viewModel::today,
        onToggleHabit = viewModel::toggleHabit,
        onSummaryChange = viewModel::onSummaryChange,
        onAddEntry = viewModel::addEntry,
        onCycleTask = viewModel::cycleTask,
        onUpdateEntry = viewModel::updateEntry,
        onDeleteEntry = viewModel::deleteEntry,
        onSetTranscribed = viewModel::setTranscribed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    state: DayUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onToggleHabit: (Long) -> Unit,
    onSummaryChange: (String) -> Unit,
    onAddEntry: (EntryType, String) -> Unit,
    onCycleTask: (Long) -> Unit,
    onUpdateEntry: (Long, EntryType, TaskStatus, String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onSetTranscribed: (Boolean) -> Unit,
) {
    var editing by remember { mutableStateOf<EntryUi?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.isToday) "Hoy · ${state.date.weekdayName()}" else state.date.weekdayName(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = state.date.longDate(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
                    }
                },
                actions = {
                    if (!state.isToday) {
                        TextButton(onClick = onToday) { Text("Hoy") }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Día siguiente")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = { EntryInputBar(onAdd = onAddEntry) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "status") {
                TranscribedCard(state = state, onSetTranscribed = onSetTranscribed)
            }

            item(key = "habits_title") { SectionTitle("Hábitos") }
            item(key = "habits") { HabitsRow(habits = state.habits, onToggle = onToggleHabit) }

            item(key = "summary_title") { SectionTitle("Frase del día") }
            item(key = "summary") {
                SummaryField(state = state, onChange = onSummaryChange)
            }

            item(key = "log_title") { SectionTitle("Registro") }
            if (state.loaded && state.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Nada apuntado. Escribe abajo: \"o \" para evento, \"- \" para nota; " +
                            "si no, es una tarea. Toca el símbolo de una tarea para cambiar su estado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(state.entries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    onCycle = { onCycleTask(entry.id) },
                    onEdit = { editing = entry },
                )
            }
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { type, status, text ->
                onUpdateEntry(entry.id, type, status, text)
                editing = null
            },
            onDelete = {
                onDeleteEntry(entry.id)
                editing = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TranscribedCard(state: DayUiState, onSetTranscribed: (Boolean) -> Unit) {
    if (!state.loaded) return
    if (!state.hasContent && !state.transcribed && state.lastTranscribedAt == null) return

    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        state.transcribed -> colors.secondaryContainer to colors.onSecondaryContainer
        state.changedSinceTranscribed -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                when {
                    state.transcribed -> {
                        Text("Pasado al cuaderno ✓", style = MaterialTheme.typography.titleSmall)
                        state.lastTranscribedAt?.let {
                            Text(formatTimestamp(it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.changedSinceTranscribed -> {
                        Text("Cambios sin pasar", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Lo resaltado es nuevo desde que lo pasaste",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> Text(
                        "Pendiente de pasar al cuaderno",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (state.transcribed) {
                OutlinedButton(onClick = { onSetTranscribed(false) }) { Text("Desmarcar") }
            } else {
                Button(onClick = { onSetTranscribed(true) }) { Text("Ya lo pasé") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitsRow(habits: List<HabitUi>, onToggle: (Long) -> Unit) {
    if (habits.isEmpty()) {
        Text(
            "No hay hábitos activos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        habits.forEach { habit ->
            FilterChip(
                selected = habit.done,
                onClick = { onToggle(habit.id) },
                label = { Text(habit.name) },
                leadingIcon = if (habit.done) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                border = if (habit.changed) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                } else if (habit.done) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                },
            )
        }
    }
}

@Composable
private fun SummaryField(state: DayUiState, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    // Borrador local mientras se escribe; se reinicia al cambiar de día.
    var draft by remember(state.date) { mutableStateOf<String?>(null) }
    val highlight = MaterialTheme.colorScheme.tertiary
    OutlinedTextField(
        value = draft ?: state.summary,
        onValueChange = {
            draft = it
            onChange(it)
        },
        placeholder = { Text("Una frase para resumir el día") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
        supportingText = if (state.summaryChanged) {
            { Text("Cambiada desde que la pasaste", color = highlight) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Composable
private fun EntryRow(entry: EntryUi, onCycle: () -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val finished = entry.type == EntryType.TASK && entry.status != TaskStatus.OPEN
    Surface(
        onClick = onEdit,
        color = if (entry.changed) colors.tertiaryContainer else Color.Transparent,
        contentColor = if (entry.changed) colors.onTertiaryContainer else colors.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = entry.type == EntryType.TASK, onClick = onCycle),
            ) {
                Text(
                    text = symbolFor(entry.type, entry.status),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (finished) colors.onSurfaceVariant else colors.primary,
                )
            }
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (finished) colors.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 10.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun EntryInputBar(onAdd: (EntryType, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(EntryType.TASK) }

    fun submit() {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            onAdd(type, clean)
            text = ""
            type = EntryType.TASK
        }
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Toca el símbolo para cambiar el tipo a mano.
            TextButton(onClick = { type = type.next() }) {
                Text(type.symbol, style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    val match = EntryShortcuts.match(value)
                    if (match != null) {
                        type = match.type
                        text = match.rest
                    } else {
                        text = value
                    }
                },
                placeholder = { Text("${type.label}…  (o evento · - nota)") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Añadir")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditEntryDialog(
    entry: EntryUi,
    onDismiss: () -> Unit,
    onSave: (EntryType, TaskStatus, String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by rememberSaveable(entry.id) { mutableStateOf(entry.text) }
    var type by rememberSaveable(entry.id) { mutableStateOf(entry.type) }
    var status by rememberSaveable(entry.id) { mutableStateOf(entry.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar entrada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text("${option.symbol} ${option.label}") },
                        )
                    }
                }
                if (type == EntryType.TASK) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskStatus.entries.forEach { option ->
                            FilterChip(
                                selected = status == option,
                                onClick = { status = option },
                                label = { Text("${option.symbol} ${option.label}") },
                            )
                        }
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Borrar entrada", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(type, status, text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
