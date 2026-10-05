package com.syemape.radio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.syemape.radio.BuildConfig
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.MediaUploader
import com.syemape.radio.data.RadioTransmission
import com.syemape.radio.data.Realtime
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun RadioTransmission.preview(): String = when {
    !text.isNullOrBlank() -> text
    audioKey != null -> "🎤 Nota de voz" + (durationSec?.let { " · ${it.toInt()}s" } ?: "")
    imageKey != null -> "📷 Imagen"
    videoKey != null -> "🎬 Video"
    fileKey != null -> "📎 " + (fileName ?: "Archivo")
    else -> ""
}

/** Origen del backend (sin /api) para construir URLs de archivos servidos. */
private val mediaOrigin: String = BuildConfig.API_BASE_URL.substringBefore("/api")

/** URL completa de un archivo a partir de su key (`/uploads/...`). */
private fun urlOf(key: String?): String? = when {
    key.isNullOrBlank() -> null
    key.startsWith("http") -> key
    key.startsWith("/") -> mediaOrigin + key
    else -> null
}

/** Tamaño legible (p.ej. "3.2 MB"). */
private fun humanSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt()} KB"
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

@Composable
fun ChannelChatScreen(channelId: String, title: String, topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val meId = SessionManager.user?.id
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<RadioTransmission>() }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var uploading by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    // Abre un archivo (video/documento) con una app externa (navegador/reproductor).
    fun openExternally(key: String?) {
        val url = urlOf(key) ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("No se pudo abrir el archivo") }
    }

    // Sube un adjunto elegido y lo publica en el canal.
    fun sendMedia(uri: Uri) {
        val picked = runCatching { MediaUploader.query(context, uri) }.getOrNull() ?: return
        if (picked.size > MediaUploader.MAX_UPLOAD_BYTES) {
            toast("Archivo muy grande (máx 100 MB)")
            return
        }
        scope.launch {
            uploading = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching { MediaUploader.upload(context, picked) }
            }
            uploading = false
            val res = outcome.getOrNull()
            if (res == null) {
                val tooLarge = outcome.exceptionOrNull() is com.syemape.radio.data.MediaUploadTooLargeException
                toast(if (tooLarge) "Archivo muy grande (máx 100 MB)" else "No se pudo subir el archivo")
                return@launch
            }
            val kind = MediaUploader.kindOf(picked.mime)
            Realtime.socket("/radio").emit(
                "channel:media",
                JSONObject()
                    .put("channelId", channelId)
                    .put("kind", kind)
                    .put("key", res.key)
                    .put("fileName", picked.name)
                    .put("fileSize", if (picked.size > 0) picked.size else (res.size ?: 0))
                    .put("mimeType", picked.mime),
            )
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) sendMedia(uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sendMedia(uri)
    }

    // Notas de voz: las reproduce RadioManager con el MISMO enrutado que la radio
    // (stream de llamada + altavoz + volumen de la radio) para que suenen fuerte.
    var playingId by remember { mutableStateOf<String?>(null) }
    var playbackQueue by remember { mutableStateOf<List<RadioTransmission>>(emptyList()) }
    var playbackGeneration by remember { mutableStateOf(0) }
    fun scrollToVoice(id: String) {
        var row = 0
        var previousDay: String? = null
        for (message in items) {
            val day = Fmt.dayKey(message.createdAt)
            if (day != previousDay) {
                row++ // encabezado de fecha
                previousDay = day
            }
            if (message.id == id) break
            row++
        }
        scope.launch { listState.animateScrollToItem(row.coerceAtLeast(0)) }
    }
    fun playSequence(sequence: List<RadioTransmission>, index: Int, generation: Int) {
        if (generation != playbackGeneration) return
        val note = sequence.getOrNull(index)
        if (note == null) {
            playbackQueue = emptyList()
            playingId = null
            return
        }
        val url = urlOf(note.audioKey) ?: run {
            scope.launch { playSequence(sequence, index + 1, generation) }
            return
        }
        com.syemape.radio.data.RadioManager.playVoiceNote(
            url = url,
            id = note.id,
            onCompletion = {
                scope.launch { if (generation == playbackGeneration) playSequence(sequence, index + 1, generation) }
            },
            onState = {
                playingId = it
                if (it != null) scrollToVoice(it)
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            playbackGeneration++
            playbackQueue = emptyList()
            com.syemape.radio.data.RadioManager.stopVoiceNote { }
        }
    }
    fun toggleVoice(t: RadioTransmission) {
        if (playingId == t.id) {
            playbackGeneration++
            playbackQueue = emptyList()
            com.syemape.radio.data.RadioManager.stopVoiceNote { playingId = null }
            return
        }
        val sequence = items.filter { !it.audioKey.isNullOrBlank() }
        val start = sequence.indexOfFirst { it.id == t.id }
        if (start < 0) return
        playbackGeneration++
        playbackQueue = sequence.drop(start)
        playSequence(playbackQueue, 0, playbackGeneration)
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
        com.syemape.radio.data.RadioManager.setOpenChatChannel(channelId)
        // Asegura estar en la sala del canal para recibir eventos en vivo.
        if (!socket.connected()) socket.connect()
        socket.emit("channel:join", channelId)
        // Añade una transmisión (texto/imagen/media vía channel:post, o nota de voz vía ptt:ended).
        val addFromEvent = io.socket.emitter.Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            if (o.optString("channelId") != channelId) return@Listener
            if (!o.has("transmission")) return@Listener // ptt:ended sin grabación: ignorar
            val t = runCatching { Realtime.gson.fromJson(o.getJSONObject("transmission").toString(), RadioTransmission::class.java) }.getOrNull()
                ?: return@Listener
            scope.launch(Dispatchers.Main) { if (items.none { it.id == t.id }) items.add(t) }
        }
        socket.on("channel:post", addFromEvent) // texto / imagen / video / archivo
        socket.on("ptt:ended", addFromEvent)    // nota de voz grabada
        onDispose {
            socket.off("channel:post", addFromEvent)
            socket.off("ptt:ended", addFromEvent)
            com.syemape.radio.data.RadioManager.setOpenChatChannel(null)
            // /radio usa un solo socket y una sola sala activa. Restablece la sala que
            // sigue mostrando el sintonizador al cerrar el chat de otro canal.
            val tunedChannel = com.syemape.radio.data.RadioManager.channelId
            if (tunedChannel != channelId) {
                socket.emit("channel:leave", channelId)
                tunedChannel?.let { socket.emit("channel:join", it) }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MapeColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().background(MapeColors.Card).padding(top = topPadding).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).pressScale { onBack() }, contentAlignment = Alignment.Center) {
                Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.Text, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(title, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Chat del canal", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var previousDay: String? = null
            items.forEach { t ->
                val day = Fmt.dayKey(t.createdAt)
                if (day != previousDay) {
                    item(key = "channel-day-$day") { ChannelDateDivider(Fmt.dayLabel(t.createdAt)) }
                    previousDay = day
                }
                item(key = t.id) {
                val mine = t.senderId == meId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) MapeColors.Ink else MapeColors.Card).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (!mine) {
                            Text(t.sender?.nickname ?: t.sender?.name ?: "—", color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                        }
                        when {
                            t.audioKey != null -> VoiceBubble(t, mine, playingId == t.id) { toggleVoice(t) }
                            t.imageKey != null -> ImageBubble(urlOf(t.imageKey)) { urlOf(t.imageKey)?.let { fullscreenImage = it } }
                            t.videoKey != null -> MediaCard(MapeIcons.Video, "Video", t.fileName ?: "Toca para reproducir", t.fileSize, mine) { openExternally(t.videoKey) }
                            t.fileKey != null -> MediaCard(MapeIcons.FileDoc, t.fileName ?: "Archivo", "Toca para abrir", t.fileSize, mine) { openExternally(t.fileKey) }
                            else -> Text(t.preview(), color = if (mine) MapeColors.White else MapeColors.Text, fontFamily = Outfit, fontSize = 15.sp)
                        }
                        Text(Fmt.clockTime(t.createdAt), color = if (mine) MapeColors.TextOnDark else MapeColors.TextFaint, fontFamily = Outfit, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp).align(Alignment.End))
                    }
                }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(MapeColors.Card).padding(horizontal = 12.dp, vertical = 10.dp).padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Botón adjuntar (foto/video o archivo). Muestra spinner mientras sube.
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MapeColors.Bg).pressScale(enabled = !uploading) { attachMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (uploading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MapeColors.Text,
                        )
                    } else {
                        Icon(MapeIcons.Paperclip, null, tint = MapeColors.Text, modifier = Modifier.size(22.dp))
                    }
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Foto o video", fontFamily = Outfit) },
                        leadingIcon = { Icon(MapeIcons.Image, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            attachMenu = false
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Archivo", fontFamily = Outfit) },
                        leadingIcon = { Icon(MapeIcons.FileDoc, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            attachMenu = false
                            runCatching { pickFile.launch(arrayOf("*/*")) }
                        },
                    )
                }
            }
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape).background(MapeColors.Bg).padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text("Mensaje al canal…", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 15.sp)
                BasicTextField(
                    value = draft, onValueChange = { draft = it.take(2000) },
                    textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = MapeColors.Text),
                    cursorBrush = SolidColor(MapeColors.Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MapeColors.Ink).pressScale(enabled = draft.isNotBlank()) {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        val chatSocket = Realtime.socket("/radio")
                        if (!chatSocket.connected()) {
                            toast("Sin conexión. El mensaje sigue en el borrador.")
                        } else {
                            draft = ""
                            chatSocket.emit(
                            "channel:text",
                            JSONObject().put("channelId", channelId).put("text", text),
                            io.socket.client.Ack { args ->
                                val ack = args.firstOrNull() as? JSONObject
                                if (ack?.optBoolean("ok") != true) {
                                    scope.launch(Dispatchers.Main) {
                                        if (draft.isEmpty()) draft = text
                                        toast("No se pudo enviar el mensaje. Inténtalo de nuevo.")
                                    }
                                }
                            },
                            )
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.Send, null, tint = MapeColors.White, modifier = Modifier.size(20.dp)) }
        }
    }

    // Visor de imagen a pantalla completa.
    fullscreenImage?.let { url ->
        Dialog(onDismissRequest = { fullscreenImage = null }) {
            Box(Modifier.fillMaxSize().pressScale { fullscreenImage = null }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = url, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelDateDivider(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Spacer(Modifier.weight(1f).height(1.dp).background(MapeColors.Border))
        Text(label, color = MapeColors.TextFaint, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
        Spacer(Modifier.weight(1f).height(1.dp).background(MapeColors.Border))
    }
}

@Composable
private fun VoiceBubble(t: RadioTransmission, mine: Boolean, playing: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.pressScale { onToggle() },
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
            color = if (mine) MapeColors.White else MapeColors.Text,
            fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        )
    }
}

@Composable
private fun ImageBubble(url: String?, onClick: () -> Unit) {
    AsyncImage(
        model = url,
        contentDescription = "Imagen",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 230.dp)
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressScale { onClick() },
    )
}

@Composable
private fun MediaCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titleText: String,
    subtitle: String,
    size: Long?,
    mine: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (mine) MapeColors.White else MapeColors.Ink
    Row(
        Modifier.widthIn(max = 240.dp).pressScale { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (mine) MapeColors.White else MapeColors.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (mine) MapeColors.Ink else MapeColors.White, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(titleText, color = fg, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            val sub = humanSize(size).let { if (it.isBlank()) subtitle else "$subtitle · $it" }
            Text(sub, color = if (mine) MapeColors.TextOnDark else MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp, maxLines = 1)
        }
    }
}
