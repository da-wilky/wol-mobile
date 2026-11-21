package app.swilk.wolmobile.run

import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.Network
import org.junit.Assert.assertEquals
import org.junit.Test

class RunTest {
    @Test
    fun relayCommandFillsPlaceholders() {
        val network = Network(1, "Home", null, 22, "192.168.1.255", "wakeonlan -i {broadcast} -p {port} {mac}")
        val host = Host(1, "nas", "AA:BB:CC:DD:EE:FF", network.id, 9, null, null)
        assertEquals(
            "wakeonlan -i 192.168.1.255 -p 9 AA:BB:CC:DD:EE:FF",
            relayCommand(network, host),
        )
    }

    @Test
    fun formatsAges() {
        assertEquals("now", formatAge(30_000))
        assertEquals("5m", formatAge(5 * 60_000))
        assertEquals("2h", formatAge(2 * 3_600_000))
        assertEquals("3d", formatAge(3 * 86_400_000L))
    }
}
