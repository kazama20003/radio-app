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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.LiveDot
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.RoundIconButton
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

private data class MenuRow(
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val route: String,
    val danger: Boolean = false,
)

private val menuRows = listOf(
    MenuRow(MapeIcons.UserPlus, "Administrar usuarios", "Accesos, roles, apelativos y fotos", "admin-users"),
    MenuRow(MapeIcons.User, "Mi cuenta", "Datos personales y contraseña", "account"),
    MenuRow(MapeIcons.Bell, "Notificaciones", "Alertas críticas, chat y radio", "notifications"),
    MenuRow(MapeIcons.Sliders, "Ajustes de la app", "Mapa, radio, tema y permisos", "settings"),
    MenuRow(MapeIcons.Clock, "Ayuda y soporte", "Chat con soporte de Mape", "help"),
    MenuRow(MapeIcons.ArrowRight, "Cerrar sesión", "Salir de esta cuenta", "logout", danger = true),
)

@Composable
fun ProfileScreen(topPadding: Dp, onLogout: () -> Unit, onOpen: (String) -> Unit = {}) {
    val user = com.syemape.radio.data.SessionManager.user
    val name = user?.nickname?.takeIf { it.isNotBlank() } ?: user?.name?.takeIf { it.isNotBlank() } ?: "—"
    val role = user?.positionTitle?.takeIf { it.isNotBlank() } ?: when (user?.role) {
        "ADMIN" -> "Administrador"
        "SUPERVISOR" -> "Supervisora de operaciones"
        else -> "Operador"
    }
    val shiftLabel = when (user?.shift) {
        "TARDE" -> "Turno tarde"
        "NOCHE" -> "Turno noche"
        else -> "Turno mañana"
    }
    val isAdmin = user?.role == "ADMIN" || user?.role == "SUPERVISOR"
    val visibleRows = if (isAdmin) menuRows else menuRows.filter { it.title != "Administrar usuarios" }
    val summary by com.syemape.radio.ui.rememberAsync { com.syemape.radio.data.Backend.api.unitsSummary() }
    val metrics by com.syemape.radio.ui.rememberAsync { com.syemape.radio.data.Backend.api.alertMetrics() }
    val s = summary?.getOrNull()
    val m = metrics?.getOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MapeColors.Bg),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topPadding + 20.dp, bottom = 110.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Perfil", color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 30.sp)
                RoundIconButton(MapeIcons.User, bg = MapeColors.Ink, tint = MapeColors.White)
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MapeColors.Ink)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Avatar(com.syemape.radio.ui.initialsOf(name), MapeColors.Red, size = 68.dp, border = 3.dp, borderColor = MapeColors.White)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text(role, color = MapeColors.TextOnDarkSoft, fontFamily = Outfit, fontSize = 13.sp)
                    Row(
                        Modifier.padding(top = 2.dp).height(24.dp).clip(CircleShape).background(MapeColors.Red).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LiveDot(size = 6.dp, color = MapeColors.White)
                        Text(shiftLabel, color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStat("${s?.total ?: 0}", "Unidades", MapeColors.Ink, Modifier.weight(1f))
                ProfileStat("${s?.enRuta ?: 0}", "En ruta", MapeColors.Ink, Modifier.weight(1f))
                ProfileStat("${m?.pendientes ?: 0}", "Alertas", MapeColors.RedDark, Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
        items(visibleRows) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MapeColors.White)
                    .pressScale { if (r.danger) onLogout() else onOpen(r.route) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(if (r.danger) MapeColors.RedSoftBg else MapeColors.Bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(r.icon, null, tint = if (r.danger) MapeColors.RedDark else MapeColors.Ink, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.title, color = if (r.danger) MapeColors.RedDark else MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(r.sub, color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
                }
                Icon(MapeIcons.ChevronRight, null, tint = Color(0xFF9A9A9A), modifier = Modifier.size(18.dp))
            }
        }
        item {
            Text(
                "Mape v1.0 · Última sincronización hace 3 s",
                color = Color(0xFF9A9A9A),
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, numColor: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(MapeColors.White).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = numColor, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Text(label, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp)
    }
}
