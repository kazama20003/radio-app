package com.syemape.radio.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.syemape.radio.EmergencyAlertActivity
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
    // Nuevo ID porque Android no deja modificar sonido/vibración de canales existentes.
    private const val CH_PRIVATE_CALL = "mape_private_calls_v3"
    private const val CH_PRIVATE_ACTIVE = "mape_private_active_v2"
    // v3 deja el canal sin tono. La sirena se reproduce aparte, sin pedir foco de audio.
    private const val CH_EMERGENCY = "mape_radio_emergency_v3"
    private val EMERGENCY_VIBRATION = longArrayOf(0, 220, 100, 650, 140, 220, 100, 650)
    private val RED = 0xFFE5322D.toInt()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val emergencyAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    @Volatile private var emergencySirenPlayer: MediaPlayer? = null
    @Volatile private var emergencySoundGeneration = 0

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

        if (nm.getNotificationChannel(CH_PRIVATE_CALL) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_PRIVATE_CALL, "Llamadas de radio privada", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Timbre y vibración para llamadas entrantes de radio privada"
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val soundAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(sound, soundAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 850, 250, 850, 250, 850, 250, 850)
                enableLights(true); lightColor = RED
            })
        }
        if (nm.getNotificationChannel(CH_PRIVATE_ACTIVE) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_PRIVATE_ACTIVE, "Radio privada activa", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Controles silenciosos de una transmisión privada activa"
                setSound(null, null)
                enableVibration(false)
            })
        }
        if (nm.getNotificationChannel(CH_EMERGENCY) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_EMERGENCY, "Emergencias de radio", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Vibración urgente; la sirena se controla con la alerta"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = EMERGENCY_VIBRATION
                enableLights(true)
                lightColor = RED
            })
        }

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

    fun notifyPrivateCall(callId: String, callerName: String) {
        val ctx = appContext ?: return
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        fun actionIntent(accept: Boolean): PendingIntent {
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?.putExtra("private_call_action", if (accept) "answer" else "decline")
                ?.putExtra("private_call_id", callId)
            return PendingIntent.getActivity(ctx, (callId + accept).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(ctx, CH_PRIVATE_CALL)
            .setSmallIcon(R.drawable.ic_stat_alert).setColor(RED)
            .setContentTitle("Llamada de radio privada")
            .setContentText("$callerName quiere hablar contigo")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$callerName te está llamando por radio privada"))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false)
            .setContentIntent(openAppIntent())
            .addAction(0, "Rechazar", actionIntent(false))
            .addAction(0, "Contestar", actionIntent(true)).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(callId.hashCode(), notification) }
    }

    fun notifyActivePrivateCall(callId: String, peerName: String) {
        val ctx = appContext ?: return
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        runCatching { NotificationManagerCompat.from(ctx).cancel(callId.hashCode()) }
        fun intent(action: String): PendingIntent {
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?.putExtra("private_call_action", action)
                ?.putExtra("private_call_id", callId)
            return PendingIntent.getActivity(ctx, (callId + action).hashCode(), launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(ctx, CH_PRIVATE_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_alert).setColor(RED)
            .setContentTitle("Radio privada activa")
            .setContentText("Con $peerName · toca para volver a la transmisión")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false)
            .setContentIntent(intent("open"))
            .addAction(0, "Volver", intent("open"))
            .addAction(0, "Colgar", intent("hangup"))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(activePrivateCallId(callId), notification) }
    }

    fun cancelPrivateCall(callId: String) {
        appContext?.let {
            runCatching { NotificationManagerCompat.from(it).cancel(callId.hashCode()) }
            runCatching { NotificationManagerCompat.from(it).cancel(activePrivateCallId(callId)) }
        }
    }

    private fun activePrivateCallId(callId: String) = callId.hashCode() xor 0x4D415045

    private fun emergencyActionIntent(id: String, action: String): PendingIntent {
        val ctx = appContext ?: error("Notifier no está inicializado")
        val intent = Intent(ctx, EmergencyAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EmergencyAlarmReceiver.EXTRA_ALERT_ID, id)
        return PendingIntent.getBroadcast(
            ctx,
            (id + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Sirena en bucle por el canal de alarma; no solicita foco ni cambia el volumen de la radio. */
    private fun startEmergencyAlarm() {
        val ctx = appContext ?: return
        val generation = ++emergencySoundGeneration
        mainHandler.post {
            releaseEmergencyPlayers()
            startSirenLoop(ctx, generation)
        }
    }

    private fun startSirenLoop(ctx: Context, generation: Int) {
        if (generation != emergencySoundGeneration) return
        val siren = createEmergencyPlayer(ctx, R.raw.emergency_siren) ?: return
        emergencySirenPlayer = siren
        runCatching {
            siren.isLooping = true
            siren.setOnPreparedListener { prepared ->
                if (generation == emergencySoundGeneration) prepared.start() else prepared.release()
            }
            siren.prepareAsync()
        }.onFailure {
            runCatching { siren.release() }
            if (emergencySirenPlayer === siren) emergencySirenPlayer = null
        }
    }

    private fun createEmergencyPlayer(ctx: Context, resourceId: Int): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(emergencyAudioAttributes)
            setVolume(1f, 1f)
            ctx.resources.openRawResourceFd(resourceId).use { descriptor ->
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
        }
    }.getOrNull()

    private fun releaseEmergencyPlayers() {
        emergencySirenPlayer?.let { player ->
            runCatching { player.setOnCompletionListener(null); player.stop() }
            runCatching { player.release() }
        }
        emergencySirenPlayer = null
    }

    /** Detener al confirmar/cerrar la alerta o tocar su notificación. */
    fun stopEmergency(id: String, context: Context? = appContext) {
        emergencySoundGeneration++
        mainHandler.post { releaseEmergencyPlayers() }
        context?.let { ctx ->
            runCatching { NotificationManagerCompat.from(ctx).cancel(id.hashCode()) }
        }
    }

    /** Sirena continua y diálogo de confirmación en pantalla. */
    fun notifyEmergency(id: String, senderName: String, channelName: String) {
        val ctx = appContext ?: return
        val screenIntent = emergencyActivityIntent(id, senderName, channelName)
        if (com.syemape.radio.MainActivity.isForeground || EmergencyAlertActivity.isForeground) {
            startEmergencyAlarm()
            val opened = runCatching {
                ctx.startActivity(screenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }.isSuccess
            if (opened) {
                vibrateEmergency()
                return
            }
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        startEmergencyAlarm()
        val alertIntent = PendingIntent.getActivity(
            ctx, ("emergency-open" + id).hashCode(), screenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "Solicita apoyo en $channelName"
        val notification = NotificationCompat.Builder(ctx, CH_EMERGENCY)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(RED)
            .setContentTitle("🚨 EMERGENCIA · $senderName")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(alertIntent)
            .setFullScreenIntent(alertIntent, true)
            .setDeleteIntent(emergencyActionIntent(id, EmergencyAlarmReceiver.ACTION_DISMISS))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id.hashCode(), notification) }
    }

    private fun emergencyActivityIntent(id: String, senderName: String, channelName: String): Intent {
        val ctx = appContext ?: error("Notifier no está inicializado")
        return Intent(ctx, EmergencyAlertActivity::class.java)
            .putExtra(EmergencyAlarmReceiver.EXTRA_ALERT_ID, id)
            .putExtra(EmergencyAlertActivity.EXTRA_SENDER_NAME, senderName)
            .putExtra(EmergencyAlertActivity.EXTRA_CHANNEL_NAME, channelName)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** Confirmación háptica local para quien pulsa el botón de emergencia. */
    fun vibrateEmergency() {
        val ctx = appContext ?: return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(EMERGENCY_VIBRATION, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(EMERGENCY_VIBRATION, -1)
            }
        }
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
