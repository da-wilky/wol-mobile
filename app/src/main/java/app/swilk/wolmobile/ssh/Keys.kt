package app.swilk.wolmobile.ssh

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.Base64

class GeneratedKey(val publicKey: String, val privateKeyPem: String)

private const val ED25519 = "ssh-ed25519"

/** Unencrypted OpenSSH (openssh-key-v1) Ed25519 key; at-rest protection is done by [KeyCipher]. */
fun generateEd25519(comment: String, random: SecureRandom = SecureRandom()): GeneratedKey {
    val private = Ed25519PrivateKeyParameters(random)
    val pub = private.generatePublicKey().encoded
    val publicBlob = sshBytes { string(ED25519.toByteArray()); string(pub) }
    val check = random.nextInt()
    val privateSection = sshBytes {
        writeInt(check); writeInt(check)
        string(ED25519.toByteArray()); string(pub); string(private.encoded + pub); string(comment.toByteArray())
        var pad = 1
        while (size() % 8 != 0) writeByte(pad++)
    }
    val file = sshBytes {
        write("openssh-key-v1\u0000".toByteArray())
        string("none".toByteArray()); string("none".toByteArray()); string(ByteArray(0))
        writeInt(1)
        string(publicBlob)
        string(privateSection)
    }
    val base64 = Base64.getEncoder()
    val pem = buildString {
        appendLine("-----BEGIN OPENSSH PRIVATE KEY-----")
        base64.encodeToString(file).chunked(70).forEach(::appendLine)
        appendLine("-----END OPENSSH PRIVATE KEY-----")
    }
    return GeneratedKey("$ED25519 ${base64.encodeToString(publicBlob)} $comment", pem)
}

private fun sshBytes(build: DataOutputStream.() -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use(build)
    return bytes.toByteArray()
}

private fun DataOutputStream.string(value: ByteArray) {
    writeInt(value.size)
    write(value)
}
