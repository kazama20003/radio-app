package com.syemape.radio.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    /** Fecha estable para agrupar los mensajes del chat por día. */
    fun dayKey(iso: String?): String = instantOf(iso)?.atZone(zone)?.toLocalDate()?.toString() ?: "sin-fecha"

    /** Encabezado de fecha para el historial: Hoy/Ayer o fecha completa en español. */
    fun dayLabel(iso: String?): String {
        val date = instantOf(iso)?.atZone(zone)?.toLocalDate() ?: return "Fecha no disponible"
        val today = LocalDate.now(zone)
        val spanish = Locale.forLanguageTag("es-PE")
        val formatted = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", spanish)
            .format(date).replaceFirstChar { it.uppercase(spanish) }
        return when (date) {
            today -> "HOY · $formatted"
            today.minusDays(1) -> "AYER · $formatted"
            else -> formatted
        }
    }

    /** Hora local exacta en formato de 24 horas. */
    fun clockTime(iso: String?): String = instantOf(iso)?.let {
        DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-PE")).format(it.atZone(zone))
    } ?: "--:--"

    /** Fecha y hora local exacta para ubicar cuándo llegó la última posición. */
    fun dateTime(iso: String?): String = instantOf(iso)?.let {
        DateTimeFormatter.ofPattern("dd/MM/yyyy '·' HH:mm", Locale.forLanguageTag("es-PE"))
            .format(it.atZone(zone))
    } ?: ""

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
