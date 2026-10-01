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

@Composable
fun ChannelChatScreen(channelId: String, title: String, topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val meId = SessionManager.user?.id
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<RadioTransmission>() }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(channelId) {
        runCatching { Backend.api.radioHistory(channelId) }.getOrNull()?.let { items.clear(); items.addAll(it) }
    }
    LaunchedEffect(items.size) { if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1) }

    DisposableEffect(channelId) {
        val socket = Realtime.socket("/radio")
        // Asegura estar en la sala del canal para recibir channel:post en vivo.
        if (!socket.connected()) socket.connect()
        socket.emit("channel:join", channelId)
        val listener = io.socket.emitter.Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            if (o.optString("channelId") != channelId) return@Listener
            val t = runCatching { Realtime.gson.fromJson(o.getJSONObject("transmission").toString(), RadioTransmission::class.java) }.getOrNull()
                ?: return@Listener
            scope.launch(Dispatchers.Main) { if (items.none { it.id == t.id }) items.add(t) }
        }
        socket.on("channel:post", listener)
        onDispose { socket.off("channel:post", listener) }
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
                        Text(t.preview(), color = if (mine) MapeColors.White else MapeColors.Ink, fontFamily = Outfit, fontSize = 15.sp)
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
