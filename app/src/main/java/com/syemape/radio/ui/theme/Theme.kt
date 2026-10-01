package com.syemape.radio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

/** Esquema Material mínimo alineado a la paleta Mape (la UI usa colores directos). */
private val MapeColorScheme = lightColorScheme(
    primary = MapeColors.Ink,
    onPrimary = MapeColors.White,
    secondary = MapeColors.Red,
    background = MapeColors.Bg,
    onBackground = MapeColors.Ink,
    surface = MapeColors.White,
    onSurface = MapeColors.Ink,
    error = MapeColors.Red,
)

/**
 * Tema de la app. Envuelve MaterialTheme con la paleta y la tipografía Outfit, y
 * fija el estilo de texto por defecto a Outfit para que cualquier [Text] herede la fuente.
 */
@Composable
fun MapeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MapeColorScheme,
        typography = MapeTypography,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides
                MaterialTheme.typography.bodyMedium.copy(fontFamily = Outfit),
            content = content,
        )
    }
}
