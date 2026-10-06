package com.syemape.radio.data

/** Usuario autenticado devuelto por el backend. Campos opcionales por robustez. */
data class AuthUser(
    val id: String = "",
    val name: String? = null,
    val email: String? = null,
    val dni: String? = null,
    val operatorCode: String? = null,
    val role: String? = null,            // ADMIN | SUPERVISOR | OPERADOR
    val nickname: String? = null,
    val avatarKey: String? = null,
    val photoUrl: String? = null,
    val positionTitle: String? = null,
    val phone: String? = null,
    val shift: String? = null,           // MANANA | TARDE | NOCHE
    val isActive: Boolean = true,
    val isOnline: Boolean = false,
)

data class PersonalSyncResult(
    val fetched: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val deactivated: Int = 0,
    val errors: List<String> = emptyList(),
)

data class UpdateUserRequest(
    val role: String? = null,
    val isActive: Boolean? = null,
)

/** Respuesta de /auth/login y /auth/refresh. */
data class Session(
    val accessToken: String = "",
    val refreshToken: String = "",
    val user: AuthUser = AuthUser(),
)

data class LoginRequest(val identifier: String, val password: String)
data class RefreshRequest(val refreshToken: String)

/** Error de API con mensaje legible. */
class ApiException(val status: Int, message: String) : Exception(message)

// ---- Datos de dominio ----

data class UnitsSummary(val total: Int = 0, val enRuta: Int = 0, val detenidos: Int = 0)

data class AlertMetrics(val criticas: Int = 0, val pendientes: Int = 0, val hoy: Int = 0)

/** Persona conectada con ubicación (tracking/people y presence:update). */
data class LivePerson(
    val id: String = "",
    val name: String? = null,
    val nickname: String? = null,
    val avatarKey: String? = null,
    val role: String? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastSpeedKmh: Double? = null,
    val lastHeading: Double? = null,
    val lastPositionAt: String? = null,
)

data class MiniUser(
    val id: String = "",
    val name: String? = null,
    val nickname: String? = null,
    val avatarKey: String? = null,
    val isOnline: Boolean = false,
)

data class MiniUnit(val id: String = "", val code: String? = null, val plate: String? = null)

/** Unidad en vivo (tracking/live). */
data class LiveUnit(
    val id: String = "",
    val code: String? = null,
    val status: String? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastSpeedKmh: Double? = null,
    val lastHeading: Double? = null,
    val lastPositionAt: String? = null,
    val operator: MiniUser? = null,
)

data class Alert(
    val id: String = "",
    val type: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val title: String = "",
    val description: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val locationLabel: String? = null,
    val createdAt: String? = null,
    val unit: MiniUnit? = null,
    val operator: MiniUser? = null,
)

data class Message(
    val id: String = "",
    val conversationId: String? = null,
    val senderId: String? = null,
    val type: String? = "TEXT",
    val body: String? = null,
    val attachmentKey: String? = null,
    val durationSec: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val locationLabel: String? = null,
    val createdAt: String? = null,
    val sender: MiniUser? = null,
)

data class ConvMember(val user: MiniUser? = null)

data class Conversation(
    val id: String = "",
    val type: String? = null,
    val title: String? = null,
    val lastMessageAt: String? = null,
    val unread: Int = 0,
    val members: List<ConvMember> = emptyList(),
    val lastMessage: Message? = null,
)

data class ConversationDetail(
    val conversation: Conversation = Conversation(),
    val messages: List<Message> = emptyList(),
)

data class SendMessageRequest(
    val type: String = "TEXT",
    val body: String? = null,
)

data class AppSetting(
    val refreshIntervalSec: Int = 15,
    val liveLocation: Boolean = true,
    val transitMap: Boolean = true,
    val radioSound: Boolean = true,
    val criticalAlerts: Boolean = true,
    val darkMode: Boolean = false,
)

data class NotificationPref(
    val criticalAlerts: Boolean = true,
    val chatMessages: Boolean = true,
    val radioBroadcasts: Boolean = true,
    val unitStatus: Boolean = true,
    val dailyDigest: Boolean = false,
    val soundVibration: Boolean = true,
    val doNotDisturb: Boolean = false,
)

data class UpdateProfileRequest(
    val name: String? = null,
    val nickname: String? = null,
    val positionTitle: String? = null,
    val phone: String? = null,
)

data class ReportPositionRequest(
    val lat: Double,
    val lng: Double,
    val speedKmh: Double? = null,
    val heading: Double? = null,
    val accuracy: Double? = null,
)

data class RadioChannel(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val memberCount: Int = 0,
    val joined: Boolean = false,
)

data class CreateRadioChannelRequest(
    val name: String,
    val type: String = "OPERACIONES",
    val description: String? = null,
)

/** Transmisión del chat de un canal de radio (voz, imagen o texto). */
data class RadioTransmission(
    val id: String = "",
    val channelId: String? = null,
    val senderId: String? = null,
    val text: String? = null,
    val audioKey: String? = null,
    val imageKey: String? = null,
    val videoKey: String? = null,
    val fileKey: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val durationSec: Double? = null,
    val createdAt: String? = null,
    val sender: MiniUser? = null,
)

/** Respuesta de `POST /api/media/upload`. */
data class MediaUpload(
    val key: String = "",
    val url: String = "",
    val mime: String? = null,
    val size: Long? = null,
)

/** Resultado de `GET /api/maps/directions` (proxy a Google Directions). */
data class DirectionsResult(
    val ok: Boolean = false,
    val distanceText: String? = null,
    val distanceMeters: Int? = null,
    val durationText: String? = null,
    val durationSeconds: Int? = null,
    val overviewPolyline: String? = null,
    val endAddress: String? = null,
    val steps: List<DirectionsStep> = emptyList(),
)

data class DirectionsStep(
    val instruction: String = "",
    val distanceText: String? = null,
    val distanceMeters: Int? = null,
    val durationText: String? = null,
    val polyline: String? = null,
    val maneuver: String? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
)

/** Posición de unidad recibida en vivo (position:update). */
data class UnitPosition(
    val unitId: String = "",
    val code: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val speedKmh: Double? = null,
    val heading: Double? = null,
    val status: String? = null,
    val operator: MiniUser? = null,
    val recordedAt: String? = null,
)
