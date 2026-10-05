package com.syemape.radio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** Esquema Material mínimo alineado a la paleta Mape (la UI usa colores directos). */
private val LightMapeColorScheme = lightColorScheme(
    primary = MapeColors.Ink,
    onPrimary = MapeColors.White,
    secondary = MapeColors.Ink,
    background = MapeColors.Bg,
    onBackground = MapeColors.Ink,
    surface = MapeColors.White,
    onSurface = MapeColors.Ink,
    error = MapeColors.Ink,
)
private val DarkMapeColorScheme = darkColorScheme(
    primary = MapeColors.White,
    onPrimary = MapeColors.Ink,
    secondary = MapeColors.White,
    background = Color(0xFF101010),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1D1D1D),
    onSurface = Color(0xFFF2F2F2),
    error = Color(0xFFFF7777),
)

/**
 * Tema de la app. Envuelve MaterialTheme con la paleta y la tipografía Outfit, y
 * fija el estilo de texto por defecto a Outfit para que cualquier [Text] herede la fuente.
 */
@Composable
fun MapeTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    MapeColors.darkMode = darkMode
    MaterialTheme(
        colorScheme = if (darkMode) DarkMapeColorScheme else LightMapeColorScheme,
        typography = MapeTypography,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides
                MaterialTheme.typography.bodyMedium.copy(fontFamily = Outfit),
            content = content,
        )
    }
}
