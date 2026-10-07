package com.syemape.radio.data

import android.content.Context
import android.content.SharedPreferences

/** Preferencias de UI persistentes: última pestaña, canal de radio y salida de audio. */
object Prefs {
    private var p: SharedPreferences? = null

    fun init(context: Context) {
        if (p == null) p = context.applicationContext.getSharedPreferences("mape_prefs", Context.MODE_PRIVATE)
    }

    var lastTab: String?
        get() = p?.getString("lastTab", null)
        set(v) { p?.edit()?.putString("lastTab", v)?.apply() }

    var lastChannelId: String?
        get() = p?.getString("lastChannelId", null)
        set(v) { p?.edit()?.putString("lastChannelId", v)?.apply() }

    /** Nombre del último canal (para mostrarlo al instante sin esperar la lista REST). */
    var lastChannelName: String?
        get() = p?.getString("lastChannelName", null)
        set(v) { p?.edit()?.putString("lastChannelName", v)?.apply() }

    var speakerOn: Boolean
        get() = p?.getBoolean("speakerOn", true) ?: true
        set(v) { p?.edit()?.putBoolean("speakerOn", v)?.apply() }

    var darkMode: Boolean
        // The main app screens follow Radio's dark monochrome appearance by default.
        get() = p?.getBoolean("darkMode", true) ?: true
        set(v) { p?.edit()?.putBoolean("darkMode", v)?.apply() }

    /** Preferencia de sonido de radio; null significa que aún no se sincronizó con ajustes del backend. */
    var radioSound: Boolean?
        get() = if (p?.contains("radioSound") == true) p?.getBoolean("radioSound", true) else null
        set(v) {
            val editor = p?.edit() ?: return
            if (v == null) editor.remove("radioSound") else editor.putBoolean("radioSound", v)
            editor.apply()
        }

    /** Volumen de la radio (0..1) sobre STREAM_VOICE_CALL. Por defecto al máximo. */
    var callVolume: Float
        get() = p?.getFloat("callVolume", 1f) ?: 1f
        set(v) { p?.edit()?.putFloat("callVolume", v)?.apply() }

    /** Ya se pidió una vez la exención de optimización de batería (no volver a molestar). */
    var batteryOptAsked: Boolean
        get() = p?.getBoolean("batteryOptAsked", false) ?: false
        set(v) { p?.edit()?.putBoolean("batteryOptAsked", v)?.apply() }
}
