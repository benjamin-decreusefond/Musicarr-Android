package com.musicarr.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.dataStore by preferencesDataStore(name = "musicarr_session")

/**
 * Persists the server URL, the session cookie, and the signed-in username.
 * Values are also cached in memory so the network stack (interceptors, the
 * playback service's data source) can read them synchronously.
 */
class SessionManager(private val context: Context) : SessionCookieSource {
    // Process-lifetime scope for fire-and-forget persistence. SupervisorJob so a
    // single failed write can't cancel later ones.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keyServerUrl = stringPreferencesKey("server_url")
    private val keyCookie = stringPreferencesKey("session_cookie")
    private val keyUsername = stringPreferencesKey("username")

    @Volatile var serverUrl: String = ""; private set
    @Volatile override var sessionCookie: String = ""; private set
    @Volatile var username: String = ""; private set

    /** Blocking one-time load at process start (tiny preferences file). */
    fun load() = runBlocking {
        val prefs = context.dataStore.data.first()
        serverUrl = prefs[keyServerUrl] ?: ""
        sessionCookie = prefs[keyCookie] ?: ""
        username = prefs[keyUsername] ?: ""
    }

    val hasSession: Boolean get() = serverUrl.isNotEmpty() && sessionCookie.isNotEmpty()

    /** Normalized base URL, always ending in "/". */
    val baseUrl: String get() = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    /**
     * Host of the configured server ("" when none), for deciding whether a
     * request is going to our own server. Read on every request by the cookie
     * jar, so the parse result is cached alongside the URL that produced it.
     */
    @Volatile private var cachedHostFor: String = ""
    @Volatile private var cachedHost: String = ""
    override val serverHost: String
        get() {
            val url = serverUrl
            if (url.isEmpty()) return ""
            if (cachedHostFor != url) {
                cachedHost = try { url.toHttpUrlOrNull()?.host ?: "" } catch (_: Exception) { "" }
                cachedHostFor = url
            }
            return cachedHost
        }

    suspend fun setServerUrl(url: String) {
        val normalized = url.trim().removeSuffix("/")
        serverUrl = normalized
        context.dataStore.edit { it[keyServerUrl] = normalized }
    }

    suspend fun setSession(cookie: String, user: String) {
        sessionCookie = cookie
        username = user
        context.dataStore.edit {
            it[keyCookie] = cookie
            it[keyUsername] = user
        }
    }

    /**
     * Called by the cookie jar when the server refreshes the session cookie —
     * i.e. from OkHttp's network thread. The in-memory value updates
     * synchronously so the very next request already carries it; the disk write
     * is fired off on [scope] rather than blocking the network thread on I/O.
     */
    override fun updateCookie(cookie: String) {
        sessionCookie = cookie
        scope.launch { context.dataStore.edit { it[keyCookie] = cookie } }
    }

    suspend fun clearSession() {
        sessionCookie = ""
        username = ""
        context.dataStore.edit {
            it.remove(keyCookie)
            it.remove(keyUsername)
        }
    }
}
