@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.pobla.cuaderno.ui.day

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.pobla.cuaderno.ui.components.SectionTitle
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
    val motion = MaterialTheme.motionScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // El título se desliza en vertical al cambiar de día.
                    AnimatedContent(
                        targetState = state.date,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInVertically(motion.defaultSpatialSpec()) { it * dir } + fadeIn(motion.defaultEffectsSpec()))
                                .togetherWith(
                                    slideOutVertically(motion.defaultSpatialSpec()) { -it * dir } + fadeOut(motion.fastEffectsSpec()),
                                )
                        },
                        label = "titulo",
                    ) { date ->
                        val isToday = date == java.time.LocalDate.now()
                        Column {
                            Text(
                                text = if (isToday) "Hoy · ${date.weekdayName()}" else date.weekdayName(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = date.longDate(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = !state.isToday,
                        enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
                    ) {
                        FilledTonalButton(onClick = onToday) { Text("Hoy") }
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
        // Contenido del día: se desliza a izquierda/derecha al cambiar de fecha.
        AnimatedContent(
            targetState = state,
            contentKey = { it.date },
            transitionSpec = {
                val dir = if (targetState.date > initialState.date) 1 else -1
                (slideInHorizontally(motion.defaultSpatialSpec()) { w -> dir * w / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(motion.defaultSpatialSpec()) { w -> -dir * w / 3 } + fadeOut(motion.fastEffectsSpec()),
                    )
                    .using(SizeTransform(clip = false))
            },
            modifier = Modifier
                .fillMaxSize()
                .swipeToChangeDay(onPrevious = onPrevious, onNext = onNext),
            label = "dia",
        ) { dayState ->
            DayContent(
                state = dayState,
                padding = padding,
                onToggleHabit = onToggleHabit,
                onSummaryChange = onSummaryChange,
                onCycleTask = onCycleTask,
                onEdit = { editing = it },
                onSetTranscribed = onSetTranscribed,
            )
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

/** Deslizar a la derecha = día anterior; a la izquierda = día siguiente. */
@Composable
private fun Modifier.swipeToChangeDay(onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    val prev by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    return pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total > threshold -> {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        prev()
                    }
                    total < -threshold -> {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        next()
                    }
                }
            },
        ) { _, dragAmount -> total += dragAmount }
    }
}

@Composable
private fun DayContent(
    state: DayUiState,
    padding: PaddingValues,
    onToggleHabit: (Long) -> Unit,
    onSummaryChange: (String) -> Unit,
    onCycleTask: (Long) -> Unit,
    onEdit: (EntryUi) -> Unit,
    onSetTranscribed: (Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "status") {
            TranscribedCard(state = state, onSetTranscribed = onSetTranscribed)
        }

        item(key = "habits_title") { SectionTitle("Hábitos") }
        item(key = "habits") { HabitsRow(habits = state.habits, onToggle = onToggleHabit) }

        item(key = "summary_title") { SectionTitle("Frase del día") }
        item(key = "summary") { SummaryField(state = state, onChange = onSummaryChange) }

        item(key = "log_title") { SectionTitle("Registro") }
        if (state.loaded && state.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Nada apuntado. Escribe abajo: \"o \" para evento, \"- \" para nota; " +
                        "si no, es una tarea. Toca el símbolo de una tarea para cambiar su estado. " +
                        "Desliza a los lados para cambiar de día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .animateItem()
                        .padding(vertical = 8.dp),
                )
            }
        }
        items(state.entries, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                onCycle = { onCycleTask(entry.id) },
                onEdit = { onEdit(entry) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun TranscribedCard(state: DayUiState, onSetTranscribed: (Boolean) -> Unit) {
    val visible = state.loaded && (state.hasContent || state.transcribed || state.lastTranscribedAt != null)
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
    ) {
        val container by animateColorAsState(
            targetValue = when {
                state.transcribed -> colors.secondaryContainer
                state.changedSinceTranscribed -> colors.tertiaryContainer
                else -> colors.surfaceContainerHigh
            },
            animationSpec = motion.defaultEffectsSpec(),
            label = "tarjeta",
        )
        val content = when {
            state.transcribed -> colors.onSecondaryContainer
            state.changedSinceTranscribed -> colors.onTertiaryContainer
            else -> colors.onSurfaceVariant
        }
        Surface(
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .animateContentSize(motion.defaultSpatialSpec()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = when {
                        state.transcribed -> 0
                        state.changedSinceTranscribed -> 1
                        else -> 2
                    },
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 })
                            .togetherWith(fadeOut(motion.fastEffectsSpec()))
                    },
                    modifier = Modifier.weight(1f),
                    label = "estado",
                ) { status ->
                    Column {
                        when (status) {
                            0 -> {
                                Text("Pasado al cuaderno ✓", style = MaterialTheme.typography.titleMedium)
                                state.lastTranscribedAt?.let {
                                    Text(formatTimestamp(it), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            1 -> {
                                Text("Cambios sin pasar", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Lo resaltado es nuevo desde que lo pasaste",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            else -> Text(
                                "Pendiente de pasar al cuaderno",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (state.transcribed) {
                    TextButton(onClick = { onSetTranscribed(false) }) { Text("Desmarcar") }
                } else {
                    Button(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSetTranscribed(true)
                    }) { Text("Ya lo pasé") }
                }
            }
        }
    }
}

@Composable
private fun HabitsRow(habits: List<HabitUi>, onToggle: (Long) -> Unit) {
    if (habits.isEmpty()) {
        Text(
            "No hay hábitos activos. Añádelos en Ajustes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        habits.forEach { habit ->
            FilterChip(
                selected = habit.done,
                onClick = {
                    haptics.performHapticFeedback(
                        if (habit.done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    onToggle(habit.id)
                },
                label = { Text(habit.name) },
                leadingIcon = {
                    AnimatedVisibility(
                        visible = habit.done,
                        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                shape = CircleShape,
                border = if (habit.changed) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                } else if (habit.done) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                modifier = Modifier.animateContentSize(motion.fastSpatialSpec()),
            )
        }
    }
}

@Composable
private fun pillFieldColors(container: Color) = TextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = container,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun SummaryField(state: DayUiState, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    // Borrador local mientras se escribe; se reinicia al cambiar de día.
    var draft by remember(state.date) { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    TextField(
        value = draft ?: state.summary,
        onValueChange = {
            draft = it
            onChange(it)
        },
        placeholder = { Text("Una frase para resumir el día") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
        shape = MaterialTheme.shapes.large,
        colors = pillFieldColors(
            if (state.summaryChanged) colors.tertiaryContainer else colors.surfaceContainerHigh,
        ),
        supportingText = if (state.summaryChanged) {
            { Text("Cambiada desde que la pasaste", color = colors.tertiary) }
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
private fun EntryRow(
    entry: EntryUi,
    onCycle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val finished = entry.type == EntryType.TASK && entry.status != TaskStatus.OPEN
    val background by animateColorAsState(
        if (entry.changed) colors.tertiaryContainer else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "fondo",
    )
    val textColor by animateColorAsState(
        when {
            finished -> colors.onSurfaceVariant
            entry.changed -> colors.onTertiaryContainer
            else -> colors.onSurface
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "texto",
    )
    Surface(
        onClick = onEdit,
        color = background,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = entry.type == EntryType.TASK) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onCycle()
                    },
            ) {
                // El símbolo "salta" al cambiar: • → × → >
                AnimatedContent(
                    targetState = symbolFor(entry.type, entry.status),
                    transitionSpec = {
                        (scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f) + fadeIn(motion.fastEffectsSpec()))
                            .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "simbolo",
                ) { symbol ->
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (finished) colors.onSurfaceVariant else colors.primary,
                    )
                }
            }
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun EntryInputBar(onAdd: (EntryType, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(EntryType.TASK) }
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme

    fun submit() {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onAdd(type, clean)
            text = ""
            type = EntryType.TASK
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Toca el símbolo para cambiar el tipo a mano.
            FilledTonalIconButton(onClick = { type = type.next() }) {
                AnimatedContent(
                    targetState = type,
                    transitionSpec = {
                        (scaleIn(motion.fastSpatialSpec(), initialScale = 0.3f) + fadeIn(motion.fastEffectsSpec()))
                            .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.3f) + fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "tipo",
                ) { t ->
                    Text(t.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            TextField(
                value = text,
                onValueChange = { value ->
                    val match = EntryShortcuts.match(value)
                    if (match != null) {
                        if (match.type != type) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        type = match.type
                        text = match.rest
                    } else {
                        text = value
                    }
                },
                placeholder = { Text("${type.label}…  (o evento · - nota)") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = MaterialTheme.shapes.extraLarge,
                colors = pillFieldColors(MaterialTheme.colorScheme.surfaceContainerHighest),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Añadir")
            }
        }
    }
}

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
    val motion = MaterialTheme.motionScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar entrada") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.animateContentSize(motion.defaultSpatialSpec()),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text("${option.symbol} ${option.label}") },
                            shape = CircleShape,
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
                                shape = CircleShape,
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
            Button(
                onClick = { onSave(type, status, text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
