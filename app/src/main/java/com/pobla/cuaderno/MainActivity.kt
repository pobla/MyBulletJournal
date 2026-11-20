package com.pobla.cuaderno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pobla.cuaderno.ui.AppRoot
import com.pobla.cuaderno.ui.theme.CuadernoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuadernoApp).container
        // Como mucho una vez al día, al abrir la app.
        container.updates.checkIfDue()
        setContent {
            val dynamicColor by container.settings.dynamicColor.collectAsStateWithLifecycle()
            CuadernoTheme(dynamicColor = dynamicColor) {
                AppRoot()
            }
        }
    }
}
