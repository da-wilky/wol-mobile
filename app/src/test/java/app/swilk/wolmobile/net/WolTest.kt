package app.swilk.wolmobile.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WolTest {
    @Test
    fun magicPacketIsSyncStreamFollowedBySixteenMacs() {
        val packet = magicPacket("01:23:45:67:89:AB")
        val mac = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte())
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (i in 0 until 16) assertArrayEquals(mac, packet.copyOfRange(6 + i * 6, 12 + i * 6))
    }

    @Test
    fun sendsMagicPacketOverUdp() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { receiver ->
            receiver.soTimeout = 2_000
            runBlocking { sendMagicPacket(null, "01:23:45:67:89:AB", "127.0.0.1", receiver.localPort) }
            val buffer = DatagramPacket(ByteArray(200), 200)
            receiver.receive(buffer)
            assertArrayEquals(magicPacket("01:23:45:67:89:AB"), buffer.data.copyOf(buffer.length))
        }
    }
}
