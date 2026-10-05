package com.syemape.radio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Paleta monocromática de MAPE: superficies blancas, fondos grises y controles negros.
 */
object MapeColors {
    var darkMode by mutableStateOf(false)
    val Bg get() = if (darkMode) Color(0xFF101010) else Color(0xFFF4F4F4)
    // Los colores de acción y sus contenidos se invierten juntos en oscuro para
    // que botones, etiquetas y controles mantengan contraste en toda la app.
    val Ink get() = if (darkMode) Color(0xFFE8E8E8) else Color(0xFF0A0A0A)
    val White get() = if (darkMode) Color(0xFF171717) else Color(0xFFFFFFFF)
    val Red get() = if (darkMode) Color(0xFFE8E8E8) else Color(0xFF111111)
    val Blue get() = if (darkMode) Color(0xFFE8E8E8) else Color(0xFF111111)
    val RedDark get() = if (darkMode) Color(0xFFE8E8E8) else Color(0xFF111111)
    val RedSoftBg get() = if (darkMode) Color(0xFF292929) else Color(0xFFEAEAEA)
    val Card get() = if (darkMode) Color(0xFF1D1D1D) else Color(0xFFFFFFFF)
    val PanelDark = Color(0xFF1A1A1A)    // superficies dentro de cabeceras oscuras
    val PanelBorder = Color(0xFF3A3A3A)
    val Text get() = if (darkMode) Color(0xFFF2F2F2) else Color(0xFF0A0A0A)
    val TextMuted get() = if (darkMode) Color(0xFFB5B5B5) else Color(0xFF6A6A6A)
    val TextFaint get() = if (darkMode) Color(0xFFAAAAAA) else Color(0xFF7A7A7A)
    val TextSubtle get() = if (darkMode) Color(0xFFCCCCCC) else Color(0xFF5A5A5A)
    val TextOnDark = Color(0xFFC9C9C9)   // texto secundario sobre negro
    val TextOnDarkSoft = Color(0xFFB5B5B5)
    val Border get() = if (darkMode) Color(0xFF424242) else Color(0xFFDADADA)
    val BorderLight get() = if (darkMode) Color(0xFF333333) else Color(0xFFE4E4E4)
    val Timestamp get() = if (darkMode) Color(0xFF999999) else Color(0xFF9A9A9A)
}
