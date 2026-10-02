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

    var speakerOn: Boolean
        get() = p?.getBoolean("speakerOn", true) ?: true
        set(v) { p?.edit()?.putBoolean("speakerOn", v)?.apply() }

    /** Volumen de la radio (0..1) sobre STREAM_VOICE_CALL. Por defecto al máximo. */
    var callVolume: Float
        get() = p?.getFloat("callVolume", 1f) ?: 1f
        set(v) { p?.edit()?.putFloat("callVolume", v)?.apply() }

    /** Ya se pidió una vez la exención de optimización de batería (no volver a molestar). */
    var batteryOptAsked: Boolean
        get() = p?.getBoolean("batteryOptAsked", false) ?: false
        set(v) { p?.edit()?.putBoolean("batteryOptAsked", v)?.apply() }
}
