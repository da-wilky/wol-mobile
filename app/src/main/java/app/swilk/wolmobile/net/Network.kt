package app.swilk.wolmobile.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

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
 * True if [address]:[port] answers. A refused connection counts as reachable: the host itself sent the reset. Other connect errors, e.g. ENETUNREACH, also throw [ConnectException].
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
        } catch (e: ConnectException) {
            (e.cause as? ErrnoException)?.errno == OsConstants.ECONNREFUSED
        } catch (_: IOException) {
            false
        }
    }

/**
 * Apps can't open raw sockets; the system ping uses an unprivileged ICMP socket instead.
 * It can't resolve VPN names such as NetBird's, so [address] is resolved here, over [network] if given.
 */
suspend fun pingReachable(network: Network?, address: String, deadlineSeconds: Int = 3): Boolean = withContext(Dispatchers.IO) {
    try {
        val ip = resolve(network, address).first()
        val binary = if (ip is Inet6Address) "/system/bin/ping6" else "/system/bin/ping"
        // Repeats until one reply or the deadline: the first packet can be lost while a VPN tunnel is set up on demand.
        // Not reading the output: a few pings' fit the pipe buffer.
        val process = ProcessBuilder(binary, "-c", "1", "-i", "0.5", "-w", "$deadlineSeconds", ip.hostAddress)
            .redirectErrorStream(true).start()
        val exited = process.waitFor(deadlineSeconds + 1L, TimeUnit.SECONDS)
        process.destroy()
        exited && process.exitValue() == 0
    } catch (_: IOException) {
        false
    }
}

private fun resolve(network: Network?, address: String): Array<InetAddress> =
    network?.getAllByName(address) ?: InetAddress.getAllByName(address)

/** True when one of [addresses] lies in a subnet the Wi-Fi [network] is directly attached to. */
suspend fun inWifiSubnet(context: Context, network: Network, addresses: List<String>): Boolean = withContext(Dispatchers.IO) {
    val links = context.getSystemService(ConnectivityManager::class.java).getLinkProperties(network)?.linkAddresses.orEmpty()
    addresses.any { address ->
        val ips = try {
            resolve(network, address).toList()
        } catch (_: IOException) {
            emptyList()
        }
        ips.any { ip -> links.any { inSubnet(ip.address, it.address.address, it.prefixLength) } }
    }
}

fun inSubnet(address: ByteArray, subnet: ByteArray, prefixLength: Int): Boolean {
    if (address.size != subnet.size) return false
    return address.indices.all { i ->
        val bits = (prefixLength - i * 8).coerceIn(0, 8)
        val mask = (0xFF shl (8 - bits)) and 0xFF
        (address[i].toInt() and mask) == (subnet[i].toInt() and mask)
    }
}
