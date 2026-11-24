@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.pobla.cuaderno.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pobla.cuaderno.model.Mood
import com.pobla.cuaderno.model.Weather

/** Color oscuro para dibujar la cara sobre el color del ánimo (se lee en claro y oscuro). */
private val MoodInk = Color(0xFF1E1B16)

/** Fila de 7 símbolos de tiempo; toca uno para elegirlo y otra vez para quitarlo. */
@Composable
fun WeatherPicker(selected: Weather?, changed: Boolean, onSelect: (Weather?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Weather.entries.forEach { w ->
            val on = w == selected
            val bg by animateColorAsState(if (on) colors.primaryContainer else Color.Transparent, label = "fondo")
            val scale by animateFloatAsState(if (on) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "escala")
            Surface(
                onClick = {
                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onSelect(if (on) null else w)
                },
                shape = CircleShape,
                color = bg,
                border = if (on && changed) BorderStroke(2.dp, colors.tertiary) else null,
                modifier = Modifier.size(42.dp).scale(scale),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(w.iconRes),
                        contentDescription = stringResource(w.labelRes),
                        tint = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/** Fila de 5 caras de ánimo con su color; la elegida se rellena. */
@Composable
fun MoodPicker(selected: Mood?, changed: Boolean, onSelect: (Mood?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Mood.entries.forEach { m ->
            val on = m == selected
            val moodColor = Color(m.color)
            val bg by animateColorAsState(if (on) moodColor else Color.Transparent, label = "fondo")
            val scale by animateFloatAsState(if (on) 1.1f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "escala")
            Surface(
                onClick = {
                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onSelect(if (on) null else m)
                },
                shape = CircleShape,
                color = bg,
                border = if (on && changed) BorderStroke(2.dp, colors.tertiary) else null,
                modifier = Modifier.size(46.dp).scale(scale),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(m.iconRes),
                        contentDescription = stringResource(m.labelRes),
                        tint = if (on) MoodInk else moodColor,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/** Icono pequeño de tiempo (semana, mes). */
@Composable
fun WeatherIcon(weather: Weather, size: Dp = 18.dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(painterResource(weather.iconRes), contentDescription = stringResource(weather.labelRes), tint = tint, modifier = Modifier.size(size))
}

/** Punto de ánimo: círculo del color con la cara dibujada encima. */
@Composable
fun MoodDot(mood: Mood, size: Dp = 20.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(Color(mood.color)),
    ) {
        Icon(
            painterResource(mood.iconRes),
            contentDescription = stringResource(mood.labelRes),
            tint = MoodInk,
            modifier = Modifier.size(size * 0.8f).padding(0.dp),
        )
    }
}
