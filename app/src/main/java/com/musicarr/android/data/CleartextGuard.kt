package com.musicarr.android.data

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Refuses unencrypted requests to anything that isn't a local network address.
 *
 * The app has to allow cleartext at all — Musicarr is self-hosted and most
 * people run it on their LAN without a certificate. But allowing it app-wide
 * meant the session cookie could travel in the clear to any host, over any
 * network. Android's network security config can't express "private ranges
 * only" (it matches domains by DNS suffix and IP literals exactly), so the
 * rule is enforced here instead, on the shared OkHttpClient — which also covers
 * ExoPlayer's stream requests.
 *
 * Hostnames that aren't IP literals are only treated as local when they are
 * loopback or a local-network suffix; a public DNS name over http:// is
 * rejected rather than resolved, so a name that happens to point at a private
 * address can't be used to slip past the check.
 */
class CleartextGuard : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps && !isLocalHost(url)) {
            throw IOException(
                "Refusing to send unencrypted traffic to ${url.host}. " +
                    "Use https:// for a server outside your local network."
            )
        }
        return chain.proceed(chain.request())
    }

    companion object {
        // Suffixes used for names on a local network. ".local" is mDNS; the
        // others are the conventional home-router domains.
        private val LOCAL_SUFFIXES = listOf(".local", ".home.arpa", ".lan", ".internal")

        /** Whether [url] points somewhere on the local network. */
        fun isLocalHost(url: HttpUrl): Boolean = isLocalHost(url.host)

        fun isLocalHost(rawHost: String): Boolean {
            val host = rawHost.trim().trim('[', ']').lowercase()
            if (host.isEmpty()) return false
            if (host == "localhost") return true
            if (LOCAL_SUFFIXES.any { host.endsWith(it) }) return true
            // Only treat it as an address if it already *is* one: resolving a
            // public name here would let DNS decide whether cleartext is
            // allowed, which is exactly the wrong party to ask.
            val address = parseLiteral(host) ?: return false
            return address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||   // 10/8, 172.16/12, 192.168/16
                address.isAnyLocalAddress ||
                isUniqueLocalIpv6(address) ||
                isCarrierGradeNat(address)
        }

        /** Parse an IP literal without ever hitting DNS. */
        private fun parseLiteral(host: String): InetAddress? {
            val looksNumeric = host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }
            val looksIpv6 = host.contains(':')
            if (!looksNumeric && !looksIpv6) return null
            return try { InetAddress.getByName(host) } catch (_: Exception) { null }
        }

        /** fc00::/7 — the IPv6 equivalent of the private IPv4 ranges. */
        private fun isUniqueLocalIpv6(address: InetAddress): Boolean =
            address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC

        /** 100.64.0.0/10 — used by Tailscale and similar overlay networks, which
         *  is how a lot of people reach a home server from outside. */
        private fun isCarrierGradeNat(address: InetAddress): Boolean {
            if (address !is Inet4Address) return false
            val bytes = address.address
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            return first == 100 && second in 64..127
        }
    }
}
