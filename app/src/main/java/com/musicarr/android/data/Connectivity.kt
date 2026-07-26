package com.musicarr.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device currently has usable connectivity.
 *
 * Deliberately reports the *network's* state, not the server's: a Musicarr
 * server is usually on the user's LAN, so "has internet" is the wrong
 * question — a phone on home Wi-Fi with no WAN can still reach it perfectly.
 * NET_CAPABILITY_VALIDATED is therefore not required.
 *
 * This is a hint for the UI and for deciding when to retry, never a gate: every
 * request still goes out and is allowed to fail on its own terms, because a
 * reachable network says nothing about a reachable server.
 */
class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(currentlyOnline())

    val online: StateFlow<Boolean> = _online

    /** Invoked when connectivity comes back, to flush anything queued offline. */
    var onReconnect: (() -> Unit)? = null

    private fun currentlyOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update()
        override fun onLost(network: Network) = update()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()

        private fun update() {
            val now = currentlyOnline()
            val was = _online.value
            _online.value = now
            if (now && !was) onReconnect?.invoke()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // registerNetworkCallback can throw if the device is in a odd state
        // (some OEM ROMs, or too many registered callbacks); connectivity
        // awareness is an enhancement, never a reason to fail to start.
        runCatching { manager?.registerNetworkCallback(request, callback) }
    }
}
