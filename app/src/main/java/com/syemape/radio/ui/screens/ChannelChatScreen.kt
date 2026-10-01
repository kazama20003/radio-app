package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.BuildConfig
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.RadioTransmission
import com.syemape.radio.data.Realtime
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun RadioTransmission.preview(): String = when {
    !text.isNullOrBlank() -> text
    audioKey != null -> "🎤 Nota de voz" + (durationSec?.let { " · ${it.toInt()}s" } ?: "")
    imageKey != null -> "📷 Imagen"
    else -> ""
}

/** Origen del backend (sin /api) para construir URLs de archivos servidos. */
private val mediaOrigin: String = BuildConfig.API_BASE_URL.substringBefore("/api")

/** URL completa de una nota de voz a partir de su key (`/uploads/...`). */
private fun audioUrlOf(key: String?): String? = when {
    key.isNullOrBlank() -> null
    key.startsWith("http") -> key
    key.startsWith("/") -> mediaOrigin + key
    else -> null
}

@Composable
fun ChannelChatScreen(channelId: String, title: String, topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val meId = SessionManager.user?.id
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<RadioTransmission>() }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Reproductor de notas de voz (uno compartido; playingId = burbuja sonando).
    val player = remember { android.media.MediaPlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    fun toggleVoice(t: RadioTransmission) {
        val url = audioUrlOf(t.audioKey) ?: return
        if (playingId == t.id) { // ya sonando esta → pausar/detener
            runCatching { player.stop() }
            playingId = null
            return
        }
        runCatching {
            player.reset()
            player.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(url)
            player.setOnPreparedListener { it.start(); playingId = t.id }
            player.setOnCompletionListener { playingId = null }
            player.setOnErrorListener { _, _, _ -> playingId = null; true }
            player.prepareAsync()
        }.onFailure { playingId = null }
    }

    LaunchedEffect(channelId) {
        // El historial viene del más nuevo al más viejo: lo invertimos (orden cronológico).
        runCatching { Backend.api.radioHistory(channelId) }.getOrNull()?.let {
            items.clear(); items.addAll(it.reversed())
            if (items.isNotEmpty()) listState.scrollToItem(items.size - 1) // ir al último
        }
    }
    LaunchedEffect(items.size) { if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1) }

    DisposableEffect(channelId) {
        val socket = Realtime.socket("/radio")
        // Asegura estar en la sala del canal para recibir eventos en vivo.
        if (!socket.connected()) socket.connect()
        socket.emit("channel:join", channelId)
        // Añade una transmisión (texto/imagen vía channel:post, o nota de voz vía ptt:ended).
        val addFromEvent = io.socket.emitter.Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            if (o.optString("channelId") != channelId) return@Listener
            if (!o.has("transmission")) return@Listener // ptt:ended sin grabación: ignorar
            val t = runCatching { Realtime.gson.fromJson(o.getJSONObject("transmission").toString(), RadioTransmission::class.java) }.getOrNull()
                ?: return@Listener
            scope.launch(Dispatchers.Main) { if (items.none { it.id == t.id }) items.add(t) }
        }
        socket.on("channel:post", addFromEvent) // texto / imagen
        socket.on("ptt:ended", addFromEvent)    // nota de voz grabada
        onDispose { socket.off("channel:post", addFromEvent); socket.off("ptt:ended", addFromEvent) }
    }

    Column(Modifier.fillMaxSize().background(MapeColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().background(MapeColors.White).padding(top = topPadding).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).pressScale { onBack() }, contentAlignment = Alignment.Center) {
                Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.Ink, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(title, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Chat del canal", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items) { t ->
                val mine = t.senderId == meId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) MapeColors.Ink else MapeColors.White).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (!mine) {
                            Text(t.sender?.nickname ?: t.sender?.name ?: "—", color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                        }
                        if (t.audioKey != null) {
                            val playing = playingId == t.id
                            Row(
                                Modifier.pressScale { toggleVoice(t) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(if (mine) MapeColors.White else MapeColors.Ink),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (playing) MapeIcons.Pause else MapeIcons.Play,
                                        null,
                                        tint = if (mine) MapeColors.Ink else MapeColors.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    "Nota de voz" + (t.durationSec?.let { " · ${it.toInt()}s" } ?: ""),
                                    color = if (mine) MapeColors.White else MapeColors.Ink,
                                    fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                )
                            }
                        } else {
                            Text(t.preview(), color = if (mine) MapeColors.White else MapeColors.Ink, fontFamily = Outfit, fontSize = 15.sp)
                        }
                        Text(Fmt.shortTime(t.createdAt), color = if (mine) MapeColors.TextOnDark else MapeColors.TextFaint, fontFamily = Outfit, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp).align(Alignment.End))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(MapeColors.White).padding(horizontal = 12.dp, vertical = 10.dp).padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(CircleShape).background(MapeColors.Bg).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text("Mensaje al canal…", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 15.sp)
                BasicTextField(
                    value = draft, onValueChange = { draft = it },
                    textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = MapeColors.Ink),
                    cursorBrush = SolidColor(MapeColors.Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MapeColors.Ink).pressScale(enabled = draft.isNotBlank()) {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        Realtime.socket("/radio").emit("channel:text", JSONObject().put("channelId", channelId).put("text", text))
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.Send, null, tint = MapeColors.White, modifier = Modifier.size(20.dp)) }
        }
    }
}
