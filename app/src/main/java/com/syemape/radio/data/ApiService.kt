package com.syemape.radio.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Interfaz Retrofit del backend Mape (REST bajo /api). */
interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Session

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Session

    @POST("auth/logout")
    suspend fun logout()

    @GET("users/me")
    suspend fun me(): AuthUser

    @retrofit2.http.PATCH("users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): AuthUser

    @GET("users")
    suspend fun users(): List<AuthUser>

    @POST("users/sync-personal")
    suspend fun syncPersonal(): PersonalSyncResult

    @retrofit2.http.PATCH("users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: UpdateUserRequest): AuthUser

    @Multipart
    @POST("users/{id}/photo")
    suspend fun uploadUserPhoto(@Path("id") id: String, @Part file: MultipartBody.Part): AuthUser

    @GET("settings")
    suspend fun settings(): AppSetting

    @retrofit2.http.PATCH("settings")
    suspend fun updateSettings(@Body body: Map<String, @JvmSuppressWildcards Any?>): AppSetting

    @GET("notifications/preferences")
    suspend fun notifPrefs(): NotificationPref

    @retrofit2.http.PATCH("notifications/preferences")
    suspend fun updateNotifPrefs(@Body body: Map<String, @JvmSuppressWildcards Any?>): NotificationPref

    @GET("units/summary")
    suspend fun unitsSummary(): UnitsSummary

    @GET("tracking/live")
    suspend fun liveUnits(): List<LiveUnit>

    @GET("tracking/people")
    suspend fun livePeople(): List<LivePerson>

    @POST("tracking/me/position")
    suspend fun reportMyPosition(@Body body: ReportPositionRequest): LivePerson

    @GET("alerts")
    suspend fun alerts(): List<Alert>

    @GET("alerts/metrics")
    suspend fun alertMetrics(): AlertMetrics

    @PATCH("alerts/read-all")
    suspend fun markAlertsRead()

    @GET("radio/channels")
    suspend fun radioChannels(): List<RadioChannel>

    @GET("radio/channels/{id}/history")
    suspend fun radioHistory(
        @Path("id") id: String,
        @retrofit2.http.Query("limit") limit: Int = 50,
        @retrofit2.http.Query("before") before: String? = null,
    ): List<RadioTransmission>

    @GET("conversations")
    suspend fun conversations(): List<Conversation>

    @GET("conversations/{id}")
    suspend fun conversation(@Path("id") id: String): ConversationDetail

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body body: SendMessageRequest): Message

    @POST("conversations/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    /** Sube un archivo (imagen/video/documento) y devuelve su key en `/uploads`. */
    @Multipart
    @POST("media/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): MediaUpload

    /** Ruta de navegación por carretera hacia un punto (proxy a Google Directions). */
    @GET("maps/directions")
    suspend fun directions(
        @Query("originLat") originLat: Double,
        @Query("originLng") originLng: Double,
        @Query("destLat") destLat: Double,
        @Query("destLng") destLng: Double,
        @Query("mode") mode: String = "driving",
    ): DirectionsResult
}
