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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.Alert
import com.syemape.radio.data.Backend
import com.syemape.radio.data.Fmt
import com.syemape.radio.ui.Dot
import com.syemape.radio.ui.FilterRow
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.launch

private val filterTypes = listOf(null, "EXCESO_VELOCIDAD", "SALIDA_GEOCERCA")

private fun Alert.subtitle(): String {
    val who = operator?.name ?: "Unidad"
    val code = unit?.code ?: ""
    val base = listOf(who, code).filter { it.isNotBlank() }.joinToString(" · ")
    return if (!description.isNullOrBlank()) "$base — $description" else base
}

/** Icono representativo según el tipo de alerta. */
private fun Alert.icon(): ImageVector = when (type) {
    "EXCESO_VELOCIDAD" -> MapeIcons.Gauge
    "SALIDA_GEOCERCA" -> MapeIcons.Pin
    else -> MapeIcons.AlertTriangle
}

@Composable
fun AlertsScreen(topPadding: Dp, onGoMap: () -> Unit = {}, onGoRadio: () -> Unit = {}) {
    var filter by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val alerts = remember { androidx.compose.runtime.mutableStateListOf<com.syemape.radio.data.Alert>() }
    var metrics by remember { androidx.compose.runtime.mutableStateOf<com.syemape.radio.data.AlertMetrics?>(null) }
    var loading by remember { androidx.compose.runtime.mutableStateOf(true) }

    suspend fun reload() {
        runCatching { Backend.api.alerts() }.getOrNull()?.let { alerts.clear(); alerts.addAll(it) }
        metrics = runCatching { Backend.api.alertMetrics() }.getOrNull()
        com.syemape.radio.data.AppBadges.refresh()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        reload()
        loading = false
    }
    // Alertas en vivo por Socket.IO (/alerts).
    androidx.compose.runtime.DisposableEffect(Unit) {
        val socket = com.syemape.radio.data.Realtime.socket("/alerts")
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val onNew = io.socket.emitter.Emitter.Listener { args ->
            val a = com.syemape.radio.data.Realtime.parse<com.syemape.radio.data.Alert>(args) ?: return@Listener
            scope.launch { if (alerts.none { it.id == a.id }) alerts.add(0, a) }
        }
        val onUpd = io.socket.emitter.Emitter.Listener { args ->
            val a = com.syemape.radio.data.Realtime.parse<com.syemape.radio.data.Alert>(args) ?: return@Listener
            scope.launch { val i = alerts.indexOfFirst { it.id == a.id }; if (i >= 0) alerts[i] = a }
        }
        socket.on("alert:new", onNew)
        socket.on("alert:updated", onUpd)
        onDispose { socket.off("alert:new", onNew); socket.off("alert:updated", onUpd) }
    }

    val type = filterTypes[filter]
    val list = alerts.filter { type == null || it.type == type }

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
                Text("Alertas", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 30.sp)
                Row(
                    Modifier.height(40.dp).clip(CircleShape).background(MapeColors.Card)
                        .pressScale { scope.launch { runCatching { Backend.api.markAlertsRead() }; reload() } }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(MapeIcons.DoubleCheck, null, tint = MapeColors.Text, modifier = Modifier.size(15.dp))
                    Text("Marcar leídas", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("${metrics?.criticas ?: 0}", "Críticas", MapeColors.Red, MapeColors.White, Color(0xE6FFFFFF), Modifier.weight(1f))
                StatCard("${metrics?.pendientes ?: 0}", "Pendientes", MapeColors.Ink, MapeColors.White, MapeColors.TextOnDark, Modifier.weight(1f))
                StatCard("${metrics?.hoy ?: 0}", "Hoy", MapeColors.White, MapeColors.Ink, MapeColors.TextMuted, Modifier.weight(1f))
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            FilterRow(listOf("Todas", "Velocidad", "Geocerca"), filter) { filter = it }
        }
        if (list.isNotEmpty()) {
            item {
                Text(
                    "${list.size} ${if (list.size == 1) "alerta" else "alertas"}",
                    color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
                )
            }
            items(list) { a -> AlertCard(a, onGoMap, onGoRadio) }
        } else {
            item { EmptyState(loading) }
        }
    }
}

@Composable
private fun AlertCard(a: Alert, onGoMap: () -> Unit, onGoRadio: () -> Unit) {
    val critical = a.severity == "CRITICA"
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp)).background(MapeColors.Card)
            .then(if (critical) Modifier.border(1.5.dp, MapeColors.Red, RoundedCornerShape(22.dp)) else Modifier)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Badge de tipo: círculo con icono. Rojo si es crítica, negro si no.
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (critical) MapeColors.Red else MapeColors.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(a.icon(), null, tint = MapeColors.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (critical) Dot(MapeColors.Red, 7.dp)
                    Text(a.title.uppercase(), color = if (critical) MapeColors.RedDark else Color(0xFF4A4A4A), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(Fmt.hace(a.createdAt), color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
            }
            Text(a.subtitle(), color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (!a.locationLabel.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(MapeIcons.Pin, null, tint = MapeColors.TextMuted, modifier = Modifier.size(13.dp))
                    Text(a.locationLabel, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 13.sp)
                }
            }
            if (critical) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Row(
                        Modifier.height(38.dp).clip(CircleShape).background(MapeColors.Red).pressScale { onGoRadio() }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(MapeIcons.Mic, null, tint = MapeColors.White, modifier = Modifier.size(16.dp))
                        Text("Radio", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(
                        Modifier.height(38.dp).clip(CircleShape).background(MapeColors.Bg).pressScale { onGoMap() }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("Ver en mapa", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
        }
    }
}

/** Estado vacío (sin alertas) o de carga, centrado en el espacio disponible. */
@Composable
private fun EmptyState(loading: Boolean) {
    Column(
        Modifier.fillMaxWidth().height(360.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MapeColors.Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (loading) MapeIcons.Clock else MapeIcons.Bell,
                null,
                tint = if (loading) MapeColors.TextMuted else MapeColors.Ink,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (loading) "Cargando alertas…" else "Todo en orden",
            color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
        )
        if (!loading) {
            Spacer(Modifier.height(6.dp))
            Text(
                "No hay alertas por ahora.\nTe avisaremos cuando ocurra algo.",
                color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, bg: Color, numColor: Color, labelColor: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, color = numColor, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
        Text(label, color = labelColor, fontFamily = Outfit, fontSize = 12.sp)
    }
}
