package com.syemape.radio.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syemape.radio.ui.theme.MapeColors

/** Punto "en vivo" con halo que se expande y se desvanece en bucle (1600 ms). */
@Composable
fun LiveDot(size: Dp = 8.dp, color: Color = MapeColors.Red) {
    val transition = rememberInfiniteTransition(label = "livedot")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val s = 1f + progress * 1.9f
                    scaleX = s
                    scaleY = s
                    alpha = (0.55f * (1f - progress)).coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(color),
        )
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Barras de audio animadas (7 barras con scaleY oscilante). */
@Composable
fun Waveform(color: Color = MapeColors.Red) {
    val heights = listOf(8, 18, 26, 12, 22, 10, 16)
    val transition = rememberInfiniteTransition(label = "wave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { i, h ->
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = (i % 4) * 150, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .graphicsLayer { scaleY = scale }
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/**
 * Onda de sonido tipo ecualizador (muchas barras verticales animadas con una
 * envolvente centrada). [active] sube la amplitud cuando alguien transmite.
 */
@Composable
fun RadioWave(active: Boolean, color: Color = MapeColors.Red, modifier: Modifier = Modifier) {
    val bars = 32
    val transition = rememberInfiniteTransition(label = "radiowave")
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(680 + (i % 7) * 90, delayMillis = (i % 9) * 45, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            val env = kotlin.math.sin((i.toFloat() / (bars - 1)) * Math.PI).toFloat() // 0..1..0 centrada
            val maxH = if (active) 92f else 26f
            val minH = 6f
            val h = (minH + (maxH - minH) * env * (0.35f + 0.65f * phase)).dp
            Box(
                Modifier
                    .weight(1f)
                    .height(h)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) color else color.copy(alpha = 0.35f)),
            )
        }
    }
}

/** Anillo del PTT que se expande (scale 0.92→1.22) y se desvanece, en bucle. */
@Composable
fun PingRing(diameter: Dp, color: Color, delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "ping")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    Box(
        Modifier
            .size(diameter)
            .graphicsLayer {
                val s = 0.92f + progress * 0.30f
                scaleX = s
                scaleY = s
                alpha = (0.9f * (1f - progress)).coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .border(1.5.dp, color, CircleShape),
    )
}
