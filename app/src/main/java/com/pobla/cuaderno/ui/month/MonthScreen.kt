@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.pobla.cuaderno.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.R
import com.pobla.cuaderno.model.EntryShortcuts
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.model.symbolFor
import com.pobla.cuaderno.ui.components.SectionTitle
import com.pobla.cuaderno.ui.components.TranscribeStatus
import com.pobla.cuaderno.util.monthName
import com.pobla.cuaderno.util.monthYear
import com.pobla.cuaderno.util.weekdayLetter
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthRoute(
    onOpenDay: (LocalDate) -> Unit,
    viewModel: MonthViewModel = viewModel(factory = MonthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Diálogo de añadir (mes) o editar (entrada)
    var adding by remember { mutableStateOf<YearMonth?>(null) }
    var editing by remember { mutableStateOf<Pair<YearMonth, MonthEntryUi>?>(null) }

    MonthScreen(
        state = state,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onThisMonth = viewModel::thisMonth,
        onModeChange = viewModel::setMode,
        onOpenDay = onOpenDay,
        onCycle = viewModel::cycle,
        onAdd = { adding = it },
        onEdit = { month, entry -> editing = month to entry },
    )

    adding?.let { month ->
        MonthEntryDialog(
            title = stringResource(R.string.add_to_month, month.monthName()),
            entry = null,
            onDismiss = { adding = null },
            onSave = { type, _, text ->
                viewModel.add(month, type, text)
                adding = null
            },
        )
    }
    editing?.let { (month, entry) ->
        MonthEntryDialog(
            title = stringResource(R.string.edit_entry),
            entry = entry,
            onDismiss = { editing = null },
            onSave = { type, status, text ->
                viewModel.update(entry.id, type, status, text)
                editing = null
            },
            onDelete = {
                viewModel.delete(entry.id)
                editing = null
            },
            onMoveNext = {
                viewModel.moveToNextMonth(entry.id, month)
                editing = null
            },
            nextMonthName = month.plusMonths(1).monthName(),
        )
    }
}

@Composable
fun MonthScreen(
    state: MonthUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisMonth: () -> Unit,
    onModeChange: (MonthMode) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCycle: (Long) -> Unit,
    onAdd: (YearMonth) -> Unit,
    onEdit: (YearMonth, MonthEntryUi) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val isMonth = state.mode == MonthMode.MONTH
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isMonth) state.month.monthYear() else stringResource(R.string.future_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (isMonth) {
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
                        }
                    }
                },
                actions = {
                    if (isMonth) {
                        AnimatedVisibility(
                            visible = !state.isCurrentMonth,
                            enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
                            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
                        ) {
                            FilledTonalButton(onClick = onThisMonth) { Text(stringResource(R.string.today)) }
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
                        }
                    }
                },
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
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    MonthMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, MonthMode.entries.size),
                        ) {
                            Text(stringResource(if (mode == MonthMode.MONTH) R.string.month_mode else R.string.future_mode))
                        }
                    }
                }
            }

            if (isMonth) {
                item(key = "tasks_title") {
                    TitleWithAdd(stringResource(R.string.month_tasks)) { onAdd(state.month) }
                }
                if (state.entries.isEmpty()) {
                    item(key = "tasks_empty") { EmptyText() }
                }
                items(state.entries, key = { "m_${it.id}" }) { entry ->
                    MonthEntryRow(
                        entry = entry,
                        onCycle = { onCycle(entry.id) },
                        onEdit = { onEdit(state.month, entry) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "cal_title") { SectionTitle(stringResource(R.string.month_calendar)) }
                items(state.days, key = { "d_${it.date.toEpochDay()}" }) { day ->
                    CalendarLine(day = day, onClick = { onOpenDay(day.date) })
                }
            } else {
                state.future.forEach { (month, entries) ->
                    item(key = "f_title_${month}") {
                        TitleWithAdd(month.monthYear()) { onAdd(month) }
                    }
                    if (entries.isEmpty()) {
                        item(key = "f_empty_${month}") { EmptyText() }
                    }
                    items(entries, key = { "f_${it.id}" }) { entry ->
                        MonthEntryRow(
                            entry = entry,
                            onCycle = { onCycle(entry.id) },
                            onEdit = { onEdit(month, entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleWithAdd(title: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
        }
    }
}

@Composable
private fun EmptyText() {
    Text(
        stringResource(R.string.month_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun MonthEntryRow(
    entry: MonthEntryUi,
    onCycle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val finished = entry.type == EntryType.TASK && entry.status != TaskStatus.OPEN
    Surface(onClick = onEdit, shape = MaterialTheme.shapes.medium, color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = entry.type == EntryType.TASK) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onCycle()
                    },
            ) {
                AnimatedContent(
                    targetState = symbolFor(entry.type, entry.status),
                    transitionSpec = {
                        (scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f) + fadeIn(motion.fastEffectsSpec()))
                            .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "simbolo",
                ) { symbol ->
                    Text(
                        symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (finished) colors.onSurfaceVariant else colors.primary,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(top = 11.dp, bottom = 11.dp, end = 12.dp)) {
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (finished) colors.onSurfaceVariant else colors.onSurface,
                )
                entry.migratedTo?.let { to ->
                    Text(
                        stringResource(R.string.migrated_to, to.monthName()),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/** Línea del calendario: "23 L  ○ Dentista · ○ Cena" + frase; toca para abrir el día. */
@Composable
private fun CalendarLine(day: MonthDayUi, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val weekend = day.date.dayOfWeek.value >= 6
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (day.isToday) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface,
        border = if (day.isToday) BorderStroke(1.5.dp, colors.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${day.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (day.isToday) colors.primary else colors.onSurface,
                )
                Text(
                    day.date.weekdayLetter(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (weekend) colors.tertiary else colors.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f).padding(top = 2.dp, start = 4.dp)) {
                if (day.events.isNotEmpty()) {
                    Text(
                        day.events.joinToString("  ·  ") { "○ $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (day.summary.isNotBlank()) {
                    Text(
                        "“${day.summary}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (day.openTasks > 0) {
                    Text(
                        "• ${day.openTasks}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            val dotColor = when (day.status) {
                TranscribeStatus.PENDING -> colors.outline
                TranscribeStatus.CHANGED -> colors.tertiary
                else -> null
            }
            if (dotColor != null) {
                // Punto de "pendiente de pasar al cuaderno" (naranja si hay cambios)
                Surface(color = dotColor, shape = CircleShape, modifier = Modifier.padding(top = 8.dp, end = 4.dp).size(8.dp)) {}
            }
        }
    }
}

/** Añadir o editar una entrada del mes. Al editar una tarea: pasar al mes siguiente o borrar. */
@Composable
private fun MonthEntryDialog(
    title: String,
    entry: MonthEntryUi?,
    onDismiss: () -> Unit,
    onSave: (EntryType, TaskStatus, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onMoveNext: (() -> Unit)? = null,
    nextMonthName: String? = null,
) {
    var text by rememberSaveable(entry?.id) { mutableStateOf(entry?.text.orEmpty()) }
    var type by rememberSaveable(entry?.id) { mutableStateOf(entry?.type ?: EntryType.TASK) }
    var status by rememberSaveable(entry?.id) { mutableStateOf(entry?.status ?: TaskStatus.OPEN) }

    fun submit() {
        if (text.isNotBlank()) onSave(type, status, text.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        val match = EntryShortcuts.match(value)
                        if (match != null && entry == null) {
                            type = match.type
                            text = match.rest
                        } else {
                            text = value
                        }
                    },
                    placeholder = { Text(stringResource(R.string.input_placeholder, stringResource(type.labelRes))) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text("${option.symbol} ${stringResource(option.labelRes)}") },
                            shape = CircleShape,
                        )
                    }
                }
                if (entry != null && type == EntryType.TASK) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskStatus.entries.forEach { option ->
                            FilterChip(
                                selected = status == option,
                                onClick = { status = option },
                                label = { Text("${option.symbol} ${stringResource(option.labelRes)}") },
                                shape = CircleShape,
                            )
                        }
                    }
                }
                if (onMoveNext != null && nextMonthName != null) {
                    FilledTonalButton(onClick = onMoveNext) {
                        Text(stringResource(R.string.move_to_month, nextMonthName))
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete_entry), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
