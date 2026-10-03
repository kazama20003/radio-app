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
        installCrashGuard()
        com.syemape.radio.data.Backend.init(this)
        com.syemape.radio.data.Prefs.init(this)
        com.syemape.radio.data.Notifier.init(this)
        com.syemape.radio.data.SessionManager.bootstrap()
    }

    /**
     * Red de seguridad: una excepción no capturada en un HILO DE FONDO (callbacks de
     * Socket.IO/WebRTC) cerraría la app. Aquí la registramos y la tragamos para que la
     * radio siga viva. En el hilo principal dejamos el manejo normal (no es seguro seguir).
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            android.util.Log.e("RadioApp", "Excepción no capturada en hilo '${thread.name}'", ex)
            if (thread === android.os.Looper.getMainLooper().thread) {
                previous?.uncaughtException(thread, ex)
            }
            // Hilos de fondo: no re-lanzamos → la app NO se cierra.
        }
    }
}
