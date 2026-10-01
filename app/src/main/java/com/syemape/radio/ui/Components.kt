package com.syemape.radio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

/**
 * Modifier de pulsación tipo "PressableScale" del diseño: al presionar escala a
 * 0.95 y opacidad 0.9, con retorno suave. Sin ripple.
 */
@Composable
fun Modifier.pressScale(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "scale")
    val alpha by animateFloatAsState(if (pressed) 0.9f else 1f, label = "alpha")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Avatar circular con iniciales sobre color de fondo. */
@Composable
fun Avatar(
    initials: String,
    bg: Color,
    size: Dp = 42.dp,
    border: Dp = 0.dp,
    borderColor: Color = MapeColors.White,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(if (border > 0.dp) Modifier.border(border, borderColor, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = MapeColors.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.33f).sp,
        )
    }
}

/** Colores de avatar variados (réplica aproximada de las variantes juan/luis/carlos/rosa). */
private val avatarPalette = listOf(
    Color(0xFF347B5D), // juan - verde
    Color(0xFFB86B58), // luis - terracota
    Color(0xFF3A715A), // carlos - verde oscuro
    Color(0xFFC08457), // rosa - ocre
    Color(0xFF4A6FA5), // azul
    Color(0xFF8A5A9E), // morado
)

fun avatarColor(seed: String): Color =
    avatarPalette[(seed.sumOf { it.code } % avatarPalette.size).let { if (it < 0) it + avatarPalette.size else it }]

fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Botón redondo con icono (claro u oscuro). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    size: Dp = 46.dp,
    bg: Color = MapeColors.Ink,
    tint: Color = MapeColors.White,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .pressScale { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Punto simple. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/**
 * Fila de chips de filtro tipo píldora. Variante clara (fondo blanco / activo ink)
 * o oscura (fondo #1A1A1A / activo rojo). Con punto rojo opcional en el activo.
 */
@Composable
fun FilterRow(
    items: List<String>,
    selected: Int,
    dark: Boolean = false,
    activeDot: Boolean = false,
    onSelect: (Int) -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, label ->
            val active = index == selected
            val bg = when {
                active && dark -> MapeColors.Red
                active -> MapeColors.Ink
                dark -> MapeColors.PanelDark
                else -> MapeColors.White
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .then(if (dark) Modifier.border(1.dp, MapeColors.PanelBorder, CircleShape) else Modifier)
                    .pressScale { onSelect(index) }
                    .padding(horizontal = 16.dp)
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active && activeDot && !dark) {
                    Dot(MapeColors.Red, 8.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = if (active || dark) MapeColors.White else MapeColors.Ink,
                    fontFamily = Outfit,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
