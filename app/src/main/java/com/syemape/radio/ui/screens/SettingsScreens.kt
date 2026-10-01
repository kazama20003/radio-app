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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.AppSetting
import com.syemape.radio.data.AuthUser
import com.syemape.radio.data.Backend
import com.syemape.radio.data.NotificationPref
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.launch

@Composable
private fun SubScreen(title: String, topPadding: Dp, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MapeColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().background(MapeColors.White).padding(top = topPadding).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).pressScale { onBack() }, contentAlignment = Alignment.Center) {
                Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.Ink, modifier = Modifier.size(24.dp))
            }
            Text(title, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
        content()
    }
}

@Composable
private fun ToggleRow(label: String, sub: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.White).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (sub.isNotBlank()) Text(sub, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = MapeColors.White, checkedTrackColor = MapeColors.Red, uncheckedTrackColor = MapeColors.Border),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.White).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
        Text(value.ifBlank { "—" }, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun AccountScreen(topPadding: Dp, onBack: () -> Unit) {
    val u = SessionManager.user
    val role = when (u?.role) { "ADMIN" -> "Administrador"; "SUPERVISOR" -> "Supervisor"; else -> "Operador" }
    SubScreen("Mi cuenta", topPadding, onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MapeColors.Ink).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(initialsOf(u?.name ?: "?"), MapeColors.Red, size = 60.dp, border = 3.dp, borderColor = MapeColors.White)
                    Column {
                        Text(u?.nickname ?: u?.name ?: "—", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text(role, color = MapeColors.TextOnDarkSoft, fontFamily = Outfit, fontSize = 13.sp)
                    }
                }
            }
            item { InfoRow("Nombre", u?.name ?: "") }
            item { InfoRow("Apelativo", u?.nickname ?: "") }
            item { InfoRow("Correo", u?.email ?: "") }
            item { InfoRow("DNI / código", u?.dni ?: "") }
            item { InfoRow("Puesto", u?.positionTitle ?: "") }
            item { InfoRow("Turno", when (u?.shift) { "TARDE" -> "Tarde"; "NOCHE" -> "Noche"; else -> "Mañana" }) }
        }
    }
}

@Composable
fun AppSettingsScreen(topPadding: Dp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<AppSetting?>(null) }
    LaunchedEffect(Unit) { s = runCatching { Backend.api.settings() }.getOrNull() ?: AppSetting() }
    fun patch(field: String, value: Boolean, apply: (AppSetting) -> AppSetting) {
        s = s?.let(apply)
        scope.launch { runCatching { Backend.api.updateSettings(mapOf(field to value)) } }
    }
    val cur = s
    SubScreen("Ajustes de la app", topPadding, onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cur == null) { item { Text("Cargando…", color = MapeColors.TextFaint, fontFamily = Outfit, modifier = Modifier.padding(8.dp)) } }
            else {
                item { ToggleRow("Ubicación en vivo", "Comparte tu posición en el mapa", cur.liveLocation) { patch("liveLocation", it) { c -> c.copy(liveLocation = it) } } }
                item { ToggleRow("Mapa de tránsito", "Muestra el tráfico en el mapa", cur.transitMap) { patch("transitMap", it) { c -> c.copy(transitMap = it) } } }
                item { ToggleRow("Sonido de radio", "Beeps al transmitir/recibir", cur.radioSound) { patch("radioSound", it) { c -> c.copy(radioSound = it) } } }
                item { ToggleRow("Alertas críticas", "Avisos de alertas importantes", cur.criticalAlerts) { patch("criticalAlerts", it) { c -> c.copy(criticalAlerts = it) } } }
                item { ToggleRow("Modo oscuro", "Tema oscuro de la app", cur.darkMode) { patch("darkMode", it) { c -> c.copy(darkMode = it) } } }
            }
        }
    }
}

@Composable
fun NotificationsScreen(topPadding: Dp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf<NotificationPref?>(null) }
    LaunchedEffect(Unit) { p = runCatching { Backend.api.notifPrefs() }.getOrNull() ?: NotificationPref() }
    fun patch(field: String, value: Boolean, apply: (NotificationPref) -> NotificationPref) {
        p = p?.let(apply)
        scope.launch { runCatching { Backend.api.updateNotifPrefs(mapOf(field to value)) } }
    }
    val cur = p
    SubScreen("Notificaciones", topPadding, onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cur == null) { item { Text("Cargando…", color = MapeColors.TextFaint, fontFamily = Outfit, modifier = Modifier.padding(8.dp)) } }
            else {
                item { ToggleRow("Alertas críticas", "", cur.criticalAlerts) { patch("criticalAlerts", it) { c -> c.copy(criticalAlerts = it) } } }
                item { ToggleRow("Mensajes de chat", "", cur.chatMessages) { patch("chatMessages", it) { c -> c.copy(chatMessages = it) } } }
                item { ToggleRow("Transmisiones de radio", "", cur.radioBroadcasts) { patch("radioBroadcasts", it) { c -> c.copy(radioBroadcasts = it) } } }
                item { ToggleRow("Estado de unidades", "", cur.unitStatus) { patch("unitStatus", it) { c -> c.copy(unitStatus = it) } } }
                item { ToggleRow("Resumen diario", "", cur.dailyDigest) { patch("dailyDigest", it) { c -> c.copy(dailyDigest = it) } } }
                item { ToggleRow("Sonido y vibración", "", cur.soundVibration) { patch("soundVibration", it) { c -> c.copy(soundVibration = it) } } }
                item { ToggleRow("No molestar", "", cur.doNotDisturb) { patch("doNotDisturb", it) { c -> c.copy(doNotDisturb = it) } } }
            }
        }
    }
}

@Composable
fun AdminUsersScreen(topPadding: Dp, onBack: () -> Unit) {
    var users by remember { mutableStateOf<List<AuthUser>?>(null) }
    LaunchedEffect(Unit) { users = runCatching { Backend.api.users() }.getOrNull().orEmpty() }
    val list = users
    SubScreen("Administrar usuarios", topPadding, onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list == null) { item { Text("Cargando…", color = MapeColors.TextFaint, fontFamily = Outfit, modifier = Modifier.padding(8.dp)) } }
            else {
                item { Text("${list.size} usuarios", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) }
                items(list) { u ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.White).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(initialsOf(u.name ?: "?"), avatarColor(u.id), size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(u.nickname?.takeIf { it.isNotBlank() } ?: u.name ?: "—", color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                listOfNotNull(
                                    when (u.role) { "ADMIN" -> "Administrador"; "SUPERVISOR" -> "Supervisor"; else -> "Operador" },
                                    u.dni?.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
