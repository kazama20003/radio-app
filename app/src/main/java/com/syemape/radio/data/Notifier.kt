package com.syemape.radio.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.syemape.radio.R

/**
 * Notificaciones del sistema (barra de estado) para eventos de la flota.
 * Por ahora cubre ALERTAS, que llegan globalmente por Socket.IO (/alerts).
 *
 * Para no pisar la voz de la radio: si la radio está activa (en un canal), las
 * alertas se muestran igual (heads-up + vibración para las críticas) pero SIN
 * sonido, de modo que Android no baje el volumen (duck) del audio de la radio.
 * Si la radio no está activa, suenan normal. Todo con la marca Mape (rojo).
 */
object Notifier {
    private const val CH_ALERT_CRIT = "mape_alerts_critical"         // crítica, con sonido
    private const val CH_ALERT_CRIT_QUIET = "mape_alerts_critical_quiet" // crítica, sin sonido (radio activa)
    private const val CH_ALERT = "mape_alerts"                       // normal, con sonido
    private const val CH_ALERT_QUIET = "mape_alerts_quiet"           // normal, sin sonido (radio activa)
    private val RED = 0xFFE5322D.toInt()

    private var appContext: Context? = null

    /** Llamar una vez con el applicationContext (p. ej. en MainActivity.onCreate). */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        ensureChannels()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Crítica con sonido (heads-up + sonido + vibración + luz).
        if (nm.getNotificationChannel(CH_ALERT_CRIT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERT_CRIT, "Alertas críticas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alertas urgentes de la flota"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                    enableLights(true)
                    lightColor = RED
                },
            )
        }
        // Crítica SIN sonido: sigue saltando como heads-up + vibración, pero no
        // reproduce sonido para no bajar el volumen de la radio en curso.
        if (nm.getNotificationChannel(CH_ALERT_CRIT_QUIET) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERT_CRIT_QUIET, "Alertas críticas (radio activa)", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alertas urgentes mientras la radio está activa (sin sonido)"
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                    enableLights(true)
                    lightColor = RED
                },
            )
        }
        // Normal con sonido.
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERT, "Alertas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alertas de la flota"
                },
            )
        }
        // Normal SIN sonido (silenciosa en la bandeja) para cuando la radio está activa.
        if (nm.getNotificationChannel(CH_ALERT_QUIET) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERT_QUIET, "Alertas (radio activa)", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Alertas mientras la radio está activa (sin sonido)"
                    setSound(null, null)
                },
            )
        }
    }

    private fun openAppIntent(): PendingIntent? {
        val ctx = appContext ?: return null
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Lanza (o actualiza) la notificación de una alerta recibida en vivo. */
    fun notifyAlert(a: Alert) {
        val ctx = appContext ?: return
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val critical = a.severity == "CRITICA"
        // Si la radio está en un canal, usamos los canales sin sonido para no
        // pisar la voz (ni transmitida ni recibida).
        val radioActive = RadioManager.connected
        val channel = when {
            critical && radioActive -> CH_ALERT_CRIT_QUIET
            critical -> CH_ALERT_CRIT
            radioActive -> CH_ALERT_QUIET
            else -> CH_ALERT
        }
        val title = a.title.ifBlank { "Alerta" }
        val who = a.operator?.name ?: "Unidad"
        val code = a.unit?.code.orEmpty()
        val head = listOf(who, code).filter { it.isNotBlank() }.joinToString(" · ")
        val body = buildString {
            append(head)
            if (!a.description.isNullOrBlank()) append(if (isEmpty()) a.description else " — ${a.description}")
            if (!a.locationLabel.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append("📍 ${a.locationLabel}")
            }
        }.ifBlank { "Nueva alerta" }

        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(RED)
            .setContentTitle(if (critical) "⚠ $title" else title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())

        val id = a.id.ifBlank { title }.hashCode()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, builder.build()) }
    }
}
