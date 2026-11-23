package app.swilk.wolmobile.run

import android.content.Context
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.Login
import app.swilk.wolmobile.data.Network
import app.swilk.wolmobile.db
import app.swilk.wolmobile.net.sendMagicPacket
import app.swilk.wolmobile.net.tcpReachable
import app.swilk.wolmobile.net.wifiNetwork
import app.swilk.wolmobile.ssh.KeyCipher
import app.swilk.wolmobile.ssh.SshResult
import app.swilk.wolmobile.ssh.probeHostKey
import app.swilk.wolmobile.ssh.runSsh
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.security.GeneralSecurityException

class Outcome(val ok: Boolean, val message: String, val output: String = "")

fun relayCommand(network: Network, host: Host): String = network.relayCommand
    .replace("{mac}", host.mac!!)
    .replace("{broadcast}", network.broadcastAddress)
    .replace("{port}", host.wolPort.toString())

/** Maps the errors that cross the network and keystore boundaries to a message; anything else is a bug and propagates. */
private inline fun guarded(block: () -> Outcome): Outcome = try {
    block()
} catch (_: UserAuthException) {
    Outcome(false, "The server rejected the key. Is the public key in authorized_keys?")
} catch (e: IOException) {
    Outcome(false, e.message ?: e.javaClass.simpleName)
} catch (e: SecurityException) {
    Outcome(false, "Permission denied: ${e.message}")
} catch (e: GeneralSecurityException) {
    Outcome(false, "Could not decrypt SSH key: ${e.message}")
}

suspend fun runAction(context: Context, action: Action): Outcome = guarded {
    val db = context.db
    when (action.type) {
        ActionType.WAKE -> db.hosts().get(action.hostId!!)?.let { wake(context, it) }
        ActionType.SSH -> db.logins().get(action.loginId!!)?.let { ssh(context, it, action.command!!) }
    } ?: Outcome(false, "The host or login of this action no longer exists")
}

/** Logs in and runs `true`, saying which address it used. */
suspend fun testLogin(context: Context, login: Login): Outcome = guarded {
    val route = route(context, login.host) ?: throw noAddress(login.host)
    connect(context, login, route, "true")
    Outcome(true, "Logged in via ${route.address}")
}

/** Where to reach a host right now; [network] is the Wi-Fi to bind to, or null for normal routing, e.g. through the VPN. */
class Route(val address: String, val network: android.net.Network?)

private fun noAddress(host: Host) = IOException("${host.name} has no address")

/** The local address over Wi-Fi when on the host's network, otherwise the VPN address, or the local one if it has none. */
suspend fun route(context: Context, host: Host): Route? {
    val network = host.networkId?.let { context.db.networks().get(it) }
    val wifi = wifiNetwork(context)
    val local = host.localAddress
    return when {
        local != null && network != null && onNetwork(context, network, wifi) -> Route(local, wifi)
        host.vpnAddress != null -> Route(host.vpnAddress, null)
        local != null -> Route(local, null)
        else -> null
    }
}

/** True when the network's presence host answers at its local address over Wi-Fi, presenting its pinned key if it has one. */
private suspend fun onNetwork(context: Context, network: Network, wifi: android.net.Network?): Boolean {
    val probe = network.presenceHostId?.let { context.db.hosts().get(it) } ?: return true
    val address = probe.localAddress ?: return false
    if (wifi == null) return false
    val pinned = probe.hostKey ?: return tcpReachable(wifi, address, probe.sshPort)
    return probeHostKey(wifi, address, probe.sshPort) == pinned
}

/** [hostKey] is null when no SSH server answered at [address]. */
class HostKeyScan(val address: String, val hostKey: String?)

/** The key [host] presents where [route] currently reaches it, for pinning; null if it has no address. */
suspend fun scanHostKey(context: Context, host: Host): HostKeyScan? {
    val route = route(context, host) ?: return null
    return HostKeyScan(route.address, probeHostKey(route.network, route.address, host.sshPort))
}

private suspend fun wake(context: Context, host: Host): Outcome {
    val networks = context.db.networks()
    val network = host.networkId?.let { networks.get(it) } ?: return Outcome(false, "${host.name} has no network to wake it on")
    if (host.mac == null) return Outcome(false, "${host.name} has no MAC address")
    val wifi = wifiNetwork(context)
    val failures = mutableListOf<String>()
    if (onNetwork(context, network, wifi)) {
        try {
            sendMagicPacket(wifi, host.mac, network.broadcastAddress, host.wolPort)
            return Outcome(true, "Magic packet sent to ${host.mac}")
        } catch (e: IOException) {
            failures += if (e.message?.contains("EPERM") == true) "VPN blocks direct send" else "direct: ${e.message}"
        }
    }
    for (relay in networks.relays(network.id)) {
        val outcome = guarded { ssh(context, relay, relayCommand(network, host)) }
        if (outcome.ok) {
            val skipped = if (failures.isEmpty()) "" else " after ${failures.joinToString("; ")}"
            return Outcome(true, "Sent via ${relay.label}$skipped", outcome.output)
        }
        failures += "${relay.label}: ${outcome.message}"
    }
    if (failures.isEmpty()) return Outcome(false, "Not on ${network.name} and it has no relays")
    return Outcome(false, "Could not send: ${failures.joinToString("; ")}")
}

private suspend fun connect(context: Context, login: Login, route: Route, command: String): SshResult {
    val host = login.host
    // Pinned only from the host editor, never on whatever network a background run happens to be on.
    val pinned = host.hostKey ?: throw IOException("${host.name}'s host key isn't pinned yet. Open the host and tap Pin host key.")
    val key = login.login.keyId?.let { context.db.keys().get(it) }
        ?: throw IOException("${login.label} has no SSH key. Pick one in the login.")
    val pem = KeyCipher.decrypt(key.encryptedPrivateKey).toString(Charsets.UTF_8)
    return runSsh(route.address, host.sshPort, login.login.user, pem, pinned, command, network = route.network)
}

private suspend fun ssh(context: Context, login: Login, command: String): Outcome {
    val route = route(context, login.host) ?: throw noAddress(login.host)
    val result = connect(context, login, route, command)
    val name = login.host.name
    return when (result.exitStatus) {
        0 -> Outcome(true, "Ran on $name", result.output)
        null -> Outcome(true, "$name closed the connection without an exit status", result.output)
        else -> Outcome(false, "Exit status ${result.exitStatus} on $name", result.output)
    }
}
