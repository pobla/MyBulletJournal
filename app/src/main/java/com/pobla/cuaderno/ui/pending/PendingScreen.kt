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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pobla.cuaderno.ui.components.StatusPill
import com.pobla.cuaderno.ui.components.TranscribeStatus
import com.pobla.cuaderno.util.longDate
import com.pobla.cuaderno.util.weekdayDayMonth
import java.time.LocalDate

@Composable
fun PendingRoute(viewModel: PendingViewModel, onOpenDay: (LocalDate) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PendingScreen(state = state, onOpenDay = onOpenDay, onMarkTranscribed = viewModel::markTranscribed)
}

@OptIn(ExperimentalMaterial3Api::class)
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
                        Text("Pendientes de pasar", style = MaterialTheme.typography.titleLarge)
                        if (state.days.isNotEmpty()) {
                            Text(
                                if (state.days.size == 1) "1 día" else "${state.days.size} días",
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
                    "Todo pasado al cuaderno ✓",
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
                )
            }
        }
    }
}

@Composable
private fun PendingDayCard(day: PendingDayUi, onClick: () -> Unit, onMarkTranscribed: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (day.isToday) "Hoy · ${day.date.weekdayDayMonth()}" else day.date.weekdayDayMonth(),
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
                    detailsText(day),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (day.summary.isNotBlank()) {
                    Text(
                        "“${day.summary}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalIconButton(onClick = onMarkTranscribed) {
                Icon(Icons.Filled.Check, contentDescription = "Marcar como pasado")
            }
        }
    }
}

private fun detailsText(day: PendingDayUi): String {
    val parts = buildList {
        if (day.entryCount > 0) add(if (day.entryCount == 1) "1 entrada" else "${day.entryCount} entradas")
        if (day.habitCount > 0) add(if (day.habitCount == 1) "1 hábito" else "${day.habitCount} hábitos")
    }
    return if (parts.isEmpty()) "Solo frase del día o cambios borrados" else parts.joinToString(" · ")
}
