package com.syemape.radio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.syemape.radio.R

/**
 * Familia Outfit (la fuente del diseño original). Cada peso de mape-theme.ts
 * se mapea a su FontWeight correspondiente para poder usar fontWeight = ... .
 */
val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),       // 300
    Font(R.font.outfit_regular, FontWeight.Normal),    // 400
    Font(R.font.outfit_medium, FontWeight.Medium),     // 500
    Font(R.font.outfit_semibold, FontWeight.SemiBold), // 600
    Font(R.font.outfit_bold, FontWeight.Bold),         // 700
)

/** Typography de Material con Outfit como familia por defecto en todos los estilos. */
private val base = Typography()
val MapeTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Outfit),
    displayMedium = base.displayMedium.copy(fontFamily = Outfit),
    displaySmall = base.displaySmall.copy(fontFamily = Outfit),
    headlineLarge = base.headlineLarge.copy(fontFamily = Outfit),
    headlineMedium = base.headlineMedium.copy(fontFamily = Outfit),
    headlineSmall = base.headlineSmall.copy(fontFamily = Outfit),
    titleLarge = base.titleLarge.copy(fontFamily = Outfit),
    titleMedium = base.titleMedium.copy(fontFamily = Outfit),
    titleSmall = base.titleSmall.copy(fontFamily = Outfit),
    bodyLarge = base.bodyLarge.copy(fontFamily = Outfit),
    bodyMedium = base.bodyMedium.copy(fontFamily = Outfit),
    bodySmall = base.bodySmall.copy(fontFamily = Outfit),
    labelLarge = base.labelLarge.copy(fontFamily = Outfit),
    labelMedium = base.labelMedium.copy(fontFamily = Outfit),
    labelSmall = base.labelSmall.copy(fontFamily = Outfit),
)

/** Estilo de texto por defecto (Outfit regular, color ink) para usar como base. */
val MapeTextStyle = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal)
