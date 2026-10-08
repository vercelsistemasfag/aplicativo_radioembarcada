package br.com.radioembarcada.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler

class NetworkMonitor(context: Context, private val handler: Handler, private val onChange: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    val isConnected: Boolean
        get() = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { handler.post(onChange) }
        override fun onLost(network: Network) { handler.post(onChange) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            handler.post(onChange)
        }
    }
    fun start() { manager.registerDefaultNetworkCallback(callback) }
    fun stop() { manager.unregisterNetworkCallback(callback) }
}
