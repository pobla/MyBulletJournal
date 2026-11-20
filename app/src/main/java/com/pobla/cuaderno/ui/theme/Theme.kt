package com.pobla.cuaderno.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Paleta "papel y tinta": azul de pluma, papel crema y naranja para lo nuevo.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3E5A7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3F7),
    onPrimaryContainer = Color(0xFF0B1D33),
    secondary = Color(0xFF6B5E4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE1CF),
    onSecondaryContainer = Color(0xFF241A0E),
    tertiary = Color(0xFFB4502C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCC),
    onTertiaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFFBF8F1),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFE9E3D7),
    onSurfaceVariant = Color(0xFF4C463C),
    outline = Color(0xFF7E776B),
    outlineVariant = Color(0xFFCFC6B8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2E8),
    surfaceContainer = Color(0xFFF2ECE1),
    surfaceContainerHigh = Color(0xFFEDE6DA),
    surfaceContainerHighest = Color(0xFFE7E0D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7EC),
    onPrimary = Color(0xFF0F2C4A),
    primaryContainer = Color(0xFF274361),
    onPrimaryContainer = Color(0xFFD3E3F7),
    secondary = Color(0xFFD6C5AE),
    onSecondary = Color(0xFF3A2F21),
    secondaryContainer = Color(0xFF4E4538),
    onSecondaryContainer = Color(0xFFEDE1CF),
    tertiary = Color(0xFFFFB59A),
    onTertiary = Color(0xFF5A1B03),
    tertiaryContainer = Color(0xFF7A3018),
    onTertiaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF1B1A17),
    onBackground = Color(0xFFE9E2D8),
    surface = Color(0xFF1B1A17),
    onSurface = Color(0xFFE9E2D8),
    surfaceVariant = Color(0xFF4A463E),
    onSurfaceVariant = Color(0xFFCFC6B8),
    outline = Color(0xFF989083),
    outlineVariant = Color(0xFF4A463E),
    surfaceContainerLowest = Color(0xFF151412),
    surfaceContainerLow = Color(0xFF22201C),
    surfaceContainer = Color(0xFF26241F),
    surfaceContainerHigh = Color(0xFF302E29),
    surfaceContainerHighest = Color(0xFF3B3833),
)

// Esquinas generosas, al estilo Pixel / Material 3 Expressive.
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Tema de la app. Con [dynamicColor] (Android 12+) toma los colores del fondo de pantalla,
 * como las apps de Pixel; si no, usa la paleta "papel y tinta".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CuadernoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        content = content,
    )
}
