package com.syemape.radio.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Posiciones en vivo: siembra desde REST, escucha presence:update / position:update
 * por Socket.IO (/tracking) y reporta la ubicación propia con FusedLocation.
 */
object TrackingManager {
    val people = mutableStateMapOf<String, LivePerson>()
    val units = mutableStateMapOf<String, UnitPosition>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fused: FusedLocationProviderClient? = null
    private var locationCb: LocationCallback? = null
    private var started = false
    private var presenceListener: Emitter.Listener? = null
    private var positionListener: Emitter.Listener? = null

    fun start(context: Context) {
        if (started) return
        started = true
        scope.launch {
            runCatching { Backend.api.livePeople() }.getOrNull()?.forEach { people[it.id] = it }
            runCatching { Backend.api.liveUnits() }.getOrNull()?.forEach { u ->
                units[u.id] = UnitPosition(
                    u.id, u.code, u.lastLat, u.lastLng, u.lastSpeedKmh, u.lastHeading, u.status, u.operator, u.lastPositionAt,
                )
            }
        }
        val s = Realtime.socket("/tracking")
        presenceListener = Emitter.Listener { args ->
            val p = Realtime.parse<LivePerson>(args)
            if (p != null && p.id.isNotEmpty()) scope.launch { people[p.id] = p }
        }
        positionListener = Emitter.Listener { args ->
            val u = Realtime.parse<UnitPosition>(args)
            if (u != null && u.unitId.isNotEmpty()) scope.launch { units[u.unitId] = u }
        }
        s.on("presence:update", presenceListener)
        s.on("position:update", positionListener)
        startLocationUpdates(context)
    }

    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context) {
        if (!hasLocationPermission(context) || locationCb != null) return
        val client = fused ?: LocationServices.getFusedLocationProviderClient(context).also { fused = it }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    runCatching {
                        Backend.api.reportMyPosition(
                            ReportPositionRequest(
                                lat = loc.latitude,
                                lng = loc.longitude,
                                speedKmh = (loc.speed * 3.6),
                                heading = if (loc.hasBearing()) loc.bearing.toDouble() else null,
                                accuracy = loc.accuracy.toDouble(),
                            )
                        )
                    }
                }
            }
        }
        locationCb = cb
        runCatching { client.requestLocationUpdates(req, cb, Looper.getMainLooper()) }
    }

    fun stop() {
        locationCb?.let { cb -> fused?.removeLocationUpdates(cb) }
        locationCb = null
        runCatching {
            val s = Realtime.socket("/tracking")
            presenceListener?.let { s.off("presence:update", it) }
            positionListener?.let { s.off("position:update", it) }
        }
        people.clear(); units.clear()
        started = false
    }
}
