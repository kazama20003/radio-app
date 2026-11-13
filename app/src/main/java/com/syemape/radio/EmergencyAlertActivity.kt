package com.syemape.radio

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.EmergencyAlarmReceiver
import com.syemape.radio.data.Notifier
import com.syemape.radio.ui.theme.MapeTheme
import com.syemape.radio.ui.theme.Outfit

/** Pantalla de confirmación para la sirena de emergencia, incluso con el teléfono bloqueado. */
class EmergencyAlertActivity : ComponentActivity() {
    private var alertId by mutableStateOf("")
    private var senderName by mutableStateOf("Un operador")
    private var channelName by mutableStateOf("Radio")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.init(applicationContext)
        readAlert(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MapeTheme(darkMode = true) {
                Column(
                    Modifier.fillMaxSize().background(Color(0x99000000)),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AlertDialog(
                        onDismissRequest = { finishAlert() },
                        title = {
                            Text("EMERGENCIA SOS", color = Color(0xFFD71920), fontFamily = Outfit, fontWeight = FontWeight.Black, fontSize = 21.sp)
                        },
                        text = {
                            Text(
                                "$senderName solicita apoyo en $channelName. Confirma que recibiste la alerta para detener la sirena.",
                                color = Color.White,
                                fontFamily = Outfit,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { finishAlert() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71920)),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("CONFIRMAR SOS", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { finishAlert() }) {
                                Text("Cerrar alerta", color = Color.LightGray, fontFamily = Outfit)
                            }
                        },
                        containerColor = Color(0xFF191919),
                        titleContentColor = Color(0xFFD71920),
                        textContentColor = Color.White,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
    }

    override fun onPause() {
        isForeground = false
        super.onPause()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readAlert(intent)
    }

    override fun onBackPressed() {
        finishAlert()
    }

    private fun readAlert(intent: android.content.Intent?) {
        alertId = intent?.getStringExtra(EmergencyAlarmReceiver.EXTRA_ALERT_ID).orEmpty()
        senderName = intent?.getStringExtra(EXTRA_SENDER_NAME)?.ifBlank { null } ?: "Un operador"
        channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME)?.ifBlank { null } ?: "Radio"
    }

    private fun finishAlert() {
        if (alertId.isNotBlank()) Notifier.stopEmergency(alertId, this)
        finish()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations && alertId.isNotBlank()) Notifier.stopEmergency(alertId, this)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SENDER_NAME = "emergency_sender_name"
        const val EXTRA_CHANNEL_NAME = "emergency_channel_name"
        @Volatile var isForeground: Boolean = false
            private set
    }
}
