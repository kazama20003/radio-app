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
        val channel = intent?.getStringExtra(EXTRA_CHANNEL) ?: "Canal"
        startForegroundCompat(channel)
        acquireWakeLock()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else 0
            runCatching { startForeground(NOTIF_ID, notif, type) }
                .onFailure { startForeground(NOTIF_ID, notif) }
        } else {
            startForeground(NOTIF_ID, notif)
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
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.syemape.radio.R.drawable.ic_stat_radio)
                .setColor(0xFFE5322D.toInt())
                .apply { if (logo != null) setLargeIcon(logo) }
                .setContentTitle(channelText)
                .setContentText("Radio en vivo · tu equipo te escucha")
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .addAction(com.syemape.radio.R.drawable.ic_stat_radio, "Desconectar", stopPi)
                .build()
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
