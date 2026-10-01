package com.syemape.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

enum class Tab(val label: String, val icon: ImageVector) {
    Mapa("Mapa", MapeIcons.Pin),
    Radio("Radio", MapeIcons.Radio),
    Chats("Chat", MapeIcons.Chat),
    Alertas("Alertas", MapeIcons.Bell),
    Perfil("Perfil", MapeIcons.User),
}

@Composable
fun BottomNav(active: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(MapeColors.Ink)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val isActive = tab == active
            val badge = if (tab == Tab.Chats) com.syemape.radio.data.AppBadges.unreadChats else 0
            val dot = tab == Tab.Alertas && com.syemape.radio.data.AppBadges.pendingAlerts > 0
            Box(
                modifier = (if (isActive) Modifier.weight(1f) else Modifier.width(54.dp))
                    .fillMaxHeight()
                    .pressScale { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (isActive) MapeColors.White else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (tab == Tab.Perfil) {
                        Avatar("AM", MapeColors.Red, size = if (isActive) 26.dp else 30.dp, border = 2.dp, borderColor = if (isActive) MapeColors.Ink else MapeColors.White)
                    } else {
                        Icon(
                            tab.icon,
                            null,
                            tint = if (isActive) MapeColors.Ink else MapeColors.White,
                            modifier = Modifier.size(if (isActive) 20.dp else 22.dp),
                        )
                    }
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Text(tab.label, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                if (!isActive && badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MapeColors.Red)
                            .border(2.dp, MapeColors.Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (badge > 99) "99" else "$badge", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
                if (!isActive && dot) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MapeColors.Red)
                            .border(2.dp, MapeColors.Ink, CircleShape),
                    )
                }
            }
        }
    }
}
