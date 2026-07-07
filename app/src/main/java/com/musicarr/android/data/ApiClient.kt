package com.musicarr.android.data

import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private const val SESSION_COOKIE = "musicarr_session"

/**
 * Persists only the Musicarr session cookie (the only cookie the server sets)
 * and replays it on every request, so both Retrofit calls and ExoPlayer's
 * stream requests stay signed in across app restarts.
 */
private class SessionCookieJar(private val session: SessionManager) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.firstOrNull { it.name == SESSION_COOKIE }?.let { c ->
            if (c.value != session.sessionCookie) session.updateCookie(c.value)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val token = session.sessionCookie
        if (token.isEmpty()) return emptyList()
        return listOf(
            Cookie.Builder().name(SESSION_COOKIE).value(token).domain(url.host).path("/").build()
        )
    }
}

class ApiClient(private val session: SessionManager) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    /** Shared by Retrofit and the playback service's OkHttpDataSource. */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar(session))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile private var retrofitUrl: String? = null
    @Volatile private var apiInstance: MusicarrApi? = null

    /** (Re)built lazily; a changed server URL invalidates the cached instance. */
    val api: MusicarrApi
        get() {
            val base = session.baseUrl
            val cached = apiInstance
            if (cached != null && retrofitUrl == base) return cached
            synchronized(this) {
                if (apiInstance == null || retrofitUrl != base) {
                    apiInstance = Retrofit.Builder()
                        .baseUrl(base)
                        .client(okHttp)
                        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                        .build()
                        .create(MusicarrApi::class.java)
                    retrofitUrl = base
                }
                return apiInstance!!
            }
        }

    /** Absolute streaming URL for a track in the shared library. */
    fun streamUrl(trackId: Long): String = "${session.baseUrl}api/stream/$trackId"

    /** Turn transport/HTTP errors into a message worth showing the user. */
    fun errorMessage(e: Throwable): String = when (e) {
        is HttpException -> {
            val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val apiError = body?.let {
                try { json.decodeFromString<ApiError>(it).error } catch (_: Exception) { null }
            }
            apiError ?: "Server error (${e.code()})"
        }
        is java.net.UnknownHostException -> "Can't reach the server — check the URL"
        is java.net.ConnectException -> "Connection refused — is the server up?"
        is java.net.SocketTimeoutException -> "The server took too long to respond"
        else -> e.message ?: "Something went wrong"
    }
}
