package com.musicarr.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "musicarr_session")

/**
 * Persists the server URL, the session cookie, and the signed-in username.
 * Values are also cached in memory so the network stack (interceptors, the
 * playback service's data source) can read them synchronously.
 */
class SessionManager(private val context: Context) {
    private val keyServerUrl = stringPreferencesKey("server_url")
    private val keyCookie = stringPreferencesKey("session_cookie")
    private val keyUsername = stringPreferencesKey("username")

    @Volatile var serverUrl: String = ""; private set
    @Volatile var sessionCookie: String = ""; private set
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

    /** Called by the cookie jar when the server refreshes the session cookie. */
    fun updateCookie(cookie: String) {
        sessionCookie = cookie
        runBlocking { context.dataStore.edit { it[keyCookie] = cookie } }
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
