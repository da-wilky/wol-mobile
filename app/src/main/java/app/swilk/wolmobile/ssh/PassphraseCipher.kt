package app.swilk.wolmobile.ssh

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM under a key derived from a passphrase, for private keys that leave this app's Keystore in an export. */
class PassphraseCipher(passphrase: String, salt: ByteArray, iterations: Int) {
    private val key = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded,
        "AES",
    )

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + cipher.doFinal(plain)
    }

    /** Throws [javax.crypto.AEADBadTagException] for a wrong passphrase. */
    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, IV_SIZE))
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    companion object {
        const val ITERATIONS = 600_000
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12

        fun newSalt() = ByteArray(16).also(SecureRandom()::nextBytes)
    }
}
