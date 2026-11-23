package app.swilk.wolmobile.run

import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.Network
import app.swilk.wolmobile.data.SshLogin
import app.swilk.wolmobile.ssh.PassphraseCipher
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class TransferTest {
    private val salt = PassphraseCipher.newSalt()

    @Test
    fun passphraseRoundTripsAndRejectsAWrongOne() {
        val sealed = PassphraseCipher("correct horse", salt, PassphraseCipher.ITERATIONS).encrypt("pem".toByteArray())
        assertArrayEquals("pem".toByteArray(), PassphraseCipher("correct horse", salt, PassphraseCipher.ITERATIONS).decrypt(sealed))
        assertThrows(AEADBadTagException::class.java) { PassphraseCipher("wrong horse", salt, PassphraseCipher.ITERATIONS).decrypt(sealed) }
    }

    private fun setup(logins: List<SshLogin>) = Setup(
        1, "", PassphraseCipher.ITERATIONS,
        networks = listOf(Network(1, "Home", 2, "255.255.255.255", "wakeonlan {mac}")),
        hosts = listOf(Host(2, "pi", 1, null, 9, "192.168.1.2", "pi.vpn", 22, "ssh-ed25519 AAAA", true, null)),
        keys = emptyList(),
        logins = logins,
        relays = emptyList(),
        actions = listOf(Action(5, "Wake pi", ActionType.WAKE, 2, null, null, confirmBeforeRun = false, shortcut = false)),
        settings = null,
    )

    @Test
    fun setupSurvivesJsonAndValidates() {
        val text = Json.encodeToString(setup(listOf(SshLogin(3, 2, "wol", "pi", null))))
        val decoded = Json.decodeFromString<Setup>(text)
        decoded.validate()
        assertEquals("pi.vpn", decoded.hosts.single().vpnAddress)
        assertEquals(2L, decoded.networks.single().presenceHostId)
    }

    @Test
    fun macThatWouldInjectIntoTheRelayCommandIsRejected() {
        val base = setup(emptyList())
        val hostile = Setup(
            base.format, base.salt, base.iterations, base.networks, base.hosts.map { it.copy(mac = "AA:BB:CC:DD:EE:FF; reboot") },
            base.keys, base.logins, base.relays, base.actions, base.settings,
        )
        assertThrows(IllegalArgumentException::class.java) { hostile.validate() }
    }

    @Test
    fun danglingReferenceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { setup(listOf(SshLogin(3, 99, "wol", "pi", null))).validate() }
    }
}
