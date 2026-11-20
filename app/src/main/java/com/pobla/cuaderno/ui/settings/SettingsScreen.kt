@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.ui.settings

import android.os.Build
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.ui.components.SectionTitle
import com.pobla.cuaderno.update.UpdateState

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        habits = habits,
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

@Composable
fun SettingsScreen(
    habits: List<HabitEntity>,
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
