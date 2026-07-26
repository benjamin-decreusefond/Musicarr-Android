package com.musicarr.android.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app must keep working against a LAN server over plain HTTP, while never
 * putting the session cookie on the wire in the clear to a public host.
 */
class CleartextGuardTest {

    private fun local(host: String) = CleartextGuard.isLocalHost(host)

    @Test
    fun `loopback and localhost are local`() {
        assertTrue(local("localhost"))
        assertTrue(local("127.0.0.1"))
        assertTrue(local("127.1.2.3"))
        assertTrue(local("::1"))
    }

    @Test
    fun `RFC1918 private ranges are local`() {
        assertTrue(local("10.0.0.5"))
        assertTrue(local("10.255.255.254"))
        assertTrue(local("192.168.1.10"))
        assertTrue(local("172.16.0.1"))
        assertTrue(local("172.31.255.254"))
    }

    @Test
    fun `addresses just outside the private ranges are not local`() {
        // 172.15 and 172.32 sit either side of 172.16.0.0/12 — the boundary a
        // hand-rolled prefix check tends to get wrong.
        assertFalse(local("172.15.0.1"))
        assertFalse(local("172.32.0.1"))
        assertFalse(local("11.0.0.1"))
        assertFalse(local("192.169.1.1"))
        assertFalse(local("9.255.255.255"))
    }

    @Test
    fun `link-local, unique-local IPv6 and CGNAT are local`() {
        assertTrue(local("169.254.1.1"))       // link-local
        assertTrue(local("fd00::1"))           // unique local IPv6
        assertTrue(local("fc00::1"))
        assertTrue(local("[fd12:3456::1]"))    // bracketed form
        assertTrue(local("100.64.0.1"))        // CGNAT / Tailscale
        assertTrue(local("100.127.255.254"))
    }

    @Test
    fun `public IPv6 and public IPv4 are not local`() {
        assertFalse(local("2001:4860:4860::8888"))
        assertFalse(local("8.8.8.8"))
        assertFalse(local("1.1.1.1"))
        assertFalse(local("100.63.255.255"))   // just below CGNAT
        assertFalse(local("100.128.0.0"))      // just above CGNAT
    }

    @Test
    fun `local network name suffixes are local`() {
        assertTrue(local("nas.local"))
        assertTrue(local("Server.LOCAL"))      // case-insensitive
        assertTrue(local("musicarr.home.arpa"))
        assertTrue(local("box.lan"))
    }

    @Test
    fun `public DNS names are never local, even ones that look private`() {
        assertFalse(local("music.example.com"))
        assertFalse(local(""))
        // A public name is not resolved to decide this: DNS is controlled by
        // whoever owns the name, so it must not get a vote on whether the
        // session cookie may travel unencrypted.
        assertFalse(local("localhost.example.com"))
        assertFalse(local("10.0.0.5.example.com"))
    }

    @Test
    fun `https is allowed anywhere and http only on the LAN`() {
        // isLocalHost is what the interceptor gates cleartext on; https bypasses
        // the check entirely.
        assertTrue("https://music.example.com/".toHttpUrl().isHttps)
        assertFalse(CleartextGuard.isLocalHost("http://music.example.com/".toHttpUrl()))
        assertTrue(CleartextGuard.isLocalHost("http://192.168.1.20:8686/".toHttpUrl()))
    }
}
