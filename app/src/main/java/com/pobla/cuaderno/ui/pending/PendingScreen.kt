@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pobla.cuaderno.ui.components.StatusPill
import com.pobla.cuaderno.ui.components.TranscribeStatus
import com.pobla.cuaderno.util.longDate
import com.pobla.cuaderno.util.weekdayDayMonth
import java.time.LocalDate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.pobla.cuaderno.R
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@Composable
fun PendingRoute(viewModel: PendingViewModel, onOpenDay: (LocalDate) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PendingScreen(state = state, onOpenDay = onOpenDay, onMarkTranscribed = viewModel::markTranscribed)
}

@Composable
fun PendingScreen(
    state: PendingUiState,
    onOpenDay: (LocalDate) -> Unit,
    onMarkTranscribed: (LocalDate) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.pending_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.days.isNotEmpty()) {
                            Text(
                                pluralStringResource(R.plurals.days_count, state.days.size, state.days.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (state.loaded && state.days.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.all_transcribed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                PendingDayCard(
                    day = day,
                    onClick = { onOpenDay(day.date) },
                    onMarkTranscribed = { onMarkTranscribed(day.date) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun PendingDayCard(
    day: PendingDayUi,
    onClick: () -> Unit,
    onMarkTranscribed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = onClick,
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (day.isToday) stringResource(R.string.today_weekday, day.date.weekdayDayMonth()) else day.date.weekdayDayMonth(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (day.hasChanges) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill(TranscribeStatus.CHANGED)
                    }
                }
                Text(
                    day.date.longDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    detailsText(LocalContext.current, day),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (day.summary.isNotBlank()) {
                    Text(
                        stringResource(R.string.quoted, day.summary),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalIconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onMarkTranscribed()
            }) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.mark_transcribed))
            }
        }
    }
}

private fun detailsText(context: Context, day: PendingDayUi): String {
    val res = context.resources
    val parts = buildList {
        if (day.entryCount > 0) add(res.getQuantityString(R.plurals.entries_count, day.entryCount, day.entryCount))
        if (day.habitCount > 0) add(res.getQuantityString(R.plurals.habits_count, day.habitCount, day.habitCount))
    }
    return if (parts.isEmpty()) context.getString(R.string.only_summary) else parts.joinToString(" · ")
}
