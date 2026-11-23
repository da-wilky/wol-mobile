package app.swilk.wolmobile.run

import android.content.Context
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.Network
import app.swilk.wolmobile.data.NetworkRelay
import app.swilk.wolmobile.data.Settings
import app.swilk.wolmobile.data.SshKey
import app.swilk.wolmobile.data.SshLogin
import app.swilk.wolmobile.data.normalizeMac
import app.swilk.wolmobile.db
import app.swilk.wolmobile.ssh.KeyCipher
import app.swilk.wolmobile.ssh.PassphraseCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.AEADBadTagException

private const val FORMAT = 1
private val json = Json { ignoreUnknownKeys = true }

/** [privateKey] is the PEM encrypted with [PassphraseCipher], base64. */
@Serializable
class ExportedKey(val id: Long, val name: String, val publicKey: String, val privateKey: String)

@Serializable
class Setup(
    val format: Int,
    val salt: String,
    val iterations: Int,
    val networks: List<Network>,
    val hosts: List<Host>,
    val keys: List<ExportedKey>,
    val logins: List<SshLogin>,
    val relays: List<NetworkRelay>,
    val actions: List<Action>,
    val settings: Settings?,
) {
    /** Rejects a file the editors couldn't have produced, before anything is replaced. */
    fun validate() {
        fun unique(ids: List<Any>) = ids.toSet().also { require(it.size == ids.size) }
        val networkIds = unique(networks.map { it.id })
        val hostIds = unique(hosts.map { it.id })
        val keyIds = unique(keys.map { it.id })
        val loginIds = unique(logins.map { it.id })
        val actionIds = unique(actions.map { it.id })
        unique(relays.map { it.networkId to it.position })
        fun ok(id: Long?, ids: Set<Any>) = id == null || id in ids
        fun port(p: Int?) = p == null || p in 1..65535
        require(networks.all { ok(it.presenceHostId, hostIds) })
        // The MAC is substituted into the relay command, so only the normalized form may pass.
        require(hosts.all { ok(it.networkId, networkIds) && (it.mac == null || normalizeMac(it.mac) == it.mac) })
        require(hosts.all { port(it.wolPort) && port(it.sshPort) && port(it.statusPort) })
        require(logins.all { it.hostId in hostIds && ok(it.keyId, keyIds) })
        require(relays.all { it.networkId in networkIds && it.loginId in loginIds })
        require(actions.all { ok(it.hostId, hostIds) && ok(it.loginId, loginIds) })
        require(actions.all { if (it.type == ActionType.WAKE) it.hostId != null else it.loginId != null && it.command != null })
        require(settings == null || settings.id == 0 && settings.tiles.all { ok(it, actionIds) })
        // Bounded so a crafted file can't stall the key derivation.
        require(iterations in 100_000..10_000_000)
    }
}

private fun ByteArray.base64() = Base64.getEncoder().encodeToString(this)
private fun String.unbase64() = Base64.getDecoder().decode(this)

/** Everything but widgets, statuses and the log, as JSON; private keys are encrypted with [passphrase]. */
suspend fun exportSetup(context: Context, passphrase: String): String = withContext(Dispatchers.Default) {
    val dao = context.db.transfer()
    val salt = PassphraseCipher.newSalt()
    val cipher = PassphraseCipher(passphrase, salt, PassphraseCipher.ITERATIONS)
    val keys = dao.keys().map {
        ExportedKey(it.id, it.name, it.publicKey, cipher.encrypt(KeyCipher.decrypt(it.encryptedPrivateKey)).base64())
    }
    json.encodeToString(
        Setup(
            FORMAT, salt.base64(), PassphraseCipher.ITERATIONS, dao.networks(), dao.hosts(), keys,
            dao.logins(), dao.relays(), dao.actions(), dao.settings(),
        ),
    )
}

/** Replaces this app's setup with an export; placed widgets need setting up again. */
suspend fun importSetup(context: Context, text: String, passphrase: String): Outcome = withContext(Dispatchers.Default) {
    val setup: Setup
    val keys: List<SshKey>
    try {
        setup = json.decodeFromString<Setup>(text)
        if (setup.format != FORMAT) return@withContext Outcome(false, "This export is from a newer version of the app")
        setup.validate()
        val cipher = PassphraseCipher(passphrase, setup.salt.unbase64(), setup.iterations)
        keys = setup.keys.map { SshKey(it.id, it.name, it.publicKey, KeyCipher.encrypt(cipher.decrypt(it.privateKey.unbase64()))) }
    } catch (_: AEADBadTagException) {
        return@withContext Outcome(false, "Wrong passphrase")
    } catch (_: IllegalArgumentException) {
        return@withContext Outcome(false, "This file isn't a valid setup export")
    }
    context.db.transfer().replaceAll(setup.networks, setup.hosts, keys, setup.logins, setup.relays, setup.actions, setup.settings)
    onActionsChanged(context)
    Outcome(true, "Imported ${setup.hosts.size} host(s) and ${setup.actions.size} action(s)")
}
