package app.swilk.wolmobile.net

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

fun magicPacket(mac: String): ByteArray {
    val macBytes = mac.split(':').map { it.toInt(16).toByte() }
    require(macBytes.size == 6) { "Invalid MAC: $mac" }
    return ByteArray(6) { 0xFF.toByte() } + List(16) { macBytes }.flatten()
}

suspend fun sendMagicPacket(network: Network?, mac: String, broadcast: String, port: Int) = withContext(Dispatchers.IO) {
    val payload = magicPacket(mac)
    val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(broadcast), port)
    DatagramSocket().use { socket ->
        network?.bindSocket(socket)
        socket.broadcast = true
        // UDP is fire-and-forget; repeat to survive a dropped packet.
        repeat(3) { socket.send(packet) }
    }
}
