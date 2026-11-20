@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.data.ReminderKind
import com.pobla.cuaderno.data.ReminderSetting
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.reminders.Notifications
import com.pobla.cuaderno.ui.components.SectionTitle
import com.pobla.cuaderno.update.UpdateState
import java.time.DayOfWeek
import androidx.compose.ui.res.stringResource
import com.pobla.cuaderno.R
import com.pobla.cuaderno.data.ALL_DAYS
import com.pobla.cuaderno.data.WORKDAYS
import com.pobla.cuaderno.data.WEEKEND
import com.pobla.cuaderno.util.letter
import com.pobla.cuaderno.util.shortName
import com.pobla.cuaderno.BuildConfig
import com.pobla.cuaderno.update.ReleaseNotes
import com.pobla.cuaderno.ui.components.WhatsNewDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.pobla.cuaderno.widget.TaskWidgetReceiver

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Estado del permiso de notificaciones; se refresca al volver a la app (p. ej. desde Ajustes del sistema).
    var notificationsAllowed by remember { mutableStateOf(Notifications.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.canNotify(context)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsAllowed = Notifications.canNotify(context) }

    fun ensureNotificationPermission() {
        if (Notifications.canNotify(context)) return
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }

    SettingsScreen(
        habits = habits,
        reminders = reminders,
        notificationsAllowed = notificationsAllowed,
        onReminderEnabledChange = { kind, enabled ->
            viewModel.setReminderEnabled(kind, enabled)
            if (enabled) ensureNotificationPermission()
        },
        onReminderTimeChange = viewModel::setReminderTime,
        onReminderDaysChange = viewModel::setReminderDays,
        onTestReminder = { kind ->
            if (Notifications.canNotify(context)) Notifications.showTest(context, kind) else ensureNotificationPermission()
        },
        onOpenNotificationSettings = { openNotificationSettings(context) },
        dynamicColor = dynamicColor,
        updateState = updateState,
        versionName = viewModel.updates.currentVersionName,
        onAdd = viewModel::addHabit,
        onRename = viewModel::renameHabit,
        onArchive = viewModel::archiveHabit,
        onMove = viewModel::moveHabit,
        onDynamicColorChange = viewModel::setDynamicColor,
        onCheckUpdates = viewModel::checkForUpdates,
        onDownloadUpdate = viewModel::downloadUpdate,
        onInstallUpdate = { viewModel.updates.install(context) },
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
    }
    context.startActivity(intent)
}

@Composable
fun SettingsScreen(
    habits: List<HabitEntity>,
    reminders: Map<ReminderKind, ReminderSetting>,
    notificationsAllowed: Boolean,
    onReminderEnabledChange: (ReminderKind, Boolean) -> Unit,
    onReminderTimeChange: (ReminderKind, Int, Int) -> Unit,
    onReminderDaysChange: (ReminderKind, Set<DayOfWeek>) -> Unit,
    onTestReminder: (ReminderKind) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    dynamicColor: Boolean,
    updateState: UpdateState,
    versionName: String,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onArchive: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    var renaming by remember { mutableStateOf<HabitEntity?>(null) }
    var pickingTime by remember { mutableStateOf<ReminderKind?>(null) }
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
                title = {
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
            item(key = "habits_title") { SectionTitle(stringResource(R.string.section_daily_habits)) }
            item(key = "habits_hint") {
                Text(
                    stringResource(R.string.habits_hint),
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
                    modifier = Modifier.animateItem(),
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
                        placeholder = { Text(stringResource(R.string.new_habit)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitNew() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { submitNew() }, enabled = newHabit.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_habit))
                    }
                }
            }

            item(key = "reminders_title") { SectionTitle(stringResource(R.string.section_reminders)) }
            if (!notificationsAllowed && reminders.values.any { it.enabled }) {
                item(key = "reminders_blocked") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Text(
                                stringResource(R.string.notifications_blocked),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenNotificationSettings) { Text(stringResource(R.string.allow)) }
                        }
                    }
                }
            }
            ReminderKind.entries.forEach { kind ->
                val setting = reminders[kind] ?: ReminderSetting(false, kind.defaultMinutes)
                item(key = "reminder_${kind.name}") {
                    ReminderCard(
                        kind = kind,
                        setting = setting,
                        onEnabledChange = { onReminderEnabledChange(kind, it) },
                        onPickTime = { pickingTime = kind },
                        onDaysChange = { onReminderDaysChange(kind, it) },
                        onTest = { onTestReminder(kind) },
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item(key = "look_title") { SectionTitle(stringResource(R.string.section_appearance)) }
                item(key = "dynamic") {
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dynamic_color_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.dynamic_color_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                        }
                    }
                }
            }

            // Idioma por app: Android 13+ (en versiones anteriores sigue el idioma del móvil).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item(key = "language") { LanguageCard() }
            }

            // Widget: Android 8+ permite pedir que se añada a la pantalla de inicio.
            item(key = "widget") { WidgetCard() }

            item(key = "updates_title") { SectionTitle(stringResource(R.string.section_updates)) }
            item(key = "updates") {
                UpdatesCard(
                    state = updateState,
                    versionName = versionName,
                    onCheck = onCheckUpdates,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                )
            }
        }
    }

    renaming?.let { habit ->
        var name by rememberSaveable(habit.id) { mutableStateOf(habit.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_habit)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(habit.id, name)
                        renaming = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pickingTime?.let { kind ->
        val setting = reminders[kind] ?: ReminderSetting(false, kind.defaultMinutes)
        val timeState = rememberTimePickerState(
            initialHour = setting.hour,
            initialMinute = setting.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickingTime = null },
            title = { Text(stringResource(kind.titleRes)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                Button(onClick = {
                    onReminderTimeChange(kind, timeState.hour, timeState.minute)
                    pickingTime = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    archiving?.let { habit ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text(stringResource(R.string.remove_habit_title, habit.name)) },
            text = { Text(stringResource(R.string.remove_habit_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onArchive(habit.id)
                    archiving = null
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Pide al lanzador que añada el widget "Hoy" a la pantalla de inicio. */
@Composable
private fun WidgetCard() {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    if (!manager.isRequestPinAppWidgetSupported) return
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.widget_settings_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.widget_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = {
                manager.requestPinAppWidget(ComponentName(context, TaskWidgetReceiver::class.java), null, null)
            }) { Text(stringResource(R.string.widget_settings_action)) }
        }
    }
}

/** Abre el ajuste del sistema "Idioma de la app" (Android 13+). */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }) { Text(stringResource(R.string.change)) }
        }
    }
}

@Composable
private fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
    ALL_DAYS -> stringResource(R.string.days_all)
    WORKDAYS -> stringResource(R.string.days_workdays)
    WEEKEND -> stringResource(R.string.days_weekend)
    else -> days.sorted().joinToString(", ") { it.shortName() }
}

@Composable
private fun ReminderCard(
    kind: ReminderKind,
    setting: ReminderSetting,
    onEnabledChange: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onDaysChange: (Set<DayOfWeek>) -> Unit,
    onTest: () -> Unit,
) {
    val description = stringResource(kind.descriptionRes)
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(kind.titleRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = setting.enabled, onCheckedChange = onEnabledChange)
        }
        if (setting.enabled) {
            DaySelector(
                selected = setting.days,
                onChange = onDaysChange,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilledTonalButton(onClick = onPickTime) {
                    Text(stringResource(R.string.at_time, setting.label))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    daysLabel(setting.days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onTest) { Text(stringResource(R.string.test)) }
            }
        }
    }
}

/** Siete botones redondos L M X J V S D; siempre queda al menos uno marcado. */
@Composable
private fun DaySelector(
    selected: Set<DayOfWeek>,
    onChange: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        DayOfWeek.values().forEach { day ->
            val isOn = day in selected
            val container by animateColorAsState(
                if (isOn) colors.primary else colors.surfaceContainerHighest,
                animationSpec = motion.fastEffectsSpec(),
                label = "dia",
            )
            val content by animateColorAsState(
                if (isOn) colors.onPrimary else colors.onSurfaceVariant,
                animationSpec = motion.fastEffectsSpec(),
                label = "texto",
            )
            Surface(
                onClick = {
                    val next = if (isOn) selected - day else selected + day
                    if (next.isNotEmpty()) {
                        haptics.performHapticFeedback(
                            if (isOn) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                        )
                        onChange(next)
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    }
                },
                shape = CircleShape,
                color = container,
                contentColor = content,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        day.letter(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) { content() }
    }
}

@Composable
private fun UpdatesCard(
    state: UpdateState,
    versionName: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    SettingsCard {
        var showNotes by remember { mutableStateOf(false) }
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.installed_version, versionName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showNotes = true }) { Text(stringResource(R.string.whats_new_button)) }
        }
        if (showNotes) {
            val notes = remember {
                ReleaseNotes.between(ReleaseNotes.bundled(context), 0, BuildConfig.COMMIT_TIME).take(10)
            }
            WhatsNewDialog(versionName, notes, onDismiss = { showNotes = false })
        }
        Text(
            stringResource(R.string.updates_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 12.dp))
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "actualizacion",
        ) { s ->
            when (s) {
                UpdateState.Idle -> FilledTonalButton(onClick = onCheck) { Text(stringResource(R.string.check_updates)) }
                UpdateState.Checking -> Text(stringResource(R.string.checking), style = MaterialTheme.typography.bodyMedium)
                UpdateState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.up_to_date), modifier = Modifier.weight(1f))
                    TextButton(onClick = onCheck) { Text(stringResource(R.string.check_again)) }
                }
                is UpdateState.Available -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.new_version, s.versionCode), modifier = Modifier.weight(1f))
                    Button(onClick = onDownload) { Text(stringResource(R.string.download)) }
                }
                is UpdateState.Downloading -> Column {
                    val progress by animateFloatAsState(s.progress, motion.defaultEffectsSpec(), label = "progreso")
                    Text(stringResource(R.string.downloading, s.versionCode, (s.progress * 100).toInt()))
                    Spacer(Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.ReadyToInstall -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ready_version, s.versionCode), modifier = Modifier.weight(1f))
                    Button(onClick = onInstall) { Text(stringResource(R.string.install)) }
                }
                is UpdateState.Error -> Column {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onCheck) { Text(stringResource(R.string.retry)) }
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}
