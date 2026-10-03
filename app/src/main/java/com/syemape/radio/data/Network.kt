package com.syemape.radio.data

import android.content.Context
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import com.syemape.radio.BuildConfig

/** Persistencia simple de la sesión (tokens + usuario) en SharedPreferences. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("mape_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    @Volatile var accessToken: String? = prefs.getString("access", null)
        private set
    @Volatile var refreshToken: String? = prefs.getString("refresh", null)
        private set

    fun save(session: Session) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        prefs.edit()
            .putString("access", session.accessToken)
            .putString("refresh", session.refreshToken)
            .putString("user", gson.toJson(session.user))
            .apply()
    }

    fun loadUser(): AuthUser? =
        prefs.getString("user", null)?.let { runCatching { gson.fromJson(it, AuthUser::class.java) }.getOrNull() }

    fun clear() {
        accessToken = null
        refreshToken = null
        prefs.edit().clear().apply()
    }
}

/** Retrofit síncrono solo para renovar el token dentro del Authenticator. */
private interface RefreshApi {
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequest): retrofit2.Call<Session>
}

/** Localizador de servicios de backend. Inicializar con [init] al arrancar la app. */
object Backend {
    lateinit var api: ApiService
        private set
    lateinit var tokens: TokenStore
        private set

    private var refreshApiRef: RefreshApi? = null

    /**
     * Renueva el access token usando el refresh token (bloqueante). Lo usa el socket
     * cuando el servidor lo rechaza por token vencido, para reconectar con uno nuevo
     * sin tener que cerrar sesión. Devuelve true si quedó un token válido.
     */
    fun refreshAccessToken(): Boolean {
        val rt = tokens.refreshToken ?: return false
        val api = refreshApiRef ?: return false
        val r = runCatching { api.refresh(RefreshRequest(rt)).execute() }.getOrNull()
        val body = r?.body()
        return if (r != null && r.isSuccessful && body != null) {
            tokens.save(body); true
        } else {
            false
        }
    }

    fun init(context: Context) {
        if (::api.isInitialized) return
        tokens = TokenStore(context.applicationContext)

        val baseUrl = BuildConfig.API_BASE_URL

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        val authHeader = Interceptor { chain ->
            val original = chain.request()
            val token = tokens.accessToken
            val req = if (token != null && original.header("Authorization") == null) {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            } else original
            chain.proceed(req)
        }

        // Cliente aislado (sin authenticator) para el refresh.
        val refreshClient = OkHttpClient.Builder().addInterceptor(logging).build()
        val refreshApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RefreshApi::class.java)
        refreshApiRef = refreshApi

        val refreshAuthenticator = Authenticator { _: Route?, response: Response ->
            // Evita bucles: si ya reintentamos, abandona.
            if (responseCount(response) >= 2) return@Authenticator null
            val rt = tokens.refreshToken ?: return@Authenticator null
            val newSession = synchronized(this) {
                // Puede que otro hilo ya renovara: valida contra el token usado.
                val used = response.request.header("Authorization")?.removePrefix("Bearer ")
                if (used != null && used != tokens.accessToken) {
                    Session(tokens.accessToken ?: "", tokens.refreshToken ?: "")
                } else {
                    val r = runCatching { refreshApi.refresh(RefreshRequest(rt)).execute() }.getOrNull()
                    val body = r?.body()
                    if (r != null && r.isSuccessful && body != null) {
                        tokens.save(body)
                        body
                    } else {
                        tokens.clear()
                        null
                    }
                }
            } ?: return@Authenticator null

            response.request.newBuilder()
                .header("Authorization", "Bearer ${newSession.accessToken}")
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authHeader)
            .addInterceptor(logging)
            .authenticator(refreshAuthenticator)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var count = 1
        while (r?.priorResponse != null) {
            count++
            r = r.priorResponse
        }
        return count
    }
}
