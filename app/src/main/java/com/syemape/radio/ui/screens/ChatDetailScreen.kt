package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
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
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.Message
import com.syemape.radio.data.SendMessageRequest
import com.syemape.radio.data.MediaUploader
import com.syemape.radio.data.PickedFile
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatDetailScreen(conversationId: String, title: String, topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val meId = SessionManager.user?.id
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Message>() }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) {
        loading = true
        runCatching { Backend.api.conversation(conversationId) }
            .onSuccess {
                messages.clear(); messages.addAll(it.messages)
                runCatching { Backend.api.markRead(conversationId) }
                com.syemape.radio.data.AppBadges.refresh()
            }
        loading = false
    }

    androidx.compose.runtime.DisposableEffect(recorder) {
        val activeRecorder = recorder
        onDispose {
            if (activeRecorder != null) {
                runCatching { activeRecorder.stop() }
                runCatching { activeRecorder.release() }
                recordingFile?.delete()
            }
        }
    }

    // Recepción en vivo por Socket.IO: une a la sala y escucha message:new.
    androidx.compose.runtime.DisposableEffect(conversationId) {
        val socket = com.syemape.radio.data.Realtime.socket("/chat")
        socket.emit("conversation:join", conversationId)
        val listener = io.socket.emitter.Emitter.Listener { args ->
            val msg = com.syemape.radio.data.Realtime.parse<Message>(args) ?: return@Listener
            if (msg.conversationId == null || msg.conversationId == conversationId) {
                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (messages.none { it.id == msg.id }) messages.add(msg)
                }
            }
        }
        socket.on("message:new", listener)
        onDispose {
            socket.off("message:new", listener)
            socket.emit("conversation:leave", conversationId)
        }
    }

    // Autoscroll al final cuando cambian los mensajes.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun startVoiceRecording() {
        runCatching {
            val file = File.createTempFile("private-voice-", ".m4a", context.cacheDir)
            val media = android.media.MediaRecorder()
            media.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(64000)
            media.setAudioSamplingRate(24000)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            media.start()
            recordingFile = file
            recordingStartedAt = android.os.SystemClock.elapsedRealtime()
            recorder = media
            recording = true
        }.onFailure {
            runCatching { recorder?.release() }
            recorder = null
            recordingFile?.delete()
            recordingFile = null
            android.widget.Toast.makeText(context, "No se pudo iniciar la grabación", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoice() {
        val media = recorder
        val file = recordingFile
        recorder = null
        recordingFile = null
        recording = false
        if (media == null || file == null) return
        val duration = ((android.os.SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L).toInt().coerceAtLeast(1)
        runCatching { media.stop() }.onFailure {
            runCatching { media.release() }; file.delete()
            return
        }
        runCatching { media.release() }
        if (file.length() < 500L) {
            file.delete()
            return
        }
        sending = true
        scope.launch {
            try {
                val upload = MediaUploader.upload(
                    context,
                    PickedFile(android.net.Uri.fromFile(file), file.name, file.length(), "audio/mp4"),
                )
                val sent = Backend.api.sendMessage(
                    conversationId,
                    SendMessageRequest(type = "VOICE", attachmentKey = upload.key, durationSec = duration),
                )
                if (messages.none { it.id == sent.id }) messages.add(sent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "No se pudo enviar la nota de voz", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                file.delete()
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MapeColors.Bg)) {
        // Cabecera
        Row(
            Modifier.fillMaxWidth().background(MapeColors.Card).padding(top = topPadding).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).pressScale { onBack() }, contentAlignment = Alignment.Center) {
                Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.Text, modifier = Modifier.size(24.dp))
            }
            Text(title, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }

        // Mensajes
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { m ->
                val mine = m.senderId == meId
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        Modifier.widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (mine) MapeColors.Ink else MapeColors.Card)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (!mine) {
                            Text(
                                m.sender?.nickname ?: m.sender?.name ?: "—",
                                color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (m.type == "VOICE" && !m.attachmentKey.isNullOrBlank()) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(MapeColors.Red).clickable {
                                    val key = m.attachmentKey.orEmpty()
                                    val url = when {
                                        key.startsWith("http") -> key
                                        key.startsWith("/") -> com.syemape.radio.BuildConfig.API_BASE_URL.substringBefore("/api") + key
                                        else -> null
                                    }
                                    if (url != null) com.syemape.radio.data.RadioManager.playVoiceNote(url, m.id, onState = { playingVoiceId = it })
                                }, contentAlignment = Alignment.Center) {
                                    Icon(if (playingVoiceId == m.id) MapeIcons.Pause else MapeIcons.Play, "Reproducir nota", tint = MapeColors.White, modifier = Modifier.size(18.dp))
                                }
                                Text("Nota de voz · ${m.durationSec ?: 0}s", color = if (mine) MapeColors.White else MapeColors.Text, fontFamily = Outfit, fontSize = 14.sp)
                            } else {
                                Text(Fmt.preview(m).ifBlank { m.body ?: "" }, color = if (mine) MapeColors.White else MapeColors.Text, fontFamily = Outfit, fontSize = 15.sp)
                            }
                        }
                        Text(
                            Fmt.shortTime(m.createdAt),
                            color = if (mine) MapeColors.TextOnDark else MapeColors.TextFaint,
                            fontFamily = Outfit, fontSize = 10.sp,
                            modifier = Modifier.padding(top = 3.dp).align(Alignment.End),
                        )
                    }
                }
            }
        }

        // Barra de entrada
        Row(
            Modifier.fillMaxWidth().background(MapeColors.Card).padding(horizontal = 12.dp, vertical = 10.dp).padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(if (recording) MapeColors.Red else MapeColors.Ink)
                    .pressScale(enabled = !sending) { if (recording) stopAndSendVoice() else startVoiceRecording() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MapeIcons.Mic, if (recording) "Detener y enviar" else "Grabar nota de voz", tint = MapeColors.White, modifier = Modifier.size(29.dp))
            }
            Box(
                Modifier.weight(1f).height(48.dp).clip(CircleShape).background(MapeColors.Bg).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text(if (recording) "Grabando… toca el micrófono para enviar" else "Mensaje…", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 15.sp)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2000) },
                    textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = MapeColors.Text),
                    cursorBrush = SolidColor(MapeColors.Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MapeColors.Ink).pressScale(enabled = !sending && draft.isNotBlank()) {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !sending) {
                        draft = ""
                        sending = true
                        scope.launch {
                            runCatching { Backend.api.sendMessage(conversationId, SendMessageRequest(type = "TEXT", body = text)) }
                                .onSuccess { sent -> if (messages.none { it.id == sent.id }) messages.add(sent) }
                                .onFailure {
                                    if (draft.isEmpty()) draft = text
                                    android.widget.Toast.makeText(
                                        context,
                                        "No se pudo enviar el mensaje. Inténtalo de nuevo.",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            sending = false
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MapeIcons.Send, null, tint = MapeColors.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
