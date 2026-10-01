package com.syemape.radio.data

import com.google.gson.Gson
import com.syemape.radio.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
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
            transports = arrayOf(WebSocket.NAME)
            reconnection = true
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
