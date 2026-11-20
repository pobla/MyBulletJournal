package com.pobla.cuaderno.ui.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.model.symbolFor
import com.pobla.cuaderno.ui.components.SectionTitle
import com.pobla.cuaderno.ui.components.StatusPill
import com.pobla.cuaderno.util.weekRangeLabel
import com.pobla.cuaderno.util.weekdayDayMonth
import com.pobla.cuaderno.util.weekdayLetter
import java.time.LocalDate

private const val MAX_ENTRIES_PREVIEW = 6

@Composable
fun WeekRoute(
    onOpenDay: (LocalDate) -> Unit,
    viewModel: WeekViewModel = viewModel(factory = WeekViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeekScreen(
        state = state,
        onPrevious = viewModel::previousWeek,
        onNext = viewModel::nextWeek,
        onThisWeek = viewModel::thisWeek,
        onToggleHabit = viewModel::toggleHabit,
        onOpenDay = onOpenDay,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    state: WeekUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onToggleHabit: (LocalDate, Long) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.isCurrentWeek) "Esta semana" else "Semana",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            weekRangeLabel(state.weekStart),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semana anterior")
                    }
                },
                actions = {
                    if (!state.isCurrentWeek) {
                        TextButton(onClick = onThisWeek) { Text("Hoy") }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semana siguiente")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.habits.isNotEmpty() && state.days.isNotEmpty()) {
                item(key = "habits_title") { SectionTitle("Hábitos") }
                item(key = "habits") {
                    HabitGrid(state = state, onToggle = onToggleHabit)
                }
            }
            item(key = "days_title") { SectionTitle("Días") }
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                WeekDayCard(day = day, onClick = { onOpenDay(day.date) })
            }
        }
    }
}

@Composable
private fun HabitGrid(state: WeekUiState, onToggle: (LocalDate, Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
            // Cabecera: L M X J V S D con el número del día
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(NAME_WIDTH))
                state.days.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tint = if (day.isToday) colors.primary else colors.onSurfaceVariant
                        Text(day.date.weekdayLetter(), style = MaterialTheme.typography.labelMedium, color = tint)
                        Text(
                            "${day.date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            state.habits.forEach { habit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(NAME_WIDTH).padding(end = 4.dp),
                    )
                    state.days.forEach { day ->
                        val done = habit.id in day.doneHabitIds
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clickable { onToggle(day.date, habit.id) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (done) {
                                            Modifier.background(colors.primary)
                                        } else {
                                            Modifier.border(BorderStroke(1.dp, colors.outlineVariant), CircleShape)
                                        },
                                    ),
                            ) {
                                if (done) {
                                    Text(
                                        "✓",
                                        color = colors.onPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val NAME_WIDTH = 88.dp

@Composable
private fun WeekDayCard(day: WeekDayUi, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = if (day.isToday) BorderStroke(1.5.dp, colors.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.date.weekdayDayMonth(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (day.isToday) colors.primary else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(day.status)
            }
            if (day.summary.isNotBlank()) {
                Text(
                    "“${day.summary}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (day.entries.isEmpty() && day.summary.isBlank()) {
                Text(
                    "Sin apuntes",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            day.entries.take(MAX_ENTRIES_PREVIEW).forEach { entry ->
                val finished = entry.type == EntryType.TASK && entry.status != TaskStatus.OPEN
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .then(
                            if (entry.changed) {
                                Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(colors.tertiaryContainer)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Text(
                        symbolFor(entry.type, entry.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (finished) colors.onSurfaceVariant else colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (finished) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (day.entries.size > MAX_ENTRIES_PREVIEW) {
                Text(
                    "y ${day.entries.size - MAX_ENTRIES_PREVIEW} más…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 20.dp),
                )
            }
        }
    }
}
