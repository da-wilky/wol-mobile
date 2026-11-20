package app.swilk.wolmobile.run

import android.content.Context
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.SshTarget
import app.swilk.wolmobile.data.get
import app.swilk.wolmobile.db
import app.swilk.wolmobile.net.sendMagicPacket
import app.swilk.wolmobile.net.tcpReachable
import app.swilk.wolmobile.net.wifiNetwork
import app.swilk.wolmobile.ssh.KeyCipher
import app.swilk.wolmobile.ssh.runSsh
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.security.GeneralSecurityException

class Outcome(val ok: Boolean, val message: String, val output: String = "")

fun relayCommand(template: String, host: Host): String = template
    .replace("{mac}", host.mac)
    .replace("{broadcast}", host.broadcastAddress)
    .replace("{port}", host.wolPort.toString())

suspend fun runAction(context: Context, action: Action): Outcome = try {
    val db = context.db
    when (action.type) {
        ActionType.WAKE -> db.hosts().get(action.hostId!!)?.let { wake(context, it) }
        ActionType.SSH -> db.targets().get(action.targetId!!)?.let { ssh(context, it, action.command!!) }
    } ?: Outcome(false, "The host or SSH target of this action no longer exists")
} catch (_: UserAuthException) {
    Outcome(false, "The server rejected the key. Is the public key in authorized_keys?")
} catch (e: IOException) {
    Outcome(false, e.message ?: e.javaClass.simpleName)
} catch (e: SecurityException) {
    Outcome(false, "Permission denied: ${e.message}")
} catch (e: GeneralSecurityException) {
    Outcome(false, "Could not decrypt SSH key: ${e.message}")
}

private suspend fun wake(context: Context, host: Host): Outcome {
    val db = context.db
    val settings = db.settings().get()
    val wifi = wifiNetwork(context)
    val probe = settings.homeProbeAddress
    val atHome = probe == null || wifi != null && tcpReachable(wifi, probe, settings.homeProbePort)
    if (atHome) {
        sendMagicPacket(wifi, host.mac, host.broadcastAddress, host.wolPort)
        return Outcome(true, "Magic packet sent to ${host.mac}")
    }
    val relay = settings.relayTargetId?.let { db.targets().get(it) }
        ?: return Outcome(false, "Not on the home network and no relay is configured")
    return ssh(context, relay, relayCommand(settings.relayCommand, host))
}

private suspend fun ssh(context: Context, target: SshTarget, command: String): Outcome {
    val db = context.db
    val key = db.keys().get(target.keyId)!!
    val pem = KeyCipher.decrypt(key.encryptedPrivateKey).toString(Charsets.UTF_8)
    val result = runSsh(target.address, target.port, target.user, pem, target.hostKey, command)
    if (target.hostKey == null) db.targets().setHostKey(target.id, result.hostKey)
    return when (result.exitStatus) {
        0 -> Outcome(true, "Ran on ${target.name}", result.output)
        null -> Outcome(true, "${target.name} closed the connection without an exit status", result.output)
        else -> Outcome(false, "Exit status ${result.exitStatus} on ${target.name}", result.output)
    }
}
