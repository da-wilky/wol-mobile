package app.swilk.wolmobile.ssh

import android.net.Network
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** [exitStatus] is null when the connection closed before reporting one, e.g. after `poweroff`. */
class SshResult(val exitStatus: Int?, val output: String)

class HostKeyMismatch(seen: String) :
    IOException("Host key changed (now ${fingerprint(seen)}). If expected, pin the host key again in the host.")

/** Host key as "type base64-blob", the known_hosts format. */
fun encodeHostKey(key: PublicKey): String {
    val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
    return "${KeyType.fromKey(key)} ${Base64.getEncoder().encodeToString(blob)}"
}

fun fingerprint(hostKey: String): String {
    val blob = Base64.getDecoder().decode(hostKey.substringAfter(' '))
    val digest = MessageDigest.getInstance("SHA-256").digest(blob)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}

suspend fun runSsh(
    address: String,
    port: Int,
    user: String,
    privateKeyPem: String,
    pinnedHostKey: String,
    command: String,
    timeoutSeconds: Long = 60,
    network: Network? = null,
): SshResult = withContext(Dispatchers.IO) {
    var seen: String? = null
    val verifier = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val encoded = encodeHostKey(key).also { seen = it }
            return pinnedHostKey == encoded
        }

        // No preference: hinting the pinned type would force legacy ssh-rsa for RSA host keys.
        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
    SSHClient(DefaultConfig()).use { client ->
        network?.let { client.socketFactory = it.socketFactory }
        client.addHostKeyVerifier(verifier)
        client.connectTimeout = 10_000
        client.timeout = 30_000
        try {
            client.connect(address, port)
        } catch (e: SSHException) {
            val actual = seen
            if (actual != null && actual != pinnedHostKey) throw HostKeyMismatch(actual)
            throw e
        }
        client.authPublickey(user, OpenSSHKeyV1KeyFile().apply { init(privateKeyPem, null, null) })
        val timedOut = AtomicBoolean()
        // Channel reads block without a deadline; closing the client is the only way to unblock them.
        val watchdog = launch {
            delay(timeoutSeconds * 1_000)
            timedOut.set(true)
            client.close()
        }
        try {
            client.startSession().use { session ->
                val cmd = session.exec(command)
                val output = StringBuilder()
                val exitStatus = try {
                    // Both streams share the channel window; reading them in turn stalls once the unread one fills it.
                    // A failed child would cancel the whole scope, so stderr swallows the error stdout reports anyway.
                    val stderr = async {
                        try {
                            IOUtils.readFully(cmd.errorStream).toString(Charsets.UTF_8.name())
                        } catch (_: IOException) {
                            ""
                        }
                    }
                    output.append(IOUtils.readFully(cmd.inputStream).toString(Charsets.UTF_8.name()))
                    output.append(stderr.await())
                    cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                    cmd.exitStatus
                } catch (e: IOException) {
                    if (timedOut.get()) throw IOException("Command timed out after ${timeoutSeconds}s", e)
                    null
                }
                SshResult(exitStatus, output.toString().trim())
            }
        } finally {
            watchdog.cancel()
        }
    }
}

/** The host key [address]:[port] presents, over [network] if given, or null if it can't be reached. Stops after key exchange without logging in. */
suspend fun probeHostKey(network: Network?, address: String, port: Int): String? = withContext(Dispatchers.IO) {
    var seen: String? = null
    SSHClient(DefaultConfig()).use { client ->
        network?.let { client.socketFactory = it.socketFactory }
        client.connectTimeout = 1_500
        client.timeout = 3_000
        client.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                seen = encodeHostKey(key)
                return false
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })
        try {
            client.connect(address, port)
        } catch (_: IOException) {
        }
    }
    seen
}
