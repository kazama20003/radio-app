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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Conversation
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.RoundIconButton
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.rememberAsync
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

private val Color8A = Color(0xFF8A8A8A)

private data class ChatDisplay(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val unread: Int,
    val group: Boolean,
)

private fun Conversation.toDisplay(meId: String?): ChatDisplay {
    val group = type == "GROUP" || !title.isNullOrBlank()
    val other = members.firstOrNull { it.user?.id != meId }?.user
    val name = title?.takeIf { it.isNotBlank() }
        ?: other?.nickname?.takeIf { it.isNotBlank() }
        ?: other?.name?.takeIf { it.isNotBlank() }
        ?: "Conversación"
    return ChatDisplay(id, name, Fmt.preview(lastMessage), Fmt.shortTime(lastMessage?.createdAt ?: lastMessageAt), unread, group)
}

@Composable
fun ChatsScreen(
    topPadding: Dp,
    onOpenChat: (String, String) -> Unit = { _, _ -> },
    onOpenChannelChat: (String, String) -> Unit = { _, _ -> },
) {
    val meId = SessionManager.user?.id
    val result by rememberAsync { Backend.api.conversations() }
    val convos = result?.getOrNull().orEmpty().map { it.toDisplay(meId) }
    // Canales de radio: se muestran como chats aparte (diferenciados) arriba de todo.
    val channelsResult by rememberAsync { Backend.api.radioChannels() }
    val channels = channelsResult?.getOrNull().orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MapeColors.Bg),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topPadding + 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Chats", color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 30.sp)
                RoundIconButton(MapeIcons.Plus, bg = MapeColors.Ink, tint = MapeColors.White)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp).clip(CircleShape).background(MapeColors.White).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MapeIcons.Search, null, tint = MapeColors.TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Buscar chat", color = Color8A, fontFamily = Outfit, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (channels.isNotEmpty()) {
            item {
                Text(
                    "CANALES DE RADIO",
                    color = MapeColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, letterSpacing = 0.6.sp,
                )
            }
            items(channels) { ch ->
                val chName = ch.name ?: "Canal"
                val chTitle = listOfNotNull(ch.name, ch.description).joinToString(" · ").ifBlank { "Canal" }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MapeColors.White)
                        .pressScale { onOpenChannelChat(ch.id, chTitle) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MapeColors.Ink), contentAlignment = Alignment.Center) {
                        Icon(MapeIcons.Radio, null, tint = MapeColors.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chName, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text("Canal de radio · ${ch.memberCount} en el canal", color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(MapeIcons.ChevronRight, null, tint = MapeColors.TextFaint, modifier = Modifier.size(20.dp))
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "MENSAJES",
                    color = MapeColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, letterSpacing = 0.6.sp,
                )
            }
        }
        if (result == null) {
            item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MapeColors.Ink) } }
        } else if (convos.isEmpty()) {
            item {
                Text(
                    "No tienes chats todavía. Toca + para iniciar uno.",
                    color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                )
            }
        }
        items(convos) { chat ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MapeColors.White).pressScale { onOpenChat(chat.id, chat.name) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chat.group) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MapeColors.Red), contentAlignment = Alignment.Center) {
                        Icon(MapeIcons.Truck, null, tint = MapeColors.White, modifier = Modifier.size(22.dp))
                    }
                } else {
                    Avatar(initialsOf(chat.name), avatarColor(chat.name), size = 48.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.name, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(chat.preview, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(chat.time, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
                    if (chat.unread > 0) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(MapeColors.Red), contentAlignment = Alignment.Center) {
                            Text("${chat.unread}", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
