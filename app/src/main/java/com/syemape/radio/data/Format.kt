package com.syemape.radio.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Utilidades de formato de fecha/hora para la UI (zona local). */
object Fmt {
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun instantOf(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    /** Hora corta tipo lista de chats: "10:42", "Ayer" o "9/5". */
    fun shortTime(iso: String?): String {
        val inst = instantOf(iso) ?: return ""
        val d = inst.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when {
            d == today -> DateTimeFormatter.ofPattern("HH:mm").format(inst.atZone(zone))
            d == today.minusDays(1) -> "Ayer"
            else -> "${d.dayOfMonth}/${d.monthValue}"
        }
    }

    /** Antigüedad relativa: "ahora", "hace 4 min", "hace 2 h". */
    fun hace(iso: String?): String {
        val inst = instantOf(iso) ?: return ""
        val min = (Instant.now().toEpochMilli() - inst.toEpochMilli()) / 60000
        return when {
            min < 1 -> "ahora"
            min < 60 -> "hace $min min"
            min < 1440 -> "hace ${min / 60} h"
            else -> "hace ${min / 1440} d"
        }
    }

    /** Vista previa de un mensaje según su tipo. */
    fun preview(m: Message?): String = when (m?.type) {
        null -> "Sin mensajes aún"
        "VOICE" -> "🎤 Nota de voz"
        "IMAGE" -> "📷 Imagen"
        "VIDEO" -> "🎬 Video"
        "LOCATION" -> "📍 Ubicación"
        else -> m.body ?: ""
    }
}
