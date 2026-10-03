package com.syemape.radio.data

import com.google.gson.Gson
import com.syemape.radio.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.Polling
import io.socket.engineio.client.transports.WebSocket
import org.json.JSONObject

/**
 * Gestor de conexiones Socket.IO a los namespaces del backend (/chat, /tracking,
 * /alerts, /radio). El handshake se autentica con el access token (auth.token).
 */
object Realtime {
    val gson = Gson()

    /** Origen WS: la API_BASE sin el sufijo /api. */
    private val origin: String = BuildConfig.API_BASE_URL.substringBefore("/api")

    private val sockets = HashMap<String, Socket>()

    // Token de auth COMPARTIDO y mutable: al refrescarlo, los reconnect lo reusan sin
    // recrear el socket. Clave para que un token vencido (15 min) no deje "Conectando…".
    private val authMap: MutableMap<String, String> = java.util.Collections.synchronizedMap(HashMap())
    @Volatile private var refreshing = false

    fun socket(namespace: String): Socket = synchronized(this) {
        sockets[namespace]?.let { return it }
        authMap["token"] = Backend.tokens.accessToken ?: ""
        val opts = IO.Options().apply {
            // Polling + WebSocket: conecta YA por polling (rápido y compatible con proxies)
            // y sube a WebSocket. Antes, solo-WebSocket tardaba/reintentaba el handshake.
            transports = arrayOf(Polling.NAME, WebSocket.NAME)
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 500
            reconnectionDelayMax = 2000
            timeout = 8000
            auth = authMap // mismo mapa mutable: refrescar el token se refleja al reconectar
        }
        val s = IO.socket(origin + namespace, opts)
        // Si el servidor RECHAZA por token vencido (lo desconecta), refrescamos el token
        // y reconectamos con el nuevo — en ~1s, en vez de quedar en bucle con el viejo.
        s.on(Socket.EVENT_CONNECT) { android.util.Log.d("RadioTiming", "socket $namespace CONNECT") }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            android.util.Log.w("RadioTiming", "socket $namespace CONNECT_ERROR: ${args.firstOrNull()}")
            refreshTokenAndReconnect()
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args.firstOrNull() as? String
            android.util.Log.w("RadioTiming", "socket $namespace DISCONNECT: $reason")
            if (reason == "io server disconnect") refreshTokenAndReconnect()
        }
        sockets[namespace] = s
        s.connect()
        s
    }

    /** Refresca el access token (si falló la auth del socket) y reconecta todos los sockets. */
    private fun refreshTokenAndReconnect() {
        if (refreshing) return
        refreshing = true
        Thread {
            runCatching {
                val prev = authMap["token"]
                val ok = Backend.refreshAccessToken(prev) // dedup: si otro ya renovó, true
                android.util.Log.w("RadioTiming", "refreshAccessToken -> $ok")
                if (ok) {
                    authMap["token"] = Backend.tokens.accessToken ?: ""
                    synchronized(this) { sockets.values.forEach { runCatching { it.connect() } } }
                }
            }
            refreshing = false
        }.start()
    }

    fun closeAll() = synchronized(this) {
        sockets.values.forEach { runCatching { it.disconnect() } }
        sockets.clear()
    }

    /** Convierte el primer argumento (JSONObject) de un evento a un modelo. */
    inline fun <reified T> parse(args: Array<out Any?>): T? {
        val first = args.firstOrNull() as? JSONObject ?: return null
        return runCatching { gson.fromJson(first.toString(), T::class.java) }.getOrNull()
    }
}
