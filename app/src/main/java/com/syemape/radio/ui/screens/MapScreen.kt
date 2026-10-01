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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.Backend
import com.syemape.radio.data.LivePerson
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.FilterRow
import com.syemape.radio.ui.LiveDot
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.RoundIconButton
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.rememberAsync
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

@Composable
fun MapScreen(topPadding: Dp) {
    var filter by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val user = SessionManager.user
    val firstName = (user?.nickname ?: user?.name)?.trim()?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "operador"

    val summaryRes by rememberAsync { Backend.api.unitsSummary() }
    val metricsRes by rememberAsync { Backend.api.alertMetrics() }
    val summary = summaryRes?.getOrNull()
    val pending = metricsRes?.getOrNull()?.pendientes ?: 0
    // Estado en vivo (socket + FusedLocation) desde TrackingManager.
    val people = com.syemape.radio.data.TrackingManager.people.values.toList()
    val units = com.syemape.radio.data.TrackingManager.units.values.toList()

    Column(
        modifier = Modifier.fillMaxSize().background(MapeColors.Bg).padding(top = topPadding + 20.dp),
    ) {
        // ---- Top fijo (no scrollea para que el mapa sea interactivo) ----
        Column(
            Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton(MapeIcons.Menu, bg = MapeColors.Ink, tint = MapeColors.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(MapeIcons.Search, bg = MapeColors.White, tint = MapeColors.Ink)
                    Avatar(initialsOf(user?.name ?: "?"), MapeColors.Red, size = 46.dp, border = 2.dp, borderColor = MapeColors.White)
                }
            }
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Hola, $firstName", color = MapeColors.TextSubtle, fontFamily = Outfit, fontSize = 15.sp)
                    Row(
                        Modifier.height(28.dp).clip(CircleShape).background(MapeColors.RedSoftBg).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(MapeColors.Red))
                        Text("$pending alertas", color = MapeColors.RedDark, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Operadores en ruta", color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 30.sp)
            }
            FilterRow(
                listOf(
                    "Todos · ${summary?.total ?: 0}",
                    "En ruta · ${summary?.enRuta ?: 0}",
                    "Detenidos · ${summary?.detenidos ?: 0}",
                ),
                filter,
                activeDot = true,
            ) { filter = it }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Mapa grande e interactivo (se puede panear/hacer zoom) ----
        MapPreview(people, units, selectedId, user?.id, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp))

        Spacer(Modifier.height(12.dp))

        Text(
            "En línea · ${people.size}",
            color = MapeColors.TextSubtle, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.padding(start = 28.dp, bottom = 6.dp),
        )

        // ---- Lista de personas (scroll propio) ----
        LazyColumn(
            modifier = Modifier.height(190.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (people.isEmpty()) {
                item {
                    Text(
                        "Conectando con el equipo en vivo…",
                        color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
            items(people) { p ->
                PersonCard(p, p.id == user?.id, selected = p.id == selectedId) { selectedId = p.id }
            }
        }
    }
}

@OptIn(com.google.maps.android.compose.MapsComposeExperimentalApi::class)
@Composable
private fun MapPreview(people: List<LivePerson>, units: List<com.syemape.radio.data.UnitPosition>, selectedId: String?, meId: String?, boxModifier: Modifier) {
    val located = people.filter { it.lastLat != null && it.lastLng != null }
    val firstUnit = units.firstOrNull { it.lat != null && it.lng != null }
    val firstLat = located.firstOrNull()?.lastLat ?: firstUnit?.lat
    val firstLng = located.firstOrNull()?.lastLng ?: firstUnit?.lng
    val camera = com.google.maps.android.compose.rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(firstLat ?: -12.05, firstLng ?: -77.05),
            if (firstLat != null) 13f else 11f,
        )
    }
    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(firstLat, firstLng) {
        if (firstLat != null && firstLng != null && !centered) {
            centered = true
            camera.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                com.google.android.gms.maps.model.LatLng(firstLat, firstLng), 14f,
            )
        }
    }

    val selected = located.firstOrNull { it.id == selectedId }
    // Al seleccionar un operador: vuela la cámara a su posición.
    LaunchedEffect(selectedId, selected?.lastLat, selected?.lastLng) {
        if (selected?.lastLat != null && selected.lastLng != null) {
            camera.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(selected.lastLat, selected.lastLng), 16.5f,
                ),
                700,
            )
        }
    }

    Box(
        boxModifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFFE7ECE9)),
    ) {
        com.google.maps.android.compose.GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        ) {
            located.forEach { p ->
                val pos = com.google.android.gms.maps.model.LatLng(p.lastLat!!, p.lastLng!!)
                val st = com.google.maps.android.compose.rememberMarkerState(key = p.id, position = pos)
                st.position = pos
                com.google.maps.android.compose.MarkerComposable(
                    p.id, p.id == selectedId, p.id == meId, p.nickname ?: "", p.avatarKey ?: "",
                    state = st,
                    title = p.nickname?.takeIf { it.isNotBlank() } ?: p.name ?: "Operador",
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 1f),
                ) {
                    OperatorMarker(p, selected = p.id == selectedId, isMe = p.id == meId)
                }
            }
            units.filter { it.lat != null && it.lng != null }.forEach { u ->
                val pos = com.google.android.gms.maps.model.LatLng(u.lat!!, u.lng!!)
                com.google.maps.android.compose.Marker(
                    state = com.google.maps.android.compose.rememberMarkerState(key = "unit-${u.unitId}", position = pos),
                    title = u.code ?: "Unidad",
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE
                    ),
                )
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp).height(32.dp).clip(CircleShape).background(MapeColors.Ink).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveDot()
            Text("En vivo · ${people.size} en línea", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp).clip(CircleShape).background(MapeColors.White).pressScale { },
            contentAlignment = Alignment.Center,
        ) { Icon(MapeIcons.Locate, null, tint = MapeColors.Ink, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun OperatorMarker(p: LivePerson, selected: Boolean, isMe: Boolean) {
    val ring = when {
        selected -> MapeColors.Red
        isMe -> MapeColors.Blue
        else -> Color(0xFF2E9E5B)
    }
    val label = p.nickname?.takeIf { it.isNotBlank() }
        ?: p.name?.trim()?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() }
        ?: "Operador"
    val ringSize = if (selected) 40.dp else 34.dp
    val avatarSize = if (selected) 32.dp else 27.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(ringSize).clip(CircleShape).background(ring),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(initialsOf(p.name ?: p.nickname ?: "?"), avatarColor(p.id), size = avatarSize, border = 2.dp, borderColor = MapeColors.White)
        }
        if (selected) {
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                color = MapeColors.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(MapeColors.Ink).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PersonCard(p: LivePerson, isMe: Boolean, selected: Boolean, onSelect: () -> Unit) {
    val name = (p.nickname?.takeIf { it.isNotBlank() } ?: p.name ?: "—") + if (isMe) " (tú)" else ""
    val speed = (p.lastSpeedKmh ?: 0.0).toInt()
    val hasLocation = p.lastLat != null && p.lastLng != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.White)
            .then(if (selected) Modifier.border(1.5.dp, MapeColors.Red, RoundedCornerShape(18.dp)) else Modifier)
            .pressScale { onSelect() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(initialsOf(p.name ?: "?"), avatarColor(p.id), size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                if (hasLocation) "En vivo · $speed km/h" else "Sin ubicación todavía",
                color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp,
            )
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) MapeColors.Red else MapeColors.Bg).pressScale { onSelect() },
            contentAlignment = Alignment.Center,
        ) { Icon(MapeIcons.Locate, null, tint = if (selected) MapeColors.White else MapeColors.Ink, modifier = Modifier.size(18.dp)) }
    }
}
