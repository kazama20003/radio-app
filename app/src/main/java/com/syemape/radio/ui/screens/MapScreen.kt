package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

@Composable
fun MapScreen(topPadding: Dp) {
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

    Column(
        modifier = Modifier.fillMaxSize().background(MapeColors.Bg).padding(top = topPadding + 20.dp),
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

        // ---- Mapa grande e interactivo (se puede panear/hacer zoom) ----
        MapPreview(
            people, units, selectedId, meId, routePoints,
            onMarkerClick = { selectedId = it },
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
        )

        // ---- Panel de navegación hacia el operador seleccionado ----
        if (selectedPerson != null && selectedPerson.id != meId) {
            NavPanel(
                target = selectedPerson,
                routing = routing,
                route = if (routeFor == selectedPerson.id) routeInfo else null,
                onNavigate = { navigateTo(selectedPerson) },
                onShowSteps = { showSteps = true },
                onCancel = { clearRoute() },
            )
        }

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
                PersonCard(p, p.id == meId, selected = p.id == selectedId) { selectedId = p.id }
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
    onNavigate: () -> Unit,
    onShowSteps: () -> Unit,
    onCancel: () -> Unit,
) {
    val name = target.nickname?.takeIf { it.isNotBlank() } ?: target.name ?: "Operador"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp)).background(MapeColors.Ink).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (route != null) "Ruta a $name" else "Ir a $name",
                color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
            )
            Text(
                when {
                    routing -> "Calculando ruta…"
                    route != null -> listOfNotNull(route.distanceText, route.durationText).joinToString(" · ").ifBlank { "Ruta lista" }
                    else -> "Trazar camino por carretera"
                },
                color = MapeColors.TextOnDark, fontFamily = Outfit, fontSize = 12.sp, maxLines = 1,
            )
        }
        // Navegación NATIVA (Google Maps, paso a paso por voz). Siempre disponible.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (target.lastLat != null && target.lastLng != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MapeColors.Card).pressScale {
                    openNativeNav(ctx, target.lastLat, target.lastLng)
                },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.Pin, null, tint = MapeColors.Text, modifier = Modifier.size(20.dp)) }
        }
        if (route != null) {
            // Ver indicaciones
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MapeColors.Card).pressScale { onShowSteps() },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.Sliders, null, tint = MapeColors.Text, modifier = Modifier.size(20.dp)) }
            // Cancelar ruta
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MapeColors.Red).pressScale { onCancel() },
                contentAlignment = Alignment.Center,
            ) { Icon(MapeIcons.Close, null, tint = MapeColors.White, modifier = Modifier.size(20.dp)) }
        } else {
            Row(
                Modifier.clip(CircleShape).background(MapeColors.Red).pressScale(enabled = !routing) { onNavigate() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(MapeIcons.Navigation, null, tint = MapeColors.White, modifier = Modifier.size(18.dp))
                Text("Ir", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
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
    boxModifier: Modifier,
) {
    val cameraScope = rememberCoroutineScope()
    val context = LocalContext.current
    var satellite by remember { mutableStateOf(true) }
    var traffic by remember { mutableStateOf(true) }
    val located = people.filter { isValidCoordinate(it.lastLat, it.lastLng) }
    val photoSources = located.map { it.id to it.photoUrl }
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
        ) {
            if (routePoints.size >= 2) {
                com.google.maps.android.compose.Polyline(
                    points = routePoints,
                    color = MapeColors.Red,
                    width = 14f,
                )
            }
            located.forEach { p ->
                val pos = com.google.android.gms.maps.model.LatLng(p.lastLat!!, p.lastLng!!)
                val st = com.google.maps.android.compose.rememberMarkerState(key = p.id, position = pos)
                st.position = pos
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
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp).height(32.dp).clip(CircleShape).background(MapeColors.Ink).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveDot()
            Text("En vivo · ${people.size} en línea", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
private fun OperatorMarker(p: LivePerson, selected: Boolean, isMe: Boolean, photo: ImageBitmap?) {
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
            Box(Modifier.size(avatarSize), contentAlignment = Alignment.Center) {
                Avatar(initialsOf(p.name ?: p.nickname ?: "?"), avatarColor(p.id), size = avatarSize, border = 2.dp, borderColor = MapeColors.White)
                if (photo != null) Image(
                    bitmap = photo, contentDescription = "Foto de $label",
                    modifier = Modifier.size(avatarSize - 3.dp).clip(CircleShape), contentScale = ContentScale.Crop,
                )
            }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MapeColors.Card)
            .then(if (selected) Modifier.border(1.5.dp, MapeColors.Red, RoundedCornerShape(18.dp)) else Modifier)
            .pressScale { onSelect() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Avatar(initialsOf(p.name ?: "?"), avatarColor(p.id), size = 44.dp)
            if (!p.photoUrl.isNullOrBlank()) AsyncImage(
                model = p.photoUrl, contentDescription = "Foto de ${p.nickname ?: p.name}",
                modifier = Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                if (hasLocation) "En vivo · $speed km/h" else "Sin ubicación todavía",
                color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 12.sp,
            )
            if (hasLocation) {
                val lastUpdate = Fmt.dateTime(p.lastPositionAt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(MapeIcons.Clock, null, tint = MapeColors.TextFaint, modifier = Modifier.size(13.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Última ubicación", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 9.sp, maxLines = 1)
                        Text(
                            lastUpdate.ifBlank { "Fecha y hora no enviadas" },
                            color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 10.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) MapeColors.Red else MapeColors.Bg).pressScale { onSelect() },
            contentAlignment = Alignment.Center,
        ) { Icon(MapeIcons.Locate, null, tint = if (selected) MapeColors.White else MapeColors.Ink, modifier = Modifier.size(18.dp)) }
    }
}
