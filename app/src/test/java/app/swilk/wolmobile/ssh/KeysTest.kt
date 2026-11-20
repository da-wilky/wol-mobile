package app.swilk.wolmobile.ssh

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.sshj.common.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class KeysTest {
    @Test
    fun generatedKeyIsReadableBySshjAndMatchesPublicKey() {
        val key = generateEd25519("test-key")
        val parsed = OpenSSHKeyV1KeyFile().apply { init(key.privateKeyPem, null, null) }

        val (type, blob, comment) = key.publicKey.split(' ')
        assertEquals("ssh-ed25519", type)
        assertEquals("test-key", comment)
        val parsedBlob = Buffer.PlainBuffer().putPublicKey(parsed.public).compactData
        assertEquals(blob, Base64.getEncoder().encodeToString(parsedBlob))
        assertTrue(parsed.private != null)
    }

    @Test
    fun fingerprintMatchesOpenSshFormat() {
        // Expected value from ssh-keygen -lf.
        val hostKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINZzUfHMx+1Cjbp3KmtqIL2H63aTesFGttxVwwJ5g4YL"
        assertEquals("SHA256:J5oqjz+LczYRNZ6orCPuYCkwjLDJerW3AKLzI1gJyvM", fingerprint(hostKey))
    }
}
