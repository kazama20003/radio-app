package com.syemape.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0A0A0A)
private val Canvas = Color(0xFFF4F4F4)
private val Red = Color(0xFFE5322D)
private val RedSoft = Color(0xFFFDE3E2)
private val Muted = Color(0xFF6A6A6A)
private val Line = Color(0xFFE4E4E4)

private enum class AppTab(val label: String, val icon: ImageVector) {
    Map("Mapa", Icons.Outlined.LocationOn),
    Radio("Radio", Icons.Outlined.GraphicEq),
    Chats("Chat", Icons.Outlined.ChatBubbleOutline),
    Alerts("Alertas", Icons.Outlined.NotificationsNone),
    Profile("Perfil", Icons.Outlined.PersonOutline),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RadioMapeApp()
            }
        }
    }
}

@Composable
private fun RadioMapeApp() {
    Column(
        modifier = Modifier.fillMaxSize().background(Canvas),
    ) {
        var selectedTab by remember { mutableStateOf(AppTab.Map) }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                AppTab.Map -> MapScreen()
                AppTab.Radio -> RadioScreen()
                AppTab.Chats -> ChatsScreen()
                AppTab.Alerts -> AlertsScreen()
                AppTab.Profile -> ProfileScreen()
            }
            BottomNav(selectedTab, { selectedTab = it }, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MapScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.mape_logo),
                    contentDescription = "Mape",
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White),
                )
                Spacer(Modifier.width(10.dp))
                SearchPill()
                Spacer(Modifier.width(10.dp))
                Avatar("AM", Red)
            }
        }
        item {
            Text("Buenos dias, Andrea", color = Muted, fontSize = 15.sp)
            Text("Operadores en ruta", color = Ink, fontWeight = FontWeight.Medium, fontSize = 30.sp)
        }
        item { FilterRow(listOf("Todos", "En ruta", "Detenidos"), 0) }
        item { MapPreview() }
        item { Text("Personas activas", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        items(listOf("Carlos Mendoza" to "Unidad B-14 · En ruta", "Rosa Huaman" to "Centro · Disponible")) { person ->
            PersonCard(person.first, person.second)
        }
    }
}

@Composable
private fun MapPreview() {
    Box(
        modifier = Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFE7ECE9)),
    ) {
        Text("MAPA EN VIVO", modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Ink).padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(66.dp).clip(CircleShape).background(RedSoft).border(3.dp, Red, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocationOn, null, tint = Red, modifier = Modifier.size(35.dp))
            }
            Text("B-14", modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ink).padding(horizontal = 9.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp)
        }
        RoundIcon(Icons.Outlined.MyLocation, Color.White, Ink, Modifier.align(Alignment.BottomEnd).padding(14.dp))
    }
}

@Composable
private fun RadioScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)).background(Ink).padding(horizontal = 20.dp, vertical = 26.dp)) {
                Text("Canal activo", color = Color(0xFFC9C9C9), fontSize = 13.sp)
                Text("Operaciones · Canal 1", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                FilterRow(listOf("Canal 1", "Norte", "Taller"), 0, dark = true)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar("CM", Color(0xFF347B5D)); Spacer(Modifier.width(6.dp)); Avatar("RH", Color(0xFFB86B58)); Spacer(Modifier.width(10.dp))
                    Text("8 conectados", color = Color(0xFFC9C9C9), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f)); Dot(Red); Spacer(Modifier.width(6.dp)); Text("En vivo", color = Color.White, fontSize = 13.sp)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Red); Spacer(Modifier.width(10.dp)); Column { Text("ESCUCHANDO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Canal disponible", color = Ink, fontSize = 16.sp) }
                    Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.GraphicEq, null, tint = Red)
                }
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(218.dp).clip(CircleShape).background(RedSoft), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(182.dp).clip(CircleShape).background(Color(0xFFF9BBB8)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(148.dp).clip(CircleShape).background(Red).clickable { }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(38.dp)); Text("HABLAR", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                FilterRow(listOf("Altavoz", "Normal"), 0)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ActionButton(Icons.Outlined.History, "Ultimo", Modifier.weight(1f)); ActionButton(Icons.Outlined.ChatBubbleOutline, "Chat", Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChatsScreen() {
    val chats = listOf("Operaciones" to "Carlos: Ya estoy en la ruta", "Rosa Huaman" to "Ubicacion compartida", "Unidad B-14" to "Todo en orden, central")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 108.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Chats", Icons.Outlined.Add) }
        item { SearchPill(full = true) }
        items(chats) { chat ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(chat.first.take(2).uppercase(), Red); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(chat.first, color = Ink, fontWeight = FontWeight.SemiBold); Text(chat.second, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Column(horizontalAlignment = Alignment.End) { Text("10:42", color = Muted, fontSize = 11.sp); Spacer(Modifier.height(7.dp)); Text("2", modifier = Modifier.clip(CircleShape).background(Red).padding(horizontal = 7.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun AlertsScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 108.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Header("Alertas", Icons.Outlined.NotificationsNone, "Marcar leidas") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { Metric("2", "Criticas", Red, Color.White, Modifier.weight(1f)); Metric("5", "Pendientes", Ink, Color.White, Modifier.weight(1f)); Metric("12", "Hoy", Color.White, Ink, Modifier.weight(1f)) } }
        item { FilterRow(listOf("Todas", "Velocidad", "Geocerca"), 0) }
        items(listOf("Exceso de velocidad" to "Unidad B-14 · hace 4 min", "Salida de geocerca" to "Rosa Huaman · hace 12 min")) { alert ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, if (alert.first.startsWith("Exceso")) Red else Line, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.WarningAmber, null, tint = Red); Spacer(Modifier.width(9.dp)); Column { Text(alert.first, color = Ink, fontWeight = FontWeight.SemiBold); Text(alert.second, color = Muted, fontSize = 13.sp) } }
                Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { SmallButton("Radio", Red, Color.White); SmallButton("Ver en mapa", Canvas, Ink) }
            }
        }
    }
}

@Composable
private fun ProfileScreen() {
    val rows = listOf("Mi cuenta", "Notificaciones", "Ajustes de radio", "Ayuda y soporte")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 108.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Perfil", Icons.Outlined.Edit) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Ink).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Avatar("AM", Red, 56.dp); Spacer(Modifier.width(14.dp)); Column { Text("Andrea Morales", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold); Text("Supervisora de operaciones", color = Color(0xFFC9C9C9), fontSize = 13.sp); Text("EN TURNO", modifier = Modifier.padding(top = 7.dp).clip(RoundedCornerShape(10.dp)).background(Red).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.height(18.dp)); Row { ProfileMetric("14", "Rutas"); ProfileMetric("03:28", "Radio"); ProfileMetric("92%", "Turno") }
            }
        }
        items(rows) { row -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RoundIcon(Icons.Outlined.PersonOutline, Canvas, Ink); Spacer(Modifier.width(12.dp)); Text(row, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Medium); Icon(Icons.Outlined.ChevronRight, null, tint = Muted) } }
        item { Text("Cerrar sesion", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RedSoft).clickable { }.padding(17.dp), color = Color(0xFFB8241F), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable private fun BottomNav(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(68.dp).clip(RoundedCornerShape(34.dp)).background(Ink).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTab.entries.forEach { tab -> val active = tab == selected; Row(Modifier.weight(if (active) 1.45f else 0.78f).fillMaxSize().clip(RoundedCornerShape(28.dp)).background(if (active) Color.White else Color.Transparent).clickable { onSelect(tab) }.padding(horizontal = if (active) 10.dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(tab.icon, null, tint = if (active) Ink else Color.White, modifier = Modifier.size(22.dp)); if (active) { Spacer(Modifier.width(6.dp)); Text(tab.label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } } }
    }
}

@Composable private fun Header(title: String, icon: ImageVector, action: String? = null) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); if (action != null) Text(action, color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) else RoundIcon(icon, Ink, Color.White) }
@Composable private fun SearchPill(full: Boolean = false) = Row(Modifier.then(if (full) Modifier.fillMaxWidth() else Modifier.width(180.dp)).height(48.dp).clip(CircleShape).background(Color.White).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Search, null, tint = Muted); Spacer(Modifier.width(8.dp)); Text(if (full) "Buscar conversaciones" else "Buscar", color = Muted, fontSize = 14.sp) }
@Composable private fun RoundIcon(icon: ImageVector, background: Color, tint: Color, modifier: Modifier = Modifier) = Box(modifier.size(46.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
@Composable private fun Avatar(initials: String, color: Color, size: androidx.compose.ui.unit.Dp = 42.dp) = Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (size > 45.dp) 16.sp else 12.sp) }
@Composable private fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))
@Composable private fun FilterRow(items: List<String>, active: Int, dark: Boolean = false) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items.forEachIndexed { index, label -> Text(label, modifier = Modifier.clip(CircleShape).background(if (index == active) if (dark) Red else Ink else if (dark) Color(0xFF303030) else Color.White).padding(horizontal = 14.dp, vertical = 9.dp), color = if (index == active || dark) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) } }
@Composable private fun PersonCard(name: String, subtitle: String) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(name.take(2).uppercase(), Color(0xFF3A715A)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 13.sp) }; Dot(Color(0xFF2E9E5B)) }
@Composable private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier) = Row(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { }.padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Ink, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
@Composable private fun Metric(value: String, label: String, background: Color, color: Color, modifier: Modifier = Modifier) = Column(modifier.clip(RoundedCornerShape(18.dp)).background(background).padding(13.dp)) { Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(label, color = if (background == Color.White) Muted else Color.White, fontSize = 11.sp) }
@Composable private fun SmallButton(label: String, background: Color, color: Color) = Text(label, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 12.dp, vertical = 8.dp), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
@Composable private fun ProfileMetric(value: String, label: String) = Column(Modifier.widthIn(min = 76.dp)) { Text(value, color = Color.White, fontWeight = FontWeight.Bold); Text(label, color = Color(0xFFC9C9C9), fontSize = 11.sp) }
