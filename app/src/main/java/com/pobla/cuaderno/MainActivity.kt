package com.pobla.cuaderno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pobla.cuaderno.ui.day.DayRoute
import com.pobla.cuaderno.ui.theme.CuadernoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuadernoTheme {
                DayRoute()
            }
        }
    }
}
