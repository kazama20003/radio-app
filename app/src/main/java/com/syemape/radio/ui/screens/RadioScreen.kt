package com.syemape.radio.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.RadioManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

@Composable
fun RadioScreen(topPadding: Dp, onOpenChannelChat: (String, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) RadioManager.start(app) }
    DisposableEffect(Unit) {
        if (RadioManager.hasMicPermission(context)) RadioManager.start(app)
        else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        onDispose { } // la radio sigue viva en 2º plano; se detiene al cerrar sesión
    }
    var favorite by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MapeColors.White).padding(top = topPadding + 14.dp, start = 24.dp, end = 24.dp, bottom = 108.dp),
    ) {
        // ---- Barra superior: radio + canales (tipo FM/AM) ----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MapeColors.Bg), contentAlignment = Alignment.Center) {
                Icon(MapeIcons.Radio, null, tint = MapeColors.Ink, modifier = Modifier.size(22.dp))
            }
            Row(
                Modifier.clip(CircleShape).background(MapeColors.Bg).padding(4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RadioManager.channels.forEach { ch ->
                    val active = ch.id == RadioManager.channelId
                    Box(
                        Modifier.clip(CircleShape).background(if (active) MapeColors.Ink else Color.Transparent).pressScale { RadioManager.selectChannel(ch.id) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(ch.name ?: "Canal", color = if (active) MapeColors.White else MapeColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        // ---- Nombre de canal grande (editorial) ----
        Text(
            RadioManager.channelName,
            color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 48.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (RadioManager.connected) MapeColors.Red else MapeColors.TextFaint))
            Text(
                "${RadioManager.members} conectados · ${if (RadioManager.connected) "En vivo" else "Conectando…"}",
                color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(22.dp))

        // ---- Estrella + compartir ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (favorite) MapeColors.RedSoftBg else MapeColors.Bg).pressScale { favorite = !favorite },
                contentAlignment = Alignment.Center,
            ) { Icon(if (favorite) MapeIcons.StarFilled else MapeIcons.Star, null, tint = MapeColors.Red, modifier = Modifier.size(22.dp)) }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MapeColors.Bg).pressScale { },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.Share, null, tint = MapeColors.Ink, modifier = Modifier.size(20.dp)) }
        }

        Spacer(Modifier.height(26.dp))

        // ---- Onda de audio animada (estilo dial) con indicador rojo ----
        TunerRuler(
            active = RadioManager.talking || RadioManager.remoteSpeaking,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )

        Spacer(Modifier.height(26.dp))

        // ---- Estado (quién habla) ----
        val (label, name) = when {
            RadioManager.talking -> "TRANSMITIENDO" to "Tú · en el canal"
            RadioManager.remoteSpeaking -> "HABLANDO AHORA" to (RadioManager.speakerLabel ?: "En vivo")
            RadioManager.txFailed -> "ERROR" to "No se pudo transmitir"
            else -> "EN EL CANAL" to "En silencio"
        }
        Text(label, color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(2.dp))
        Text(name, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)

        Spacer(Modifier.weight(1f))

        // ---- Volumen + salida ----
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MapeColors.Bg).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.pressScale { RadioManager.toggleSpeaker() }) {
                Icon(MapeIcons.Speaker, null, tint = if (RadioManager.speakerOn) MapeColors.Ink else MapeColors.TextFaint, modifier = Modifier.size(20.dp))
            }
            VolumeSlider(0.8f, Modifier.weight(1f))
            Text(if (RadioManager.speakerOn) "Altavoz" else RadioManager.normalDeviceLabel, color = MapeColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))

        // ---- Controles: Último · HABLAR · Chat ----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ControlSquare(MapeIcons.Prev, enabled = false) { }
            HoldTalkButton(Modifier.weight(1f))
            ControlSquare(MapeIcons.Chat, enabled = RadioManager.channelId != null) {
                RadioManager.channelId?.let { onOpenChannelChat(it, RadioManager.channelName) }
            }
        }
    }
}

@Composable
private fun HoldTalkButton(modifier: Modifier) {
    Row(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (RadioManager.talking) MapeColors.Red else MapeColors.Ink)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    RadioManager.startTalking()
                    tryAwaitRelease()
                    RadioManager.stopTalking()
                })
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(MapeIcons.Mic, null, tint = MapeColors.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (RadioManager.talking) "CORTAR" else "HABLAR", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun ControlSquare(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(MapeColors.Bg).pressScale(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MapeColors.Ink.copy(alpha = alpha), modifier = Modifier.size(24.dp)) }
}

/**
 * Onda de audio animada con estética de dial: líneas verticales finas cuya altura
 * recorre una onda (como sonido en vivo) + indicador rojo central. [active] sube la
 * amplitud cuando alguien transmite.
 */
@Composable
private fun TunerRuler(active: Boolean, modifier: Modifier) {
    val tickColor = MapeColors.Border
    val tickActive = MapeColors.Ink.copy(alpha = 0.55f)
    val red = MapeColors.Red
    val transition = rememberInfiniteTransition(label = "audiowave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 900 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val n = 52
        val gap = w / n
        val amp = if (active) 1f else 0.45f
        for (i in 0..n) {
            val x = i * gap
            val t = i.toFloat() / n
            val env = kotlin.math.sin(t * Math.PI).toFloat() // atenúa en los bordes
            val wave = kotlin.math.sin((t * 20f + phase).toDouble()).toFloat() * 0.6f +
                kotlin.math.sin((t * 37f - phase * 1.6f).toDouble()).toFloat() * 0.4f
            val norm = (0.5f + 0.5f * wave).coerceIn(0f, 1f)
            val tickH = (h * (0.14f + 0.46f * amp * env * norm)).coerceIn(3f, h)
            drawLine(
                color = if (active) tickActive else tickColor,
                start = Offset(x, h / 2f - tickH / 2f),
                end = Offset(x, h / 2f + tickH / 2f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            color = red,
            start = Offset(w / 2f, 0f),
            end = Offset(w / 2f, h),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun VolumeSlider(fraction: Float, modifier: Modifier) {
    BoxWithConstraints(modifier.height(34.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MapeColors.Border))
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(MapeColors.Ink))
        Box(
            Modifier.offset(x = w * fraction - 11.dp).size(22.dp).clip(CircleShape).background(MapeColors.Ink).border(3.dp, MapeColors.White, CircleShape),
        )
    }
}
