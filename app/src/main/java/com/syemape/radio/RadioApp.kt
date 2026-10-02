package com.syemape.radio

import android.app.Application

/**
 * Application: inicializa la capa de datos en CUALQUIER arranque del proceso,
 * incluso cuando el sistema lo revive solo para el servicio (sin UI), p.ej. tras
 * cerrar la app desde Recientes o tras que el SO mate el proceso. Así la radio
 * puede reconectarse headless y seguir escuchando.
 */
class RadioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.syemape.radio.data.Backend.init(this)
        com.syemape.radio.data.Prefs.init(this)
        com.syemape.radio.data.Notifier.init(this)
        com.syemape.radio.data.SessionManager.bootstrap()
    }
}
