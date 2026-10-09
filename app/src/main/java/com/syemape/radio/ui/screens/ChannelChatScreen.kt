package com.syemape.radio.ui.screens

import android.content.Intent
import android.net.Uri
import android.app.Activity
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.snapshotFlow
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
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
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
private const val CHANNEL_HISTORY_PAGE_SIZE = 50

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

/** Índice real de fila en LazyColumn, contando el separador de cada día. */
private fun rowIndexForTransmission(messages: List<RadioTransmission>, targetId: String): Int? {
    var row = 0
    var previousDay: String? = null
    for (message in messages) {
        val day = Fmt.dayKey(message.createdAt)
        if (day != previousDay) {
            row++
            previousDay = day
        }
        if (message.id == targetId) return row
        row++
    }
    return null
}

@Composable
fun ChannelChatScreen(channelId: String, title: String, topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val meId = SessionManager.user?.id
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember(channelId) { mutableStateListOf<RadioTransmission>() }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var initialLoading by remember(channelId) { mutableStateOf(true) }
    var initialLoadFailed by remember(channelId) { mutableStateOf(false) }
    var loadingOlder by remember(channelId) { mutableStateOf(false) }
    var olderLoadFailed by remember(channelId) { mutableStateOf(false) }
    var hasMoreHistory by remember(channelId) { mutableStateOf(true) }
    var oldestHistoryCursor by remember(channelId) { mutableStateOf<String?>(null) }

    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var attachMenu by remember { mutableStateOf(false) }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var cameraOutput by remember { mutableStateOf<Uri?>(null) }

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    // Abre un archivo (video/documento) con una app externa (navegador/reproductor).
    fun openExternally(key: String?) {
        val url = urlOf(key) ?: return
        runCatching {
            val uri = Uri.parse(url)
            val filename = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "archivo-chat"
            val request = android.app.DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Descarga del chat")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            (context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
            toast("Descarga iniciada; puedes ver el progreso en las notificaciones")
        }.onFailure { toast("No se pudo descargar el archivo") }
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
            uploadProgress = 0f
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    MediaUploader.upload(context, picked) { progress ->
                        scope.launch(Dispatchers.Main) { uploadProgress = progress }
                    }
                }
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
    val takeCameraMedia = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val captured = cameraOutput
        cameraOutput = null
        if (result.resultCode == Activity.RESULT_OK && captured != null) sendMedia(captured)
        else captured?.let { runCatching { context.contentResolver.delete(it, null, null) } }
    }

    fun launchNativeCamera(video: Boolean) {
        runCatching {
            val file = File.createTempFile(if (video) "channel-video-" else "channel-photo-", if (video) ".mp4" else ".jpg", context.cacheDir)
            val output = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val action = if (video) android.provider.MediaStore.ACTION_VIDEO_CAPTURE else android.provider.MediaStore.ACTION_IMAGE_CAPTURE
            val intent = Intent(action).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, output)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (intent.resolveActivity(context.packageManager) == null) {
                file.delete()
                toast("No hay una cámara disponible")
            } else {
                cameraOutput = output
                takeCameraMedia.launch(intent)
            }
        }.onFailure { toast("No se pudo abrir la cámara") }
    }

    // Notas de voz: las reproduce RadioManager con el MISMO enrutado que la radio
    // (stream de llamada + altavoz + volumen de la radio) para que suenen fuerte.
    var playingId by remember { mutableStateOf<String?>(null) }
    var loadingVoiceId by remember { mutableStateOf<String?>(null) }
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
        loadingVoiceId = note.id
        com.syemape.radio.data.RadioManager.playVoiceNote(
            url = url,
            id = note.id,
            onCompletion = {
                scope.launch { if (generation == playbackGeneration) playSequence(sequence, index + 1, generation) }
            },
            onState = {
                playingId = it
                if (it == note.id || (it == null && loadingVoiceId == note.id)) loadingVoiceId = null
                if (it != null) scrollToVoice(it)
            },
        )
    }

    suspend fun loadLatestHistory() {
        initialLoading = true
        initialLoadFailed = false
        items.clear()
        oldestHistoryCursor = null
        val page = try {
            withContext(Dispatchers.IO) { Backend.api.radioHistory(channelId, CHANNEL_HISTORY_PAGE_SIZE) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (page == null) {
            initialLoadFailed = true
            hasMoreHistory = false
        } else {
            items.addAll(page.reversed())
            hasMoreHistory = page.size >= CHANNEL_HISTORY_PAGE_SIZE
            oldestHistoryCursor = page.lastOrNull()?.id
            withFrameNanos { }
            if (items.isNotEmpty()) listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
        initialLoading = false
    }

    suspend fun loadOlderHistory() {
        if (initialLoading || loadingOlder || !hasMoreHistory || items.isEmpty()) return
        val requestChannel = channelId
        val beforeId = oldestHistoryCursor ?: items.first().id
        val anchor = listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
            val id = info.key as? String
            if (id != null && items.any { it.id == id }) id to info.offset else null
        }
        loadingOlder = true
        olderLoadFailed = false
        try {
            val page = withContext(Dispatchers.IO) {
                Backend.api.radioHistory(requestChannel, CHANNEL_HISTORY_PAGE_SIZE, beforeId)
            }
            if (requestChannel != channelId) return
            val nextCursor = page.lastOrNull()?.id
            // El cursor avanza con lo que devolvió el backend aunque haya ids duplicados
            // en la lista local (p.ej., mensajes en vivo recibidos durante la carga).
            val cursorAdvanced = nextCursor != null && nextCursor != beforeId
            hasMoreHistory = page.size >= CHANNEL_HISTORY_PAGE_SIZE && cursorAdvanced
            if (cursorAdvanced) oldestHistoryCursor = nextCursor
            val existingIds = items.mapTo(HashSet()) { it.id }
            val older = page.asReversed().filterNot { it.id in existingIds }
            if (older.isNotEmpty()) {
                items.addAll(0, older)
                withFrameNanos { }
                anchor?.let { (id, offset) ->
                    rowIndexForTransmission(items, id)?.let { row -> listState.scrollToItem(row, -offset) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (requestChannel == channelId) olderLoadFailed = true
        } finally {
            // Siempre ocultar el spinner ante éxito, error, cancelación o cambio de canal.
            loadingOlder = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playbackGeneration++
            playbackQueue = emptyList()
            com.syemape.radio.data.RadioManager.stopVoiceNote { }
        }
    }
    fun toggleVoice(t: RadioTransmission) {
        if (playingId == t.id || loadingVoiceId == t.id) {
            playbackGeneration++
            playbackQueue = emptyList()
            loadingVoiceId = null
            com.syemape.radio.data.RadioManager.stopVoiceNote { playingId = null }
            return
        }
        val sequence = items.filter { !it.audioKey.isNullOrBlank() }
        val start = sequence.indexOfFirst { it.id == t.id }
        if (start < 0) return
        playbackGeneration++
        playbackQueue = sequence.drop(start)
        loadingVoiceId = t.id
        playSequence(playbackQueue, 0, playbackGeneration)
    }

    LaunchedEffect(channelId) {
        hasMoreHistory = true
        loadLatestHistory()
    }

    LaunchedEffect(channelId) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                if (!initialLoading && hasMoreHistory && !loadingOlder && firstIndex <= 1) loadOlderHistory()
        }
    }

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
            scope.launch(Dispatchers.Main) {
                if (items.none { it.id == t.id }) {
                    val layout = listState.layoutInfo
                    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                    val wasAtBottom = lastVisible == null || lastVisible >= layout.totalItemsCount - 2
                    items.add(t)
                    if (wasAtBottom) {
                        withFrameNanos { }
                        val last = listState.layoutInfo.totalItemsCount - 1
                        if (last >= 0) listState.animateScrollToItem(last)
                    }
                }
            }
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

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            t.audioKey != null -> VoiceBubble(t, mine, playingId == t.id, loadingVoiceId == t.id) { toggleVoice(t) }
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
            if (initialLoading && items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = MapeColors.Red)
                }
            } else if (initialLoadFailed && items.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().clickable { scope.launch { loadLatestHistory() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No se cargó el historial. Toca para reintentar", color = MapeColors.TextMuted, fontFamily = Outfit)
                }
            }
            if (hasMoreHistory && items.isNotEmpty() || loadingOlder || olderLoadFailed) {
                Row(
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .clip(CircleShape)
                        .background(MapeColors.Card)
                        .then(if (!loadingOlder) Modifier.clickable { scope.launch { loadOlderHistory() } } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (loadingOlder) androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MapeColors.Red,
                    )
                    Text(
                        when {
                            loadingOlder -> "Cargando mensajes anteriores…"
                            olderLoadFailed -> "No se cargaron mensajes anteriores · toca para reintentar"
                            else -> "Toca para cargar mensajes anteriores"
                        },
                        color = MapeColors.Text,
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(MapeColors.Card).padding(horizontal = 12.dp, vertical = 10.dp).padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uploading) Text(
                "${(uploadProgress * 100).toInt()}%",
                color = MapeColors.Red,
                fontFamily = Outfit,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
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
                        text = { Text("Tomar foto", fontFamily = Outfit) },
                        leadingIcon = { Icon(MapeIcons.Image, null, modifier = Modifier.size(20.dp)) },
                        onClick = { attachMenu = false; launchNativeCamera(video = false) },
                    )
                    DropdownMenuItem(
                        text = { Text("Grabar video", fontFamily = Outfit) },
                        leadingIcon = { Icon(MapeIcons.Video, null, modifier = Modifier.size(20.dp)) },
                        onClick = { attachMenu = false; launchNativeCamera(video = true) },
                    )
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
private fun VoiceBubble(t: RadioTransmission, mine: Boolean, playing: Boolean, loading: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.pressScale { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (mine) MapeColors.White else MapeColors.Ink),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(17.dp), color = if (mine) MapeColors.Ink else MapeColors.White, strokeWidth = 2.dp,
            ) else Icon(
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
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Imagen",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 230.dp)
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressScale { onClick() },
        loading = {
            Box(Modifier.size(150.dp, 96.dp).clip(RoundedCornerShape(12.dp)).background(MapeColors.Card), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MapeColors.Red, strokeWidth = 2.dp)
            }
        },
        error = {
            Text("No se pudo cargar la imagen", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp)
        },
        success = { SubcomposeAsyncImageContent() },
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
