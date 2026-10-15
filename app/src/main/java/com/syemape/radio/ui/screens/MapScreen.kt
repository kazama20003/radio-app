package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.Coil
import coil.request.ImageRequest
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.google.android.gms.maps.model.LatLng
import com.syemape.radio.data.Backend
import com.syemape.radio.data.DirectionsResult
import com.syemape.radio.data.Fmt
import com.syemape.radio.data.LivePerson
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.FilterRow
import com.syemape.radio.ui.LiveDot
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.rememberAsync
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Abre la navegación paso a paso en la app de mapas nativa (Google Maps). */
private fun openNativeNav(context: android.content.Context, lat: Double, lng: Double) {
    val nav = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("google.navigation:q=$lat,$lng&mode=d"),
    ).setPackage("com.google.android.apps.maps").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (nav.resolveActivity(context.packageManager) != null) context.startActivity(nav)
        else context.startActivity(web)
    }
}

/** Decodifica una polilínea codificada de Google en una lista de puntos. */
private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    fun readDelta(): Int? {
        var shift = 0
        var result = 0
        while (index < len) {
            val b = encoded[index++].code - 63
            if (b !in 0..63 || shift > 30 || (shift == 30 && (b and 0x1f) > 1)) return null
            result = result or ((b and 0x1f) shl shift)
            if (b < 0x20) {
                return if (result and 1 != 0) (result shr 1).inv() else result shr 1
            }
            shift += 5
        }
        return null // Truncated coordinate.
    }

    while (index < len) {
        val dlat = readDelta() ?: return emptyList()
        val dlng = readDelta() ?: return emptyList()
        val nextLat = lat.toLong() + dlat
        val nextLng = lng.toLong() + dlng
        if (nextLat !in -9_000_000L..9_000_000L || nextLng !in -18_000_000L..18_000_000L) return emptyList()
        lat = nextLat.toInt()
        lng = nextLng.toInt()
        poly.add(LatLng(lat / 1e5, lng / 1e5))
    }
    return poly
}

private fun isValidCoordinate(lat: Double?, lng: Double?): Boolean =
    lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
        lat in -90.0..90.0 && lng in -180.0..180.0

private data class OperatorMapPin(
    val person: LivePerson,
    val displayPosition: LatLng,
)

private data class OperatorMapGroup(
    val people: List<LivePerson>,
    val center: LatLng,
    val pins: List<OperatorMapPin>,
    val clustered: Boolean,
)

@Composable
fun MapScreen(topPadding: Dp, bottomPadding: Dp = 0.dp) {
    var filter by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val user = SessionManager.user
    val meId = user?.id
    val firstName = (user?.nickname ?: user?.name)?.trim()?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "operador"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val summaryRes by rememberAsync { Backend.api.unitsSummary() }
    val metricsRes by rememberAsync { Backend.api.alertMetrics() }
    val summary = summaryRes?.getOrNull()
    val pending = metricsRes?.getOrNull()?.pendientes ?: 0
    // Estado en vivo (socket + FusedLocation) desde TrackingManager.
    val people = com.syemape.radio.data.TrackingManager.people.values.toList()
    val units = com.syemape.radio.data.TrackingManager.units.values.toList()

    // Socket.IO da actualizaciones inmediatas; este refresco recupera eventos perdidos
    // y mantiene vigente el último punto mientras el mapa está abierto.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { Backend.api.livePeople() }.getOrNull()?.forEach { person ->
                if (person.id.isNotBlank()) com.syemape.radio.data.TrackingManager.people[person.id] = person
            }
            runCatching { Backend.api.liveUnits() }.getOrNull()?.forEach { unit ->
                com.syemape.radio.data.TrackingManager.units[unit.id] = com.syemape.radio.data.UnitPosition(
                    unitId = unit.id,
                    code = unit.code,
                    lat = unit.lastLat,
                    lng = unit.lastLng,
                    speedKmh = unit.lastSpeedKmh,
                    heading = unit.lastHeading,
                    status = unit.status,
                    operator = unit.operator,
                    recordedAt = unit.lastPositionAt,
                )
            }
            delay(5_000)
        }
    }

    // ---- Estado de la ruta/navegación ----
    var routeFor by remember { mutableStateOf<String?>(null) } // id del destino con ruta activa
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeInfo by remember { mutableStateOf<DirectionsResult?>(null) }
    var routing by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    fun clearRoute() {
        routeFor = null; routePoints = emptyList(); routeInfo = null; showSteps = false
    }

    fun navigateTo(target: LivePerson) {
        val me = people.firstOrNull { it.id == meId }
        val oLat = me?.lastLat
        val oLng = me?.lastLng
        if (!isValidCoordinate(oLat, oLng)) { toast("Aún no tenemos tu ubicación"); return }
        val originLat = oLat ?: return
        val originLng = oLng ?: return
        val dLat = target.lastLat
        val dLng = target.lastLng
        if (!isValidCoordinate(dLat, dLng)) { toast("Ese operador no tiene ubicación"); return }
        val destinationLat = dLat ?: return
        val destinationLng = dLng ?: return
        scope.launch {
            routing = true
            val res = runCatching { Backend.api.directions(originLat, originLng, destinationLat, destinationLng) }.getOrNull()
            routing = false
            if (res == null || !res.ok || res.overviewPolyline.isNullOrBlank()) {
                toast("No se pudo trazar la ruta"); return@launch
            }
            val decoded = decodePolyline(res.overviewPolyline.orEmpty())
            if (decoded.size < 2) {
                toast("La ruta recibida no es válida; intenta de nuevo")
                return@launch
            }
            routePoints = decoded
            routeInfo = res
            routeFor = target.id
        }
    }

    val selectedPerson = people.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId) {
        val pickedId = selectedId ?: return@LaunchedEffect
        if (pickedId != meId) {
            delay(6_500)
            if (selectedId == pickedId) selectedId = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MapeColors.Bg)
            .padding(top = topPadding + 8.dp, bottom = bottomPadding + 78.dp),
    ) {
        // ---- Top fijo (no scrollea para que el mapa sea interactivo) ----
        Column(
            Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(22.dp)),
            ) {
                Image(
                    painter = painterResource(com.syemape.radio.R.drawable.map_header),
                    contentDescription = "Camioneta MAPE en ruta",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Row(
                    Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(78.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.68f)).padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(com.syemape.radio.R.drawable.mape_brand),
                            contentDescription = "Logo de S&E MAPE",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.58f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text("MAPA EN VIVO", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Hola, $firstName", color = Color.White.copy(alpha = 0.9f), fontFamily = Outfit, fontSize = 11.sp)
                    }
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).height(27.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.68f)).padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MapeColors.Red))
                    Text("$pending alertas", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
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

        // ---- Mapa en vivo ----
        MapPreview(
            people, units, selectedId, meId, routePoints,
            onMarkerClick = { selectedId = it },
            navigationTarget = selectedPerson?.takeIf { it.id != meId },
            routing = routing,
            route = if (routeFor == selectedPerson?.id) routeInfo else null,
            onNavigate = { selectedPerson?.let(::navigateTo) },
            onShowSteps = { showSteps = true },
            onCancelRoute = { clearRoute() },
            onDismissNavigation = { selectedId = null },
            boxModifier = Modifier.fillMaxWidth().weight(0.72f).padding(horizontal = 24.dp),
        )

        // Lista siempre visible para ubicar rápidamente a cualquier persona, sin buscador.
        Column(
            Modifier.fillMaxWidth().weight(0.28f).padding(horizontal = 24.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp)).background(MapeColors.Card)
                .border(1.dp, MapeColors.Border, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveDot()
                Text("PERSONAL EN VIVO", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("${people.size}", color = MapeColors.Red, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (people.isEmpty()) {
                Text("No hay personas compartiendo ubicación", color = MapeColors.TextMuted,
                    fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(people.sortedBy { it.nickname?.lowercase() ?: it.name.orEmpty().lowercase() }, key = { it.id }) { person ->
                        val nickname = person.nickname?.takeIf { it.isNotBlank() }
                            ?: person.name?.trim()?.split(" ")?.firstOrNull() ?: "Operador"
                        val hasPosition = isValidCoordinate(person.lastLat, person.lastLng)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (person.id == selectedId) MapeColors.Red.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable {
                                    if (hasPosition) selectedId = person.id
                                    else toast("$nickname todavía no comparte ubicación")
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(avatarColor(person.id)), contentAlignment = Alignment.Center) {
                                Avatar(initialsOf(person.name ?: person.nickname ?: "?"), avatarColor(person.id), size = 38.dp)
                                AsyncImage(
                                    model = person.photoUrl,
                                    contentDescription = "Foto de $nickname",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(nickname, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!person.name.isNullOrBlank() && person.name != nickname) {
                                    Text(person.name, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                val lastPositionAt = Fmt.dateTime(person.lastPositionAt)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(MapeIcons.Pin, contentDescription = null, tint = MapeColors.TextFaint, modifier = Modifier.size(10.dp))
                                    Text(
                                        if (lastPositionAt.isBlank()) "Sin ubicación reportada" else "Últ. ubicación · $lastPositionAt",
                                        color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 9.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(if (hasPosition) "VER" else "SIN GPS", color = if (hasPosition) MapeColors.Red else MapeColors.TextFaint,
                                fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

    }

    // ---- Indicaciones paso a paso ----
    if (showSteps) {
        routeInfo?.let { info ->
            StepsSheet(info, onClose = { showSteps = false })
        }
    }
}

@Composable
private fun NavPanel(
    target: LivePerson,
    routing: Boolean,
    route: DirectionsResult?,
    modifier: Modifier,
    onNavigate: () -> Unit,
    onShowSteps: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = target.nickname?.takeIf { it.isNotBlank() } ?: target.name ?: "Operador"
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.Ink.copy(alpha = 0.97f))
            .border(1.dp, MapeColors.Border, RoundedCornerShape(18.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(avatarColor(target.id)), contentAlignment = Alignment.Center) {
                Avatar(initialsOf(target.name ?: name), avatarColor(target.id), size = 36.dp)
                AsyncImage(
                    model = target.photoUrl, contentDescription = "Foto de $name",
                    modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        routing -> "Calculando ruta…"
                        route != null -> listOfNotNull(route.distanceText, route.durationText).joinToString(" · ").ifBlank { "Ruta lista" }
                        else -> "Seleccionado en el mapa"
                    },
                    color = MapeColors.TextOnDark, fontFamily = Outfit, fontSize = 10.sp, maxLines = 1,
                )
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).clickable { onDismiss() },
                contentAlignment = Alignment.Center) {
                Icon(MapeIcons.Close, "Cerrar", tint = MapeColors.White, modifier = Modifier.size(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (target.lastLat != null && target.lastLng != null) {
                MapActionButton("Google Maps", MapeIcons.Pin, false, {
                    openNativeNav(ctx, target.lastLat, target.lastLng)
                })
            }
            if (route != null) {
                MapActionButton("Pasos", MapeIcons.Sliders, false, onShowSteps)
                MapActionButton("Quitar ruta", MapeIcons.Close, true, onCancel)
            } else {
                MapActionButton(if (routing) "Calculando…" else "Trazar ruta", MapeIcons.Navigation, true, onNavigate, enabled = !routing)
            }
        }
    }
}

@Composable
private fun RowScope.MapActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean,
    onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.weight(1f).clip(CircleShape).background(if (primary) MapeColors.Red else Color.White.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MapeColors.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepsSheet(info: DirectionsResult, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(22.dp)).background(MapeColors.Card).padding(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Indicaciones", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        listOfNotNull(info.distanceText, info.durationText).joinToString(" · "),
                        color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 13.sp,
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(MapeColors.Bg).pressScale { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Icon(MapeIcons.Close, null, tint = MapeColors.Text, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(info.steps) { step ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(MapeColors.Bg),
                            contentAlignment = Alignment.Center,
                        ) { Icon(MapeIcons.Navigation, null, tint = MapeColors.Text, modifier = Modifier.size(16.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(step.instruction, color = MapeColors.Text, fontFamily = Outfit, fontSize = 14.sp)
                            if (!step.distanceText.isNullOrBlank()) {
                                Text(step.distanceText!!, color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(com.google.maps.android.compose.MapsComposeExperimentalApi::class)
@Composable
private fun MapPreview(
    people: List<LivePerson>,
    units: List<com.syemape.radio.data.UnitPosition>,
    selectedId: String?,
    meId: String?,
    routePoints: List<LatLng>,
    onMarkerClick: (String) -> Unit,
    navigationTarget: LivePerson?,
    routing: Boolean,
    route: DirectionsResult?,
    onNavigate: () -> Unit,
    onShowSteps: () -> Unit,
    onCancelRoute: () -> Unit,
    onDismissNavigation: () -> Unit,
    boxModifier: Modifier,
) {
    val cameraScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var satellite by remember { mutableStateOf(true) }
    var traffic by remember { mutableStateOf(true) }
    var mapLoaded by remember { mutableStateOf(false) }
    val located = people.filter { isValidCoordinate(it.lastLat, it.lastLng) }
    var operatorGroups by remember { mutableStateOf<List<OperatorMapGroup>>(emptyList()) }
    LaunchedEffect(located) {
        // Until the map projection is ready, show each operator at its real GPS point.
        operatorGroups = located.map { person ->
            val point = LatLng(person.lastLat!!, person.lastLng!!)
            OperatorMapGroup(listOf(person), point, listOf(OperatorMapPin(person, point)), clustered = false)
        }
    }
    val photoSources = people.map { it.id to it.photoUrl }
    var markerPhotos by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(photoSources) {
        val loader = Coil.imageLoader(context)
        val loaded = coroutineScope {
            photoSources.mapNotNull { (id, url) ->
                if (url.isNullOrBlank()) null else async {
                    runCatching {
                        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                        val drawable = loader.execute(request).drawable as? BitmapDrawable
                        drawable?.bitmap?.asImageBitmap()?.let { id to it }
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
        markerPhotos = loaded
    }
    val firstUnit = units.firstOrNull { isValidCoordinate(it.lat, it.lng) }
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
    val myPosition = located.firstOrNull { it.id == meId }
    // Al seleccionar un operador: vuela la cámara a su posición.
    LaunchedEffect(selectedId, selected?.lastLat, selected?.lastLng) {
        if (routePoints.isEmpty() && selected?.lastLat != null && selected.lastLng != null) {
            camera.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(selected.lastLat, selected.lastLng), 16.5f,
                ),
                700,
            )
        }
    }

    // Al trazar una ruta: encuadra toda la ruta en la cámara.
    LaunchedEffect(routePoints) {
        if (routePoints.size >= 2) {
            val b = com.google.android.gms.maps.model.LatLngBounds.Builder()
            routePoints.forEach { b.include(it) }
            runCatching {
                camera.animate(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(b.build(), 120),
                    800,
                )
            }
        }
    }

    Box(
        boxModifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFFE7ECE9)),
    ) {
        com.google.maps.android.compose.GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = com.google.maps.android.compose.MapProperties(
                // Keep geographic tiles readable even when the rest of the app uses dark mode.
                mapStyleOptions = null,
                mapType = if (satellite) com.google.maps.android.compose.MapType.SATELLITE else com.google.maps.android.compose.MapType.NORMAL,
                isTrafficEnabled = traffic,
            ),
            uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { if (navigationTarget != null) onDismissNavigation() },
        ) {
            // Agrupa cuando están cercanos al alejar el zoom; al acercar, separa
            // suavemente sus avatares sin líneas que ensucien el mapa.
            com.google.maps.android.compose.MapEffect(located, camera.position.zoom, mapLoaded) { map ->
                if (!mapLoaded) return@MapEffect
                val projection = map.projection
                val remaining = located.toMutableList()
                val groups = mutableListOf<List<LivePerson>>()
                val screenPoints = located.associate { person ->
                    person.id to projection.toScreenLocation(LatLng(person.lastLat!!, person.lastLng!!))
                }
                val minSeparationPx = 54f * density
                while (remaining.isNotEmpty()) {
                    val seed = remaining.removeAt(0)
                    val group = mutableListOf(seed)
                    var cursor = 0
                    while (cursor < group.size) {
                        val currentPoint = screenPoints.getValue(group[cursor].id)
                        val matches = remaining.filter { candidate ->
                            val point = screenPoints.getValue(candidate.id)
                            val dx = (point.x - currentPoint.x).toFloat()
                            val dy = (point.y - currentPoint.y).toFloat()
                            dx * dx + dy * dy <= minSeparationPx * minSeparationPx
                        }
                        matches.forEach { candidate ->
                            remaining.remove(candidate)
                            group += candidate
                        }
                        cursor++
                    }
                    groups += group
                }
                val shouldCluster = camera.position.zoom < 15.5f
                operatorGroups = groups.map { group ->
                    val points = group.map { screenPoints.getValue(it.id) }
                    val centerX = points.map { it.x }.average().toInt()
                    val centerY = points.map { it.y }.average().toInt()
                    val center = runCatching { projection.fromScreenLocation(android.graphics.Point(centerX, centerY)) }
                        .getOrDefault(LatLng(group.first().lastLat!!, group.first().lastLng!!))
                    if (group.size == 1 || (shouldCluster && group.size > 1)) {
                        OperatorMapGroup(
                            people = group,
                            center = center,
                            pins = group.map { p -> OperatorMapPin(p, LatLng(p.lastLat!!, p.lastLng!!)) },
                            clustered = shouldCluster && group.size > 1,
                        )
                    } else {
                        val radiusPx = (48f + (group.size.coerceAtMost(8) * 3f)) * density
                        val pins = group.mapIndexed { index, person ->
                            val angle = (2.0 * Math.PI * index / group.size) - Math.PI / 2.0
                            val x = centerX + (kotlin.math.cos(angle) * radiusPx).toInt()
                            val y = centerY + (kotlin.math.sin(angle) * radiusPx).toInt()
                            val display = runCatching {
                                projection.fromScreenLocation(android.graphics.Point(x, y))
                            }.getOrDefault(LatLng(person.lastLat!!, person.lastLng!!))
                            OperatorMapPin(person, display)
                        }
                        OperatorMapGroup(group, center, pins, clustered = false)
                    }
                }
            }
            if (routePoints.size >= 2) {
                com.google.maps.android.compose.Polyline(
                    points = routePoints,
                    color = Color(0xFF17212B),
                    width = 22f,
                )
                com.google.maps.android.compose.Polyline(
                    points = routePoints,
                    color = Color(0xFF00A6FF),
                    width = 13f,
                )
            }
            operatorGroups.forEach { group ->
                if (group.clustered) {
                    val state = com.google.maps.android.compose.rememberMarkerState(
                        key = "cluster-${group.people.map { it.id }.sorted().joinToString()}-${camera.position.zoom.toInt()}",
                        position = group.center,
                    )
                    state.position = group.center
                    com.google.maps.android.compose.MarkerComposable(
                        "cluster-${group.people.map { it.id }.sorted().joinToString()}-${camera.position.zoom.toInt()}",
                        state = state,
                        title = "${group.people.size} personas cercanas",
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = {
                            cameraScope.launch {
                                camera.animate(
                                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                        group.center, (camera.position.zoom + 2.5f).coerceAtMost(19f),
                                    ),
                                    450,
                                )
                            }
                            true
                        },
                    ) {
                        OperatorClusterMarker(group.people, markerPhotos)
                    }
                }
                group.pins.forEach { pin ->
                    val p = pin.person
                    val st = com.google.maps.android.compose.rememberMarkerState(key = p.id, position = pin.displayPosition)
                    st.position = pin.displayPosition
                    com.google.maps.android.compose.MarkerComposable(
                        p.id, p.id == selectedId, p.id == meId, p.nickname ?: "", p.avatarKey ?: "",
                        p.photoUrl ?: "", markerPhotos[p.id]?.hashCode() ?: 0,
                        state = st,
                        title = p.nickname?.takeIf { it.isNotBlank() } ?: p.name ?: "Operador",
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1f),
                        onClick = { onMarkerClick(p.id); true },
                    ) {
                        OperatorMarker(p, selected = p.id == selectedId, isMe = p.id == meId, photo = markerPhotos[p.id])
                    }
                }
            }
            units.filter { isValidCoordinate(it.lat, it.lng) }.forEach { u ->
                val pos = com.google.android.gms.maps.model.LatLng(u.lat!!, u.lng!!)
                // Reasignar la posición en cada recomposición: si no, el marcador se
                // quedaba congelado en el primer punto (desfase en el mapa en vivo).
                val st = com.google.maps.android.compose.rememberMarkerState(key = "unit-${u.unitId}", position = pos)
                st.position = pos
                com.google.maps.android.compose.Marker(
                    state = st,
                    title = u.code ?: "Unidad",
                    snippet = u.recordedAt?.let { recordedAt ->
                        Fmt.dateTime(recordedAt).takeIf { it.isNotBlank() }?.let { "Última ubicación · $it" }
                    },
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE
                    ),
                )
            }
        }
        if (navigationTarget != null) {
            NavPanel(
                target = navigationTarget,
                routing = routing,
                route = route,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 10.dp, end = 58.dp),
                onNavigate = onNavigate,
                onShowSteps = onShowSteps,
                onCancel = onCancelRoute,
                onDismiss = onDismissNavigation,
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp).clip(CircleShape).background(MapeColors.Card)
                .pressScale(enabled = myPosition != null) {
                    myPosition?.let { person ->
                        cameraScope.launch {
                            camera.animate(
                                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                    LatLng(person.lastLat!!, person.lastLng!!), 16f,
                                ),
                                500,
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(MapeIcons.Locate, null, tint = MapeColors.Text, modifier = Modifier.size(20.dp)) }
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapLayerChip("Satélite", satellite) { satellite = !satellite }
            MapLayerChip("Tráfico", traffic) { traffic = !traffic }
        }
    }
}

private fun darkMapStyleOptions() = com.google.android.gms.maps.model.MapStyleOptions(
    """[
      {"elementType":"geometry","stylers":[{"color":"#242424"}]},
      {"elementType":"labels.text.fill","stylers":[{"color":"#d0d0d0"}]},
      {"elementType":"labels.text.stroke","stylers":[{"color":"#181818"}]},
      {"featureType":"road","elementType":"geometry","stylers":[{"color":"#3a3a3a"}]},
      {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#555555"}]},
      {"featureType":"water","elementType":"geometry","stylers":[{"color":"#17232b"}]},
      {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#2d2d2d"}]},
      {"featureType":"landscape","elementType":"geometry","stylers":[{"color":"#202020"}]}
    ]""".trimIndent(),
)

@Composable
private fun MapLayerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MapeColors.White else MapeColors.Text,
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) MapeColors.Red else MapeColors.Card)
            .pressScale { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
    )
}

@Composable
private fun OperatorClusterMarker(people: List<LivePerson>, photos: Map<String, ImageBitmap>) {
    Box(
        Modifier.widthIn(min = 66.dp).height(50.dp).clip(CircleShape).background(MapeColors.Red)
            .border(2.dp, MapeColors.White, CircleShape).padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
            people.take(3).forEach { person ->
                val size = if (people.size == 1) 36.dp else 30.dp
                Box(Modifier.size(size).clip(CircleShape).background(avatarColor(person.id)).border(1.5.dp, MapeColors.White, CircleShape),
                    contentAlignment = Alignment.Center) {
                    val photo = photos[person.id]
                    if (photo != null) {
                        Image(photo, contentDescription = person.nickname ?: person.name,
                            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(initialsOf(person.nickname ?: person.name ?: "?"), color = MapeColors.White,
                            fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            }
        }
        if (people.size > 3) {
            Box(Modifier.align(Alignment.BottomEnd).size(21.dp).clip(CircleShape).background(MapeColors.Ink)
                .border(1.dp, MapeColors.White, CircleShape), contentAlignment = Alignment.Center) {
                Text("+${people.size - 3}", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun OperatorMarker(p: LivePerson, selected: Boolean, isMe: Boolean, photo: ImageBitmap?) {
    val ring = when {
        selected -> MapeColors.Red
        isMe -> MapeColors.Blue
        else -> Color(0xFF2E9E5B)
    }
    val label = p.nickname?.takeIf { it.isNotBlank() }
        ?: p.name?.trim()?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() }
        ?: "Operador"
    val ringSize = if (selected) 54.dp else 48.dp
    val avatarSize = if (selected) 46.dp else 40.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(ringSize).clip(CircleShape).background(ring),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(avatarSize), contentAlignment = Alignment.Center) {
                Avatar(initialsOf(p.name ?: p.nickname ?: "?"), avatarColor(p.id), size = avatarSize, border = 2.dp, borderColor = MapeColors.White)
                if (photo != null) Image(
                    bitmap = photo, contentDescription = "Foto de $label",
                    modifier = Modifier.size(avatarSize - 3.dp).clip(CircleShape), contentScale = ContentScale.Crop,
                )
            }
            // Estas personas llegan de la lista de presencia/ubicaciones en vivo.
            Box(
                Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape)
                    .background(Color(0xFF22C55E)).border(1.5.dp, MapeColors.White, CircleShape),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = MapeColors.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.Bold,
            fontSize = if (selected) 12.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected) MapeColors.Red else MapeColors.Ink)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
