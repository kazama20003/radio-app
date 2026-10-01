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
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.launch

@Composable
fun ChatDetailScreen(conversationId: String, title: String, topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val meId = SessionManager.user?.id
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Message>() }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize().background(MapeColors.Bg)) {
        // Cabecera
        Row(
            Modifier.fillMaxWidth().background(MapeColors.White).padding(top = topPadding).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).pressScale { onBack() }, contentAlignment = Alignment.Center) {
                Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.Ink, modifier = Modifier.size(24.dp))
            }
            Text(title, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
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
                            .background(if (mine) MapeColors.Ink else MapeColors.White)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (!mine) {
                            Text(
                                m.sender?.nickname ?: m.sender?.name ?: "—",
                                color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            Fmt.preview(m).ifBlank { m.body ?: "" },
                            color = if (mine) MapeColors.White else MapeColors.Ink,
                            fontFamily = Outfit, fontSize = 15.sp,
                        )
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
            Modifier.fillMaxWidth().background(MapeColors.White).padding(horizontal = 12.dp, vertical = 10.dp).padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(CircleShape).background(MapeColors.Bg).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text("Mensaje…", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 15.sp)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = MapeColors.Ink),
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
                                .onSuccess { messages.add(it) }
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
