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
}
