package com.syemape.radio.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

enum class AuthStatus { Loading, Authenticated, Unauthenticated }

/** Estado de sesión observable por Compose + operaciones de auth. */
object SessionManager {
    var status by mutableStateOf(AuthStatus.Loading)
        private set
    var user by mutableStateOf<AuthUser?>(null)
        private set

    fun updateUser(updated: AuthUser) {
        user = updated
        Backend.tokens.saveUser(updated)
    }

    /** Restaura sesión persistida al arrancar. */
    fun bootstrap() {
        val t = Backend.tokens
        if (t.accessToken != null && t.refreshToken != null) {
            user = t.loadUser()
            status = AuthStatus.Authenticated
        } else {
            status = AuthStatus.Unauthenticated
        }
    }

    suspend fun login(identifier: String, password: String): Result<Unit> = try {
        val session = Backend.api.login(LoginRequest(identifier.trim(), password))
        Backend.tokens.save(session)
        user = session.user
        status = AuthStatus.Authenticated
        Result.success(Unit)
    } catch (e: HttpException) {
        Result.failure(ApiException(e.code(), messageFromHttp(e)))
    } catch (e: IOException) {
        Result.failure(ApiException(0, "No se pudo conectar con el servidor. Revisa tu conexión."))
    } catch (e: Exception) {
        Result.failure(ApiException(-1, e.message ?: "Error inesperado"))
    }

    suspend fun logout() {
        runCatching { Backend.api.logout() }
        runCatching { RadioManager.stop() }
        runCatching { TrackingManager.stop() }
        runCatching { AppBadges.reset() }
        runCatching { Realtime.closeAll() }
        Backend.tokens.clear()
        user = null
        status = AuthStatus.Unauthenticated
    }

    /** Limpia también el estado visible cuando el Authenticator no puede renovar la sesión. */
    fun expireLocalSession() {
        Backend.tokens.clear()
        runCatching { RadioManager.stop() }
        runCatching { TrackingManager.stop() }
        runCatching { AppBadges.reset() }
        runCatching { Realtime.closeAll() }
        user = null
        status = AuthStatus.Unauthenticated
    }

    private fun messageFromHttp(e: HttpException): String {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull() ?: return defaultFor(e.code())
        return runCatching {
            val el = JsonParser.parseString(raw)
            if (el.isJsonObject) {
                val msg = el.asJsonObject.get("message")
                when {
                    msg == null -> defaultFor(e.code())
                    msg.isJsonArray -> msg.asJsonArray.joinToString(", ") { it.asString }
                    else -> msg.asString
                }
            } else defaultFor(e.code())
        }.getOrElse { defaultFor(e.code()) }
    }

    private fun defaultFor(code: Int): String = when (code) {
        401 -> "Credenciales incorrectas"
        in 500..599 -> "Error del servidor, intenta más tarde"
        else -> "No se pudo iniciar sesión (error $code)"
    }
}
