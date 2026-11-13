package com.syemape.radio.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Detiene la sirena cuando se confirma, se cierra o se descarta su notificación. */
class EmergencyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ALERT_ID) ?: return
        Notifier.stopEmergency(id, context)
        if (intent.action == ACTION_CONFIRM) {
            android.widget.Toast.makeText(context, "Emergencia confirmada", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_ALERT_ID = "emergency_alert_id"
        const val ACTION_CONFIRM = "com.syemape.radio.EMERGENCY_CONFIRM"
        const val ACTION_DISMISS = "com.syemape.radio.EMERGENCY_DISMISS"
    }
}
