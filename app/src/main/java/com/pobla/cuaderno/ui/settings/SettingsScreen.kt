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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
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
                    Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
                        placeholder = { Text("Nuevo hábito") },
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
                        Icon(Icons.Filled.Add, contentDescription = "Añadir hábito")
                    }
                }
            }

            item(key = "reminders_title") { SectionTitle("Recordatorios") }
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
                                "Las notificaciones están bloqueadas: los recordatorios no se verán.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenNotificationSettings) { Text("Permitir") }
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
                        onTest = { onTestReminder(kind) },
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item(key = "look_title") { SectionTitle("Apariencia") }
                item(key = "dynamic") {
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Colores del fondo de pantalla", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Como las apps de Pixel. Desactívalo para usar la paleta papel y tinta.",
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

            item(key = "updates_title") { SectionTitle("Actualizaciones") }
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
            title = { Text("Renombrar hábito") },
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
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
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
            title = { Text(kind.title) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                Button(onClick = {
                    onReminderTimeChange(kind, timeState.hour, timeState.minute)
                    pickingTime = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = null }) { Text("Cancelar") } },
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
private fun ReminderCard(
    kind: ReminderKind,
    setting: ReminderSetting,
    onEnabledChange: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onTest: () -> Unit,
) {
    val description = when (kind) {
        ReminderKind.HABITS -> "Solo avisa si te queda algún hábito sin marcar ese día."
        ReminderKind.TRANSCRIBE -> "Solo avisa si hay días pendientes de pasar al cuaderno."
    }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.title, style = MaterialTheme.typography.titleMedium)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilledTonalButton(onClick = onPickTime) {
                    Text("Cada día a las ${setting.label}")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onTest) { Text("Probar") }
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
        Text("Versión instalada: $versionName", style = MaterialTheme.typography.titleMedium)
        Text(
            "Se comprueba sola una vez al día al abrir la app (release debug-latest de GitHub).",
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
                UpdateState.Idle -> FilledTonalButton(onClick = onCheck) { Text("Buscar actualizaciones") }
                UpdateState.Checking -> Text("Comprobando…", style = MaterialTheme.typography.bodyMedium)
                UpdateState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tienes la última versión ✓", modifier = Modifier.weight(1f))
                    TextButton(onClick = onCheck) { Text("Volver a mirar") }
                }
                is UpdateState.Available -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nueva versión #${s.versionCode}", modifier = Modifier.weight(1f))
                    Button(onClick = onDownload) { Text("Descargar") }
                }
                is UpdateState.Downloading -> Column {
                    val progress by animateFloatAsState(s.progress, motion.defaultEffectsSpec(), label = "progreso")
                    Text("Descargando #${s.versionCode}… ${(s.progress * 100).toInt()} %")
                    Spacer(Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.ReadyToInstall -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lista la versión #${s.versionCode}", modifier = Modifier.weight(1f))
                    Button(onClick = onInstall) { Text("Instalar") }
                }
                is UpdateState.Error -> Column {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onCheck) { Text("Reintentar") }
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
