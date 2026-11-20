@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.res.stringResource
import com.pobla.cuaderno.R

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
                            stringResource(if (state.isCurrentWeek) R.string.this_week else R.string.week),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_week))
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = !state.isCurrentWeek,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
                    ) {
                        FilledTonalButton(onClick = onThisWeek) { Text(stringResource(R.string.today)) }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_week))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = state,
            contentKey = { it.weekStart },
            transitionSpec = {
                val dir = if (targetState.weekStart > initialState.weekStart) 1 else -1
                (slideInHorizontally(motion.defaultSpatialSpec()) { w -> dir * w / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(motion.defaultSpatialSpec()) { w -> -dir * w / 3 } + fadeOut(motion.fastEffectsSpec()),
                    )
            },
            label = "semana",
        ) { state ->
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
                item(key = "habits_title") { SectionTitle(stringResource(R.string.section_habits)) }
                item(key = "habits") {
                    HabitGrid(state = state, onToggle = onToggleHabit)
                }
            }
            item(key = "days_title") { SectionTitle(stringResource(R.string.section_days)) }
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                WeekDayCard(day = day, onClick = { onOpenDay(day.date) })
            }
        }
        }
    }
}

@Composable
private fun HabitGrid(state: WeekUiState, onToggle: (LocalDate, Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
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
                                .height(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(
                                        if (done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                    )
                                    onToggle(day.date, habit.id)
                                },
                        ) {
                            HabitDot(done = done)
                        }
                    }
                }
            }
        }
    }
}

/** Círculo de la rejilla: se rellena con un pequeño rebote al marcarlo. */
@Composable
private fun HabitDot(done: Boolean) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val fill by animateColorAsState(
        if (done) colors.primary else Color.Transparent,
        animationSpec = motion.fastEffectsSpec(),
        label = "relleno",
    )
    val scale by animateFloatAsState(
        if (done) 1f else 0.85f,
        animationSpec = motion.fastSpatialSpec(),
        label = "escala",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(1.dp, if (done) Color.Transparent else colors.outlineVariant), CircleShape),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = done,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Text(
                "✓",
                color = colors.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val NAME_WIDTH = 88.dp

@Composable
private fun WeekDayCard(day: WeekDayUi, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (day.isToday) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        border = if (day.isToday) BorderStroke(1.5.dp, colors.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
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
                    stringResource(R.string.quoted, day.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (day.entries.isEmpty() && day.summary.isBlank()) {
                Text(
                    stringResource(R.string.no_notes),
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
                    stringResource(R.string.and_more, day.entries.size - MAX_ENTRIES_PREVIEW),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 20.dp),
                )
            }
        }
    }
}
