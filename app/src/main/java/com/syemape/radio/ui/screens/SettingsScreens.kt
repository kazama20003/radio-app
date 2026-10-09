package com.syemape.radio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
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
import com.syemape.radio.data.MediaUploader
import com.syemape.radio.data.UpdateUserRequest
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

@Composable
private fun SubScreen(title: String, topPadding: Dp, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MapeColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().background(MapeColors.Card).padding(top = topPadding).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).pressScale { onBack() }, contentAlignment = Alignment.Center) {
                Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.Text, modifier = Modifier.size(24.dp))
            }
            Text(title, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
        content()
    }
}

@Composable
private fun ToggleRow(label: String, sub: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.Card).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.Card).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
        Text(value.ifBlank { "—" }, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun AccountScreen(topPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var u by remember { mutableStateOf(SessionManager.user) }
    var changingPhoto by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    val choosePhoto = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            changingPhoto = true
            photoMessage = null
            try {
                val picked = MediaUploader.query(context, uri)
                u = MediaUploader.uploadMyProfilePhoto(context, picked)
                    .also(SessionManager::updateUser)
                photoMessage = "Foto de perfil actualizada"
            } catch (e: Exception) {
                photoMessage = e.message?.takeIf(String::isNotBlank) ?: "No se pudo cambiar la foto"
            } finally {
                changingPhoto = false
            }
        }
    }
    val role = when (u?.role) { "ADMIN" -> "Administrador"; "SUPERVISOR" -> "Supervisor"; else -> "Operador" }
    SubScreen("Mi cuenta", topPadding, onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MapeColors.Ink).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(68.dp).clip(CircleShape).background(MapeColors.Red).clickable(enabled = !changingPhoto) { choosePhoto.launch("image/*") }, contentAlignment = Alignment.Center) {
                        Avatar(initialsOf(u?.name ?: "?"), MapeColors.Red, size = 64.dp, border = 2.dp, borderColor = MapeColors.White)
                        if (!u?.photoUrl.isNullOrBlank()) AsyncImage(u?.photoUrl, contentDescription = "Foto de perfil", modifier = Modifier.size(64.dp).clip(CircleShape))
                        if (changingPhoto) CircularProgressIndicator(Modifier.size(22.dp), color = MapeColors.White, strokeWidth = 2.dp)
                    }
                    Column {
                        Text(u?.nickname ?: u?.name ?: "—", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text(role, color = MapeColors.TextOnDarkSoft, fontFamily = Outfit, fontSize = 13.sp)
                        Text(if (changingPhoto) "Subiendo foto…" else "Toca la foto para cambiarla", color = MapeColors.TextOnDarkSoft, fontFamily = Outfit, fontSize = 11.sp)
                    }
                }
            }
            photoMessage?.let { item { Text(it, color = MapeColors.Red, fontFamily = Outfit, fontSize = 12.sp) } }
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
fun AppSettingsScreen(topPadding: Dp, onDarkModeChanged: (Boolean) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<AppSetting?>(null) }
    LaunchedEffect(Unit) {
        val remote = runCatching { Backend.api.settings() }.getOrNull() ?: AppSetting()
        val savedRadioSound = com.syemape.radio.data.Prefs.radioSound
        if (savedRadioSound == null) com.syemape.radio.data.Prefs.radioSound = remote.radioSound
        s = remote.copy(
            darkMode = com.syemape.radio.data.Prefs.darkMode,
            radioSound = savedRadioSound ?: remote.radioSound,
        )
    }
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
                item { ToggleRow("Sonido de radio", "Chirrido de grillo al iniciar PTT", cur.radioSound) {
                    com.syemape.radio.data.Prefs.radioSound = it
                    patch("radioSound", it) { c -> c.copy(radioSound = it) }
                } }
                item { ToggleRow("Alertas críticas", "Avisos de alertas importantes", cur.criticalAlerts) { patch("criticalAlerts", it) { c -> c.copy(criticalAlerts = it) } } }
                item { ToggleRow("Modo oscuro", "Tema oscuro de la app", cur.darkMode) {
                    com.syemape.radio.data.Prefs.darkMode = it
                    onDarkModeChanged(it)
                    patch("darkMode", it) { c -> c.copy(darkMode = it) }
                } }
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<AuthUser>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var photoTarget by remember { mutableStateOf<AuthUser?>(null) }

    suspend fun refreshPersonnel() {
        loading = true
        error = null
        message = null
        try {
            val result = Backend.api.syncPersonal()
            users = Backend.api.users().filter { it.isActive }
            message = "${result.fetched} personas activas · ${result.created} nuevas · ${result.updated} actualizadas${if (result.deactivated > 0) " · ${result.deactivated} desactivadas" else ""}"
            if (result.errors.isNotEmpty()) error = "Algunos registros no se pudieron sincronizar (${result.errors.size})."
        } catch (e: Exception) {
            error = "No se pudo consultar Personal. Comprueba tu conexión e inténtalo de nuevo."
            users = runCatching { Backend.api.users().filter { it.isActive } }.getOrNull()
        } finally {
            loading = false
        }
    }

    val choosePhoto = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        val target = photoTarget
        if (uri != null && target != null) scope.launch {
            error = null
            try {
                val picked = MediaUploader.query(context, uri)
                val updated = MediaUploader.uploadProfilePhoto(context, target.id, picked)
                users = users?.map { if (it.id == updated.id) updated else it }
                message = "Foto de ${target.nickname?.takeIf { it.isNotBlank() } ?: target.name ?: "usuario"} actualizada."
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "No se pudo subir la foto."
            }
        }
    }

    LaunchedEffect(Unit) { refreshPersonnel() }
    val list = users
    SubScreen("Administrar usuarios", topPadding, onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Personal activo", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Fuente: Personal S&E MAPE", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 11.sp)
                    }
                    Text(
                        if (loading) "Actualizando…" else "Actualizar",
                        color = if (loading) MapeColors.TextFaint else MapeColors.Red,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !loading) { scope.launch { refreshPersonnel() } }.padding(10.dp),
                    )
                }
            }
            if (loading && list == null) { item { Text("Cargando personal desde la fuente oficial…", color = MapeColors.TextFaint, fontFamily = Outfit, modifier = Modifier.padding(8.dp)) } }
            message?.let { status -> item { Text(status, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp)) } }
            error?.let { text -> item { Text(text, color = MapeColors.Red, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) } }
            if (!loading && list == null) { item { Text(error ?: "No se pudo cargar el personal.", color = MapeColors.Red, fontFamily = Outfit, modifier = Modifier.padding(8.dp)) } }
            else {
                if (list != null) {
                    item { Text("${list.size} personas", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) }
                    items(list, key = { it.id }) { u ->
                        var roleMenu by remember(u.id) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.Card).padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(MapeColors.Ink).clickable {
                                photoTarget = u
                                choosePhoto.launch("image/*")
                            }, contentAlignment = Alignment.Center) {
                                if (!u.photoUrl.isNullOrBlank()) {
                                    AsyncImage(model = u.photoUrl, contentDescription = "Foto de ${u.name}", modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } else {
                                    Avatar(initialsOf(u.name ?: "?"), avatarColor(u.id), size = 48.dp)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(u.nickname?.takeIf { it.isNotBlank() } ?: u.name ?: "—", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (!u.nickname.isNullOrBlank() && !u.name.isNullOrBlank()) Text(u.name, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp)
                                Text("DNI ${u.operatorCode ?: u.dni ?: "—"} · ${u.positionTitle ?: "Personal"}", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 11.sp)
                                if (!u.phone.isNullOrBlank()) Text(u.phone, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box {
                                        Text(
                                            "Rol: ${when (u.role) { "ADMIN" -> "Administrador"; "SUPERVISOR" -> "Supervisor"; else -> "Operador" }}  ▾",
                                            color = MapeColors.Red,
                                            fontFamily = Outfit,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable { roleMenu = true }.padding(vertical = 5.dp, horizontal = 7.dp),
                                        )
                                        DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                                            listOf("OPERATOR" to "Operador", "SUPERVISOR" to "Supervisor", "ADMIN" to "Administrador").forEach { (role, label) ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                                    roleMenu = false
                                                    if (u.role != role) scope.launch {
                                                        try {
                                                            val updated = Backend.api.updateUser(u.id, UpdateUserRequest(role = role))
                                                            users = users?.map { if (it.id == updated.id) updated else it }
                                                            message = "Rol actualizado para ${u.nickname?.takeIf { it.isNotBlank() } ?: u.name ?: "usuario"}."
                                                            error = null
                                                        } catch (_: Exception) {
                                                            error = "No se pudo guardar el rol. Comprueba tus permisos y vuelve a intentar."
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                    }
                                    Text("Cambiar foto", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 11.sp, modifier = Modifier.clickable {
                                        photoTarget = u
                                        choosePhoto.launch("image/*")
                                    }.padding(vertical = 5.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
