package app.swilk.wolmobile.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

/** The non-VPN Wi-Fi network, so LAN traffic can bypass a VPN that would otherwise capture it. */
suspend fun wifiNetwork(context: Context): Network? {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    return withTimeoutOrNull(1_000) {
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(network)
                }
            }
            cm.registerNetworkCallback(request, callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }.first()
    }
}

/**
 * True if [address]:[port] answers. A refused connection counts as reachable: the host itself sent the reset.
 * Binding to [network] fails if the active VPN forbids bypassing it; that counts as unreachable.
 */
suspend fun tcpReachable(network: Network?, address: String, port: Int, timeoutMs: Int = 1_500): Boolean =
    withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                network?.bindSocket(socket)
                socket.connect(InetSocketAddress(address, port), timeoutMs)
            }
            true
        } catch (_: ConnectException) {
            true
        } catch (_: IOException) {
            false
        }
    }
