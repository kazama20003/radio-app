package com.syemape.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Servicio en primer plano que mantiene viva la radio (socket + audio WebRTC)
 * mientras el usuario está en un canal, incluso con la app en 2.º plano.
 */
class RadioService : Service() {
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching { com.syemape.radio.data.RadioManager.stop() } // corta socket + audio
            releaseWakeLock()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        // Botón "Hablar"/"Cortar" de la notificación: transmite en el CANAL ACTUAL.
        // Es un toggle (la notificación no permite mantener pulsado): 1er toque empieza,
        // 2.º corta. Reutiliza exactamente el mismo camino PTT que el botón de la app.
        if (intent?.action == ACTION_TALK) {
            val rm = com.syemape.radio.data.RadioManager
            when {
                rm.channelId == null ->
                    android.widget.Toast.makeText(this, "Entra a un canal para hablar", android.widget.Toast.LENGTH_SHORT).show()
                rm.talking -> rm.stopTalking()
                rm.remoteSpeaking ->
                    android.widget.Toast.makeText(this, "Espera, alguien está hablando", android.widget.Toast.LENGTH_SHORT).show()
                else -> rm.startTalking()
            }
            refresh(this) // re-postea la notificación con el estado nuevo (Hablar/Cortar)
            return START_STICKY
        }
        val channel = intent?.getStringExtra(EXTRA_CHANNEL)
            ?: com.syemape.radio.data.RadioManager.channelName
        startForegroundCompat(channel)
        acquireWakeLock()
        // Si el sistema revivió el proceso (START_STICKY) sin abrir la UI —p.ej.
        // tras cerrar la app desde Recientes o tras matar el proceso— re-arranca
        // el motor de radio para seguir escuchando. start() es idempotente; solo
        // si hay sesión (si no, cierra el servicio para no quedar colgado).
        if (com.syemape.radio.data.Backend.tokens.accessToken != null) {
            runCatching { com.syemape.radio.data.RadioManager.start(application) }
        } else {
            releaseWakeLock()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY // se reinicia si el sistema lo mata
    }

    /** Mantiene la radio viva aunque el usuario cierre la app desde Recientes. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // No detenemos el servicio: la radio sigue escuchando en 2º plano.
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "mape:radio").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun startForegroundCompat(channelText: String) {
        ensureChannel(this)
        val notif = buildNotification(this, channelText)
        // Robusto en todos los celulares: el tipo "micrófono" en Android 14+ (targetSdk alto)
        // puede rechazarse si falta el permiso RECORD_AUDIO o si se intenta desde 2.º plano;
        // en ese caso caemos a "reproducción multimedia", y si aún falla, sin tipo. Nunca
        // dejamos que un fallo al promover el servicio tumbe la app.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                val primaryType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                runCatching { startForeground(NOTIF_ID, notif, primaryType) }
                    .recoverCatching { startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
                    .recoverCatching { startForeground(NOTIF_ID, notif) }
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // Último recurso: no crashear. La radio sigue funcionando en primer plano.
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    companion object {
        const val NOTIF_ID = 4201
        const val CHANNEL_ID = "mape_radio"
        const val EXTRA_CHANNEL = "channel"
        const val ACTION_STOP = "com.syemape.radio.STOP_RADIO"
        const val ACTION_TALK = "com.syemape.radio.TALK_RADIO"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(CHANNEL_ID, "Radio", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                        setSound(null, null)
                    }
                    nm.createNotificationChannel(ch)
                }
            }
        }

        private fun buildNotification(context: Context, channelText: String): Notification {
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pi = android.app.PendingIntent.getActivity(
                context, 0, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val logo = runCatching {
                android.graphics.BitmapFactory.decodeResource(context.resources, com.syemape.radio.R.drawable.app_logo)
            }.getOrNull()
            val stopIntent = Intent(context, RadioService::class.java).setAction(ACTION_STOP)
            val stopPi = android.app.PendingIntent.getService(
                context, 1, stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            // Botón PTT en la notificación (toggle): habla en el canal actual.
            val talkIntent = Intent(context, RadioService::class.java).setAction(ACTION_TALK)
            val talkPi = android.app.PendingIntent.getService(
                context, 2, talkIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val talking = com.syemape.radio.data.RadioManager.talking
            val remoteSpeaking = com.syemape.radio.data.RadioManager.remoteSpeaking
            val speaker = com.syemape.radio.data.RadioManager.speakerLabel?.takeIf { it.isNotBlank() }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.syemape.radio.R.drawable.ic_stat_radio)
                .setColor(0xFFE5322D.toInt())
                .apply { if (logo != null) setLargeIcon(logo) }
                .setContentTitle(channelText)
                .setContentText(
                    when {
                        talking -> "🔴 Transmitiendo… toca Cortar para terminar"
                        remoteSpeaking -> "🔊 ${speaker ?: "Alguien"} está hablando"
                        else -> "Radio en vivo · toca Hablar para transmitir"
                    },
                )
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .addAction(
                    com.syemape.radio.R.drawable.ic_stat_radio,
                    when {
                        talking -> "Cortar"
                        remoteSpeaking -> "Espera"
                        else -> "Hablar"
                    },
                    talkPi,
                )
                .addAction(com.syemape.radio.R.drawable.ic_stat_radio, "Desconectar", stopPi)
                .build()
        }

        /** Re-postea la notificación con el estado actual (p.ej. Hablar ↔ Cortar). */
        fun refresh(context: Context?) {
            context ?: return
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching {
                nm.notify(NOTIF_ID, buildNotification(context, com.syemape.radio.data.RadioManager.channelName))
            }
        }

        fun start(context: Context?, channelText: String) {
            context ?: return
            val intent = Intent(context, RadioService::class.java).putExtra(EXTRA_CHANNEL, channelText)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun update(context: Context?, channelText: String) = start(context, channelText)

        fun stop(context: Context?) {
            context ?: return
            runCatching { context.stopService(Intent(context, RadioService::class.java)) }
        }
    }
}
