package com.pobla.cuaderno

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pobla.cuaderno.reminders.Notifications
import com.pobla.cuaderno.ui.AppRoot
import com.pobla.cuaderno.ui.theme.CuadernoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Pestaña pedida desde una notificación (se consume al navegar). */
    private val requestedTab = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuadernoApp).container
        // Como mucho una vez al día, al abrir la app.
        container.updates.checkIfDue()
        // Por si se perdió alguna programación (sin tocar las que ya existen).
        container.reminders.ensureScheduled()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val dynamicColor by container.settings.dynamicColor.collectAsStateWithLifecycle()
            val tab by requestedTab.collectAsStateWithLifecycle()
            CuadernoTheme(dynamicColor = dynamicColor) {
                AppRoot(
                    requestedTab = tab,
                    onTabHandled = { requestedTab.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_TAB)?.let { requestedTab.value = it }
    }
}
