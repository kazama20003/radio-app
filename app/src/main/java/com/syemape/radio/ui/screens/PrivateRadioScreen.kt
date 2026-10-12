package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

    Column(
        Modifier.fillMaxSize().background(PrivateBg).padding(top = topPadding).padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(PrivateCard).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.ArrowLeft, "Volver", tint = PrivateText, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text("Radio privada", color = PrivateText, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(peerName, color = PrivateMuted, fontFamily = Outfit, fontSize = 14.sp, maxLines = 1)
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (RadioManager.connected) Color(0xFF20B15A) else PrivateRed))
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PrivateCard)
                .border(1.dp, Color(0xFF3B3B3B), RoundedCornerShape(18.dp)).padding(16.dp),
        ) {
            Text(
                when {
                    RadioManager.talking -> "Tú estás hablando con $peerName"
                    RadioManager.remoteSpeaking -> "${RadioManager.speakerLabel ?: peerName} está hablando"
                    RadioManager.connected -> "Conexión privada lista"
                    else -> "Conectando radio privada…"
                },
                color = if (RadioManager.remoteSpeaking || RadioManager.talking) PrivateRed else PrivateText,
                fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text("Solo tú y $peerName pueden escuchar esta transmisión.", color = PrivateMuted, fontFamily = Outfit, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        val note = latestVoice
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PrivateCard)
                .border(1.dp, Color(0xFF3B3B3B), RoundedCornerShape(18.dp)).padding(14.dp),
        ) {
            Text("ÚLTIMA TRANSMISIÓN PRIVADA", color = PrivateRed, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.7.sp)
            Spacer(Modifier.height(8.dp))
            if (note == null) {
                Text("Todavía no hay transmisiones guardadas", color = PrivateMuted, fontFamily = Outfit, fontSize = 14.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(PrivateRed).clickable {
                            val key = note.attachmentKey.orEmpty()
                            val url = when {
                                key.startsWith("http") -> key
                                key.startsWith("/") -> BuildConfig.API_BASE_URL.substringBefore("/api") + key
                                else -> null
                            }
                            if (url != null) RadioManager.playVoiceNote(url, note.id, onState = { playingId = it })
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playingId == note.id) MapeIcons.Pause else MapeIcons.Play, "Reproducir última transmisión", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(note.sender?.nickname?.takeIf { it.isNotBlank() } ?: note.sender?.name ?: "Usuario", color = PrivateText, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${Fmt.shortTime(note.createdAt)} · ${note.durationSec ?: 0}s", color = PrivateMuted, fontFamily = Outfit, fontSize = 12.sp)
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(bottom = 30.dp), contentAlignment = Alignment.Center) {
            val micSize = minOf(maxWidth, maxHeight, 300.dp)
            if (micSize > 0.dp) RadioPushToTalkButton(micSize)
        }
    }
}
