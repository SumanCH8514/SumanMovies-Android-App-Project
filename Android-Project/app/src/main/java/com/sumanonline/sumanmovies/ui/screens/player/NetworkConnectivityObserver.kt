package com.sumanonline.sumanmovies.ui.screens.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkStatus {
    AVAILABLE,
    LOST,
    UNAVAILABLE
}

class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _status = MutableStateFlow(getCurrentStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun getCurrentStatus(): NetworkStatus {
        val cm = connectivityManager ?: return NetworkStatus.UNAVAILABLE
        val active = cm.activeNetwork ?: return NetworkStatus.UNAVAILABLE
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkStatus.UNAVAILABLE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            NetworkStatus.AVAILABLE
        } else {
            NetworkStatus.UNAVAILABLE
        }
    }

    fun startObserving() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _status.value = NetworkStatus.AVAILABLE
            }

            override fun onLost(network: Network) {
                _status.value = NetworkStatus.LOST
            }

            override fun onUnavailable() {
                _status.value = NetworkStatus.UNAVAILABLE
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (_: Throwable) {
            _status.value = getCurrentStatus()
        }
    }

    fun stopObserving() {
        val callback = networkCallback ?: return
        val cm = connectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {}
        networkCallback = null
    }
}
