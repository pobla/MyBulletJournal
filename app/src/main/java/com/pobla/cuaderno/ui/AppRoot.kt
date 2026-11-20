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

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DAY("day", "Día", Icons.Filled.Create),
    WEEK("week", "Semana", Icons.Filled.DateRange),
    PENDING("pending", "Pendientes", Icons.AutoMirrored.Filled.List),
    SETTINGS("settings", "Ajustes", Icons.Filled.Settings),
}

@Composable
fun AppRoot(requestedTab: String? = null, onTabHandled: () -> Unit = {}) {
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
                            label = { Text(tab.label) },
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
            composable(Tab.DAY.route) { DayRoute(dayViewModel) }
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
                title = { Text("Nueva versión disponible") },
                text = { Text("Hay una versión nueva (#${s.versionCode}). Se descarga aquí mismo y Android te pedirá confirmar la instalación.") },
                confirmButton = {
                    Button(onClick = { updates.download() }) { Text("Actualizar") }
                },
                dismissButton = {
                    TextButton(onClick = { updates.promptedVersion = s.versionCode }) { Text("Luego") }
                },
            )
        }
        is UpdateState.ReadyToInstall -> if (updates.promptedVersion != s.versionCode) {
            AlertDialog(
                onDismissRequest = { updates.promptedVersion = s.versionCode },
                title = { Text("Actualización descargada") },
                text = { Text("Pulsa instalar. Si es la primera vez, Android te pedirá permitir instalar apps desde Cuaderno de bolsillo; actívalo, vuelve y pulsa instalar otra vez.") },
                confirmButton = {
                    Button(onClick = { updates.install(context) }) { Text("Instalar") }
                },
                dismissButton = {
                    TextButton(onClick = { updates.promptedVersion = s.versionCode }) { Text("Luego") }
                },
            )
        }
        else -> Unit
    }
}
