package app.swilk.wolmobile.ssh

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import kotlinx.coroutines.Dispatchers
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
class SshResult(val exitStatus: Int?, val output: String, val hostKey: String)

class HostKeyMismatch(seen: String) :
    IOException("Host key changed (now ${fingerprint(seen)}). If expected, forget the host key in the SSH target.")

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

/** Trust on first use: with no [pinnedHostKey] any key is accepted and returned in [SshResult.hostKey] for pinning. */
suspend fun runSsh(
    address: String,
    port: Int,
    user: String,
    privateKeyPem: String,
    pinnedHostKey: String?,
    command: String,
    timeoutSeconds: Long = 60,
): SshResult = withContext(Dispatchers.IO) {
    var seen: String? = null
    val verifier = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val encoded = encodeHostKey(key).also { seen = it }
            return pinnedHostKey == null || pinnedHostKey == encoded
        }

        // No preference: hinting the pinned type would force legacy ssh-rsa for RSA host keys.
        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
    SSHClient(DefaultConfig()).use { client ->
        client.addHostKeyVerifier(verifier)
        client.connectTimeout = 10_000
        client.timeout = 30_000
        try {
            client.connect(address, port)
        } catch (e: SSHException) {
            val actual = seen
            if (actual != null && pinnedHostKey != null && actual != pinnedHostKey) throw HostKeyMismatch(actual)
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
                    output.append(IOUtils.readFully(cmd.inputStream).toString(Charsets.UTF_8.name()))
                    output.append(IOUtils.readFully(cmd.errorStream).toString(Charsets.UTF_8.name()))
                    cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                    cmd.exitStatus
                } catch (e: IOException) {
                    if (timedOut.get()) throw IOException("Command timed out after ${timeoutSeconds}s", e)
                    null
                }
                SshResult(exitStatus, output.toString().trim(), seen!!)
            }
        } finally {
            watchdog.cancel()
        }
    }
}
