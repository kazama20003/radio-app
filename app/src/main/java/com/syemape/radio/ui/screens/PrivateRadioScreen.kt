package com.syemape.radio.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.BuildConfig
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.Message
import com.syemape.radio.data.RadioManager
import com.syemape.radio.data.Realtime
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.theme.Outfit
import io.socket.emitter.Emitter

private val PrivateBg = Color(0xFF090909)
private val PrivateCard = Color(0xFF1D1D1D)
private val PrivateText = Color(0xFFF3F3F3)
private val PrivateMuted = Color(0xFFAAAAAA)
private val PrivateRed = Color(0xFFD71920)

@Composable
fun PrivateRadioScreen(
    conversationId: String,
    peerName: String,
    topPadding: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var latestVoice by remember(conversationId) { mutableStateOf<Message?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(conversationId, peerName) {
        RadioManager.enterPrivateRadio(conversationId, peerName)
        val socket = Realtime.socket("/radio")
        val listener = Emitter.Listener { args ->
            Realtime.parse<Message>(args)?.let { message ->
                if (message.conversationId == conversationId && message.type == "VOICE") {
                    latestVoice = message
                }
            }
        }
        socket.on("private-radio:message", listener)
        onDispose {
            socket.off("private-radio:message", listener)
            RadioManager.exitPrivateRadio()
        }
    }

    LaunchedEffect(conversationId) {
        runCatching { Backend.api.conversation(conversationId) }
            .onSuccess { detail ->
                latestVoice = detail.messages.lastOrNull { it.type == "VOICE" && !it.attachmentKey.isNullOrBlank() }
            }
    }

    val iAmSpeaking = RadioManager.talking
    val otherIsSpeaking = RadioManager.remoteSpeaking && !iAmSpeaking
    val activeSpeaker = when {
        iAmSpeaking -> SessionManager.user?.nickname?.takeIf { it.isNotBlank() }
            ?: SessionManager.user?.name ?: "Tú"
        otherIsSpeaking -> RadioManager.speakerLabel?.takeIf { it.isNotBlank() } ?: peerName
        else -> null
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0809), PrivateBg, Color(0xFF101010))))
            .padding(top = topPadding).padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(PrivateCard).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.ArrowLeft, "Volver", tint = PrivateText, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("RADIO PRIVADA", color = PrivateText, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.5.sp)
                Text("Con $peerName · canal seguro", color = PrivateMuted, fontFamily = Outfit, fontSize = 12.sp, maxLines = 1)
            }
            Row(
                Modifier.clip(CircleShape).background(if (RadioManager.connected) Color(0xFF123323) else Color(0xFF3A1C1D))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (RadioManager.connected) Color(0xFF28D17C) else PrivateRed))
                Text(if (RadioManager.connected) "EN LÍNEA" else "CONECTANDO", color = PrivateText, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF321315), Color(0xFF201516), Color(0xFF1D1D1D))))
                .border(1.dp, Color(0xFF743034), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (activeSpeaker != null) PrivateRed else Color(0xFF373737)), contentAlignment = Alignment.Center) {
                    Text(if (activeSpeaker != null) "♫" else "●", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        when {
                            iAmSpeaking -> "ESTÁS TRANSMITIENDO"
                            otherIsSpeaking -> "HABLANDO AHORA"
                            RadioManager.connected -> "PRESIONA PARA HABLAR"
                            else -> "PREPARANDO EL CANAL"
                        },
                        color = if (activeSpeaker != null) Color(0xFFFF7777) else PrivateMuted,
                        fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp,
                    )
                    Text(
                        activeSpeaker ?: if (RadioManager.connected) "Solo tú y $peerName escuchan" else "Conectando radio privada…",
                        color = PrivateText, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrivateLiveWave(active = activeSpeaker != null, level = RadioManager.audioLevel)
        }

        Spacer(Modifier.height(12.dp))
        val note = latestVoice
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF181818))
                .border(1.dp, Color(0xFF383838), RoundedCornerShape(18.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (note == null) Color(0xFF303030) else Color(0xFF382022))
                    .then(if (note != null) Modifier.clickable {
                        val key = note.attachmentKey.orEmpty()
                        val url = when {
                            key.startsWith("http") -> key
                            key.startsWith("/") -> BuildConfig.API_BASE_URL.substringBefore("/api") + key
                            else -> null
                        }
                        if (url != null) RadioManager.playVoiceNote(url, note.id, onState = { playingId = it })
                    } else Modifier), contentAlignment = Alignment.Center) {
                    Icon(if (playingId == note?.id && note != null) MapeIcons.Pause else MapeIcons.Play,
                        contentDescription = if (note == null) "Sin nota grabada" else "Reproducir nota privada",
                        tint = if (note == null) PrivateMuted else Color(0xFFFF646A), modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (note == null) "ÚLTIMA NOTA DE VOZ" else "NOTA PRIVADA · ${note.sender?.nickname?.takeIf { it.isNotBlank() } ?: note.sender?.name ?: "Usuario"}",
                        color = if (note == null) PrivateMuted else Color(0xFFFF7777), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                    Text(if (note == null) "Tu transmisión aparecerá aquí al terminar" else "${Fmt.shortTime(note.createdAt)} · ${note.durationSec ?: 0} s · Guardada en este privado",
                        color = PrivateText, fontFamily = Outfit, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val availableMicHeight = (maxHeight - 30.dp).coerceAtLeast(0.dp)
            val micSize = minOf(maxWidth, availableMicHeight, 320.dp)
            if (micSize > 0.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (RadioManager.talking) "SUELTA PARA TERMINAR" else "MANTÉN PRESIONADO PARA HABLAR",
                        color = if (RadioManager.talking) Color(0xFFFF7777) else PrivateMuted,
                        fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                    RadioPushToTalkButton(micSize)
                }
            }
        }
    }
}

@Composable
private fun PrivateLiveWave(active: Boolean, level: Float) {
    val transition = rememberInfiniteTransition(label = "private-radio-wave")
    val phase by transition.animateFloat(0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(if (active) 520 else 1800, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val bars = 48
        val gap = size.width / bars
        val gain = if (active) (0.25f + level * 0.75f).coerceIn(0.25f, 1f) else 0.24f
        for (i in 0 until bars) {
            val wave = kotlin.math.abs(kotlin.math.sin(i * 0.38 + phase * if (active) 1.6f else 0.35f)).toFloat()
            val h = size.height * (0.18f + wave * gain * 0.72f)
            val x = i * gap + gap / 2
            drawLine(if (active) Color(0xFFE5322D) else Color(0xFF686868),
                Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
    }
}
