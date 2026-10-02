package com.syemape.radio.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Contadores globales de la barra inferior: no leídos de chat y alertas pendientes.
 * Se refrescan desde REST y se actualizan en vivo por Socket.IO.
 */
object AppBadges {
    var unreadChats by mutableIntStateOf(0)
        private set
    var pendingAlerts by mutableIntStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wired = false

    fun refresh() {
        scope.launch {
            runCatching { Backend.api.conversations() }.getOrNull()?.let { list ->
                unreadChats = list.sumOf { it.unread }
            }
            runCatching { Backend.api.alertMetrics() }.getOrNull()?.let { pendingAlerts = it.pendientes }
        }
    }

    /** Conecta los sockets de chat/alertas para mantener los contadores en vivo. */
    fun wireRealtime() {
        if (wired) return
        wired = true
        val chat = Realtime.socket("/chat")
        chat.on("conversation:updated", Emitter.Listener { refresh() })
        val alerts = Realtime.socket("/alerts")
        alerts.on("alert:new", Emitter.Listener { args ->
            scope.launch { pendingAlerts += 1 }
            // Blindado: un fallo al notificar nunca debe romper el hilo del socket.
            runCatching { Realtime.parse<Alert>(args)?.let { Notifier.notifyAlert(it) } }
        })
        alerts.on("alert:updated", Emitter.Listener { refresh() })
    }

    fun reset() {
        unreadChats = 0
        pendingAlerts = 0
        wired = false
    }
}
