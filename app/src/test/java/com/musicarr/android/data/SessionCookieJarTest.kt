package com.musicarr.android.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session cookie must only ever be attached to the configured Musicarr
 * server. The OkHttpClient is shared with ExoPlayer and follows redirects, so
 * without a host check a redirect or an off-origin stream URL would hand the
 * user's credentials to a third party.
 */
class SessionCookieJarTest {

    /** Stands in for SessionManager without pulling in Android's DataStore. */
    private class FakeSession(
        var host: String = "music.example.com",
        var cookieValue: String = "token-abc",
    ) : SessionCookieSource {
        override val serverHost: String get() = host
        override val sessionCookie: String get() = cookieValue
        val saved = mutableListOf<String>()
        override fun updateCookie(cookie: String) { cookieValue = cookie; saved += cookie }
    }

    private fun jarFor(session: FakeSession) = SessionCookieJar(session)

    @Test
    fun `cookie is sent to the configured server`() {
        val jar = jarFor(FakeSession())
        val sent = jar.loadForRequest("https://music.example.com/api/library".toHttpUrl())
        assertEquals(1, sent.size)
        assertEquals("token-abc", sent[0].value)
    }

    @Test
    fun `cookie is withheld from every other host`() {
        val jar = jarFor(FakeSession())
        assertTrue(jar.loadForRequest("https://evil.example.com/steal".toHttpUrl()).isEmpty())
        // A subdomain of the server is still a different host.
        assertTrue(jar.loadForRequest("https://cdn.music.example.com/x".toHttpUrl()).isEmpty())
        // ...and so is a lookalike that merely ends with the server's name.
        assertTrue(jar.loadForRequest("https://notmusic.example.com/x".toHttpUrl()).isEmpty())
    }

    @Test
    fun `host comparison ignores case but not identity`() {
        val jar = jarFor(FakeSession(host = "Music.Example.COM"))
        assertEquals(1, jar.loadForRequest("https://music.example.com/api".toHttpUrl()).size)
    }

    @Test
    fun `no cookie is sent when signed out or unconfigured`() {
        assertTrue(
            jarFor(FakeSession(cookieValue = ""))
                .loadForRequest("https://music.example.com/api".toHttpUrl()).isEmpty()
        )
        assertTrue(
            jarFor(FakeSession(host = ""))
                .loadForRequest("https://music.example.com/api".toHttpUrl()).isEmpty()
        )
    }

    @Test
    fun `a refreshed cookie is stored only when it comes from our server`() {
        val session = FakeSession()
        val jar = jarFor(session)
        val fresh = Cookie.Builder()
            .name("musicarr_session").value("token-new").domain("music.example.com").path("/").build()

        // An attacker-controlled host must not be able to overwrite the session.
        jar.saveFromResponse("https://evil.example.com/".toHttpUrl(), listOf(fresh))
        assertEquals("token-abc", session.cookieValue)
        assertTrue(session.saved.isEmpty())

        jar.saveFromResponse("https://music.example.com/".toHttpUrl(), listOf(fresh))
        assertEquals("token-new", session.cookieValue)
        assertEquals(listOf("token-new"), session.saved)
    }
}
