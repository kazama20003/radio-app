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

    fun socket(namespace: String): Socket = synchronized(this) {
        sockets[namespace]?.let { return it }
        val opts = IO.Options().apply {
            // Polling + WebSocket: conecta YA por polling (rápido y compatible con proxies)
            // y sube a WebSocket. Antes, solo-WebSocket tardaba/reintentaba el handshake en
            // algunas redes → "Conectando…" de 10-15s.
            transports = arrayOf(Polling.NAME, WebSocket.NAME)
            reconnection = true
            // Reconexión robusta: nunca se rinde y reintenta rápido. Clave para que
            // la radio se recupere sola tras suspensiones en 2º plano de algunos
            // fabricantes (Xiaomi/Huawei/Samsung/Oppo…).
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 500
            reconnectionDelayMax = 2000
            timeout = 8000
            auth = mapOf("token" to (Backend.tokens.accessToken ?: ""))
        }
        val s = IO.socket(origin + namespace, opts)
        sockets[namespace] = s
        s.connect()
        s
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
