package app.swilk.wolmobile.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SubnetTest {
    private fun ip(s: String) = InetAddress.getByName(s).address

    @Test
    fun matchesWithinPrefixOnly() {
        assertTrue(inSubnet(ip("192.168.1.20"), ip("192.168.1.5"), 24))
        assertFalse(inSubnet(ip("192.168.2.20"), ip("192.168.1.5"), 24))
        assertTrue(inSubnet(ip("10.0.13.7"), ip("10.0.8.1"), 20))
        assertFalse(inSubnet(ip("10.0.16.7"), ip("10.0.8.1"), 20))
        assertFalse(inSubnet(ip("fd00::1"), ip("192.168.1.5"), 24))
    }
}
