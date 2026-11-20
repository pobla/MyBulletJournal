@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.ui.importer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.R
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.ImportItem
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.model.symbolFor
import com.pobla.cuaderno.ui.components.SectionTitle
import com.pobla.cuaderno.util.weekdayDayMonth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val CLAUDE_PACKAGE = "com.anthropic.claude"

/**
 * Pantalla completa para importar texto: lo pegas o llega compartido, ves cómo se va a
 * guardar y confirmas. [initialText] es el texto compartido (vacío si se abre a mano).
 */
@Composable
fun ImportDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onImported: (firstDate: LocalDate, count: Int) -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val habits by viewModel.habits.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf(initialText) }
    var defaultDateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val defaultDate = LocalDate.ofEpochDay(defaultDateEpoch)
    var pickingDate by remember { mutableStateOf(false) }

    val parsed = remember(text, defaultDateEpoch, habits) { viewModel.parse(text, defaultDate, habits) }
    // Cambios del usuario sobre la vista previa (se reinician si cambia el texto).
    val excluded = remember(parsed) { mutableStateMapOf<Int, Boolean>() }
    val overrides = remember(parsed) { mutableStateMapOf<Int, ImportItem>() }
    val items = parsed.mapIndexed { i, item -> overrides[i] ?: item }
    val selected = items.filterIndexed { i, _ -> excluded[i] != true }

    val claudeIntent = remember { context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.import_title), fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val first = selected.minOf { it.date }
                                    val count = selected.size
                                    viewModel.import(selected) { onImported(first, count) }
                                },
                            ) {
                                Text(pluralStringResource(R.plurals.import_action, selected.size, selected.size))
                            }
                        }
                    }
                },
            ) { padding ->
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "hint") {
                        Text(
                            stringResource(R.string.import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item(key = "text") {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(stringResource(R.string.import_placeholder)) },
                            minLines = 4,
                            maxLines = 10,
                            shape = MaterialTheme.shapes.large,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "claude") {
                        OutlinedButton(
                            onClick = {
                                copyPrompt(context)
                                claudeIntent?.let { context.startActivity(it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (claudeIntent != null) R.string.import_open_claude else R.string.import_copy_prompt,
                                ),
                            )
                        }
                    }
                    item(key = "date") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.import_default_date),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(onClick = { pickingDate = true }) {
                                Text(defaultDate.weekdayDayMonth())
                            }
                        }
                    }
                    if (items.isEmpty()) {
                        item(key = "nothing") {
                            Text(
                                stringResource(R.string.import_nothing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    // Vista previa agrupada por día
                    items.withIndex().groupBy { it.value.date }.toSortedMap().forEach { (date, group) ->
                        item(key = "h_${date.toEpochDay()}") {
                            SectionTitle(
                                if (date == LocalDate.now()) {
                                    stringResource(R.string.today_weekday, date.weekdayDayMonth())
                                } else {
                                    date.weekdayDayMonth()
                                },
                            )
                        }
                        group.forEach { (index, entry) ->
                            item(key = "i_$index") {
                                PreviewRow(
                                    item = entry,
                                    included = excluded[index] != true,
                                    onIncludedChange = { excluded[index] = !it },
                                    onCycle = {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        overrides[index] = cycle(entry)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pickingDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = defaultDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            defaultDateEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        pickingDate = false
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) }
                },
            ) {
                DatePicker(state = state)
            }
        }
    }
}

/** • → × → > → ○ → – → • (y deja de ser hábito si lo era). */
private fun cycle(item: ImportItem): ImportItem = when {
    item.type == EntryType.TASK && item.status == TaskStatus.OPEN -> item.copy(status = TaskStatus.DONE, habitId = null)
    item.type == EntryType.TASK && item.status == TaskStatus.DONE -> item.copy(status = TaskStatus.MIGRATED, habitId = null)
    item.type == EntryType.TASK -> item.copy(type = EntryType.EVENT, status = TaskStatus.OPEN, habitId = null)
    item.type == EntryType.EVENT -> item.copy(type = EntryType.NOTE, habitId = null)
    else -> item.copy(type = EntryType.TASK, status = TaskStatus.OPEN, habitId = null)
}

private fun copyPrompt(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("prompt", context.getString(R.string.import_prompt)))
    Toast.makeText(context, R.string.import_prompt_copied, Toast.LENGTH_LONG).show()
}

@Composable
private fun PreviewRow(
    item: ImportItem,
    included: Boolean,
    onIncludedChange: (Boolean) -> Unit,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val background by animateColorAsState(
        if (included) colors.surfaceContainerHigh else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "fila",
    )
    Surface(color = background, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = included, onCheckedChange = onIncludedChange)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCycle),
            ) {
                AnimatedContent(
                    targetState = if (item.habitId != null) "✓" else symbolFor(item.type, item.status),
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
                        color = if (included) colors.primary else colors.outline,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (included) colors.onSurface else colors.outline,
                )
                if (item.habitId != null) {
                    Text(
                        stringResource(R.string.import_habit),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.tertiary,
                    )
                }
            }
        }
    }
}
