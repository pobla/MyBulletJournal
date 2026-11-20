@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pobla.cuaderno.ui.day.DayRoute
import com.pobla.cuaderno.ui.day.DayViewModel
import com.pobla.cuaderno.ui.pending.PendingRoute
import com.pobla.cuaderno.ui.pending.PendingViewModel
import com.pobla.cuaderno.ui.settings.SettingsRoute
import com.pobla.cuaderno.ui.week.WeekRoute
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.update.UpdateState
import java.time.LocalDate
import androidx.compose.ui.res.stringResource
import com.pobla.cuaderno.R
import androidx.annotation.StringRes
import android.widget.Toast
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pobla.cuaderno.ui.importer.ImportDialog
import com.pobla.cuaderno.BuildConfig
import com.pobla.cuaderno.update.ReleaseNotes
import com.pobla.cuaderno.ui.components.ReleaseNotesList
import com.pobla.cuaderno.ui.components.WhatsNewDialog
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.pobla.cuaderno.update.ReleaseNote
import android.content.Context

private enum class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    DAY("day", R.string.tab_day, Icons.Filled.Create),
    WEEK("week", R.string.tab_week, Icons.Filled.DateRange),
    PENDING("pending", R.string.tab_pending, Icons.AutoMirrored.Filled.List),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot(
    requestedTab: String? = null,
    onTabHandled: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    // Compartidos a nivel de actividad: el día se abre desde Semana/Pendientes
    // y el número de pendientes se muestra en la barra inferior.
    val dayViewModel: DayViewModel = viewModel(factory = DayViewModel.Factory)
    val pendingViewModel: PendingViewModel = viewModel(factory = PendingViewModel.Factory)
    val pending by pendingViewModel.uiState.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val openDay: (LocalDate) -> Unit = { date ->
        dayViewModel.goTo(date)
        navController.navigateToTab(Tab.DAY.route)
    }

    val motion = MaterialTheme.motionScheme

    // Abrir la pestaña que pide una notificación (hábitos → hoy; cuaderno → pendientes).
    val navReady = backStackEntry != null
    LaunchedEffect(requestedTab, navReady) {
        if (!navReady) return@LaunchedEffect
        val route = requestedTab ?: return@LaunchedEffect
        if (route == Tab.DAY.route) dayViewModel.today()
        if (Tab.entries.any { it.route == route }) navController.navigateToTab(route)
        onTabHandled()
    }

    UpdatePrompt()
    WhatsNewAfterUpdate()

    // Importar texto: llega compartido desde otra app o se abre desde el menú del día.
    val context = LocalContext.current
    var importText by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            importText = sharedText
            onSharedTextHandled()
        }
    }
    importText?.let { initial ->
        // key: un texto compartido nuevo reinicia el diálogo
        key(initial) {
            ImportDialog(
                initialText = initial,
                onDismiss = { importText = null },
                onImported = { firstDate, count ->
                    importText = null
                    Toast.makeText(
                        context,
                        context.resources.getQuantityString(R.plurals.import_done, count, count),
                        Toast.LENGTH_SHORT,
                    ).show()
                    openDay(firstDate)
                },
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Con el teclado abierto, la barra se oculta para dejar sitio.
            if (!WindowInsets.isImeVisible) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = {
                                val count = pending.days.size
                                if (tab == Tab.PENDING && count > 0) {
                                    BadgedBox(badge = { Badge { Text("$count") } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.DAY.route,
            enterTransition = { fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f) },
            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
            popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f) },
            popExitTransition = { fadeOut(motion.fastEffectsSpec()) },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Tab.DAY.route) { DayRoute(dayViewModel, onImport = { importText = "" }) }
            composable(Tab.WEEK.route) { WeekRoute(onOpenDay = openDay) }
            composable(Tab.PENDING.route) { PendingRoute(pendingViewModel, onOpenDay = openDay) }
            composable(Tab.SETTINGS.route) { SettingsRoute() }
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * La primera vez que se abre una versión nueva, muestra lo que ha cambiado desde la
 * versión que había instalada (aunque se haya saltado alguna). En una instalación nueva no.
 */
@Composable
private fun WhatsNewAfterUpdate() {
    val context = LocalContext.current
    val notes = WhatsNewState.notes ?: computeWhatsNew(context).also {
        WhatsNewState.notes = it
        WhatsNewState.visible = it.isNotEmpty()
    }
    if (WhatsNewState.visible) {
        WhatsNewDialog(BuildConfig.VERSION_NAME, notes, onDismiss = { WhatsNewState.visible = false })
    }
}

/** Se calcula una sola vez por proceso (sobrevive a girar la pantalla). */
private object WhatsNewState {
    var notes: List<ReleaseNote>? = null
    var visible by mutableStateOf(false)
}

private fun computeWhatsNew(context: Context): List<ReleaseNote> {
    val settings = (context.applicationContext as CuadernoApp).container.settings
    val current = BuildConfig.VERSION_CODE
    val previousVersion = settings.lastSeenVersionCode
    val previousCommitTime = settings.lastSeenCommitTime
    settings.lastSeenVersionCode = current
    settings.lastSeenCommitTime = BuildConfig.COMMIT_TIME

    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val freshInstall = info.firstInstallTime == info.lastUpdateTime
    val all = ReleaseNotes.bundled(context)
    return when {
        current <= previousVersion -> emptyList()
        previousVersion == 0 && freshInstall -> emptyList()
        // Actualización desde una versión sin notas: enseñamos los últimos cambios.
        previousCommitTime == 0L -> ReleaseNotes.between(all, 0, BuildConfig.COMMIT_TIME).take(5)
        else -> ReleaseNotes.between(all, previousCommitTime, BuildConfig.COMMIT_TIME)
    }
}

/**
 * Si la comprobación diaria encuentra una versión nueva, la ofrece una vez.
 * El progreso de la descarga y el botón "Instalar" se ven también en Ajustes.
 */
@Composable
private fun UpdatePrompt() {
    val context = LocalContext.current
    val updates = (context.applicationContext as CuadernoApp).container.updates
    val state by updates.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UpdateState.Available -> if (updates.promptedVersion != s.versionCode) {
            AlertDialog(
                onDismissRequest = { updates.promptedVersion = s.versionCode },
                title = { Text(stringResource(R.string.update_available_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.update_available_text, s.versionCode))
                        if (s.notes.isNotEmpty()) {
                            Text(
                                stringResource(R.string.whats_new_update_header),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            )
                            ReleaseNotesList(s.notes)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { updates.download() }) { Text(stringResource(R.string.update_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { updates.promptedVersion = s.versionCode }) { Text(stringResource(R.string.later)) }
                },
            )
        }
        is UpdateState.ReadyToInstall -> if (updates.promptedVersion != s.versionCode) {
            AlertDialog(
                onDismissRequest = { updates.promptedVersion = s.versionCode },
                title = { Text(stringResource(R.string.update_downloaded_title)) },
                text = { Text(stringResource(R.string.update_downloaded_text)) },
                confirmButton = {
                    Button(onClick = { updates.install(context) }) { Text(stringResource(R.string.install)) }
                },
                dismissButton = {
                    TextButton(onClick = { updates.promptedVersion = s.versionCode }) { Text(stringResource(R.string.later)) }
                },
            )
        }
        else -> Unit
    }
}
