package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import coil.compose.AsyncImage
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Conversation
import com.syemape.radio.data.CreateConversationRequest
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.SessionManager
import com.syemape.radio.data.AuthUser
import com.syemape.radio.data.LivePerson
import com.syemape.radio.data.MiniUser
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.rememberAsync
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.launch

private data class ChatDisplay(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val unread: Int,
    val group: Boolean,
    val activeCount: Int,
)

private data class ActiveChatPerson(
    val id: String,
    val name: String,
    val photoUrl: String?,
)

private fun Conversation.toDisplay(meId: String?, radioOnlineIds: Set<String>): ChatDisplay {
    val group = type == "GROUP" || !title.isNullOrBlank()
    val other = members.firstOrNull { it.user?.id != meId }?.user
    val activeCount = members.mapNotNull { it.user }
        .filter { it.id != meId && (it.isOnline || it.id in radioOnlineIds) }
        .distinctBy { it.id }
        .size
    val name = title?.takeIf { it.isNotBlank() }
        ?: other?.nickname?.takeIf { it.isNotBlank() }
        ?: other?.name?.takeIf { it.isNotBlank() }
        ?: "Conversación"
    return ChatDisplay(id, name, Fmt.preview(lastMessage), Fmt.shortTime(lastMessage?.createdAt ?: lastMessageAt), unread, group, activeCount)
}

@Composable
fun ChatsScreen(
    topPadding: Dp,
    onOpenChat: (String, String) -> Unit = { _, _ -> },
    onOpenChannelChat: (String, String) -> Unit = { _, _ -> },
) {
    val meId = SessionManager.user?.id
    val result by rememberAsync { Backend.api.conversations() }
    val personnelResult by rememberAsync { Backend.api.users() }
    val radioOnlineIds = com.syemape.radio.data.RadioManager.connectedUsers.mapTo(mutableSetOf()) { it.id }
    val convos = result?.getOrNull().orEmpty().map { it.toDisplay(meId, radioOnlineIds) }
    val personnel = personnelResult?.getOrNull().orEmpty().filter { it.isActive }.associateBy { it.id }
    val tracked = com.syemape.radio.data.TrackingManager.people.values.associateBy { it.id }
    val radioUsers = com.syemape.radio.data.RadioManager.connectedUsers.associateBy { it.id }
    val activeIds = buildSet {
        personnel.values.filter { it.isOnline }.forEach { add(it.id) }
        tracked.keys.forEach(::add)
        radioUsers.keys.forEach(::add)
    }.filter { it.isNotBlank() && it != meId }
    val activePeople = activeIds.mapNotNull { id ->
        val staff: AuthUser? = personnel[id]
        val person: LivePerson? = tracked[id]
        val radioUser: MiniUser? = radioUsers[id]
        val name = staff?.nickname?.takeIf { it.isNotBlank() }
            ?: staff?.name?.takeIf { it.isNotBlank() }
            ?: person?.nickname?.takeIf { it.isNotBlank() }
            ?: person?.name?.takeIf { it.isNotBlank() }
            ?: radioUser?.nickname?.takeIf { it.isNotBlank() }
            ?: radioUser?.name?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        ActiveChatPerson(id, name, staff?.photoUrl ?: person?.photoUrl ?: radioUser?.photoUrl)
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                Text("Chats", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 30.sp)
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        if (activePeople.isNotEmpty()) {
            item {
                Text("ACTIVOS", color = MapeColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.6.sp)
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(activePeople, key = { "active-${it.id}" }) { person ->
                        Column(
                            Modifier.width(62.dp).pressScale {
                                scope.launch {
                                    val existing = result?.getOrNull()?.firstOrNull { conversation ->
                                        conversation.type == "DIRECT" && conversation.members.any { it.user?.id == person.id }
                                    }
                                    if (existing != null) {
                                        onOpenChat(existing.id, person.name)
                                    } else {
                                        runCatching {
                                            Backend.api.createConversation(CreateConversationRequest(type = "DIRECT", memberIds = listOf(person.id)))
                                        }.onSuccess { onOpenChat(it.id, person.name) }
                                            .onFailure { android.widget.Toast.makeText(context, "No se pudo abrir el chat con ${person.name}", android.widget.Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                                if (!person.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = person.photoUrl,
                                        contentDescription = "Foto de ${person.name}",
                                        modifier = Modifier.size(50.dp).clip(CircleShape),
                                    )
                                } else {
                                    Avatar(initialsOf(person.name), avatarColor(person.id), size = 50.dp)
                                }
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape)
                                        .background(Color(0xFF20A957)).border(2.dp, MapeColors.Bg, CircleShape),
                                )
                            }
                            Text(person.name, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MapeColors.Card)
                    .border(1.dp, MapeColors.BorderLight, RoundedCornerShape(22.dp))
                        .pressScale { onOpenChannelChat(ch.id, chTitle) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(MapeColors.Ink), contentAlignment = Alignment.Center) {
                            Icon(MapeIcons.Radio, null, tint = MapeColors.White, modifier = Modifier.size(24.dp))
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape)
                                .background(Color(0xFF20A957)).border(2.dp, MapeColors.Card, CircleShape),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chName, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MapeColors.Text) } }
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MapeColors.Card)
                    .border(1.dp, MapeColors.BorderLight, RoundedCornerShape(22.dp))
                    .pressScale { onOpenChat(chat.id, chat.name) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (chat.group) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(MapeColors.Red), contentAlignment = Alignment.Center) {
                            Icon(MapeIcons.Truck, null, tint = MapeColors.White, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Avatar(initialsOf(chat.name), avatarColor(chat.name), size = 48.dp)
                    }
                    if (chat.activeCount > 0) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape)
                                .background(Color(0xFF20A957)).border(2.dp, MapeColors.Card, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.name, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(chat.preview, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(chat.time, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
                    if (chat.activeCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF20A957)))
                            Text(
                                if (chat.group) "${chat.activeCount} activos" else "Activo ahora",
                                color = Color(0xFF168443), fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                            )
                        }
                    }
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
