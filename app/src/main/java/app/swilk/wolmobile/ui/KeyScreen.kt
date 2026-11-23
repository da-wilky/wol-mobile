package app.swilk.wolmobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.SshKey
import app.swilk.wolmobile.db
import app.swilk.wolmobile.ssh.KeyCipher
import app.swilk.wolmobile.ssh.fingerprint
import app.swilk.wolmobile.ssh.generateEd25519
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KeyScreen(id: Long, done: () -> Unit) {
    val context = LocalContext.current
    val dao = context.db.keys()
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf<SshKey?>(null) }
    var users by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(id) {
        key = dao.get(id)
        users = dao.loginNames(id)
    }
    val current = key ?: return

    EditorScaffold(
        title = current.name,
        onBack = done,
        deleteWarning = if (users.isEmpty()) {
            "Servers that trust this key will no longer be reachable with it."
        } else {
            "Used by ${users.joinToString()}. They keep their settings but have no key until you pick another one."
        },
        onDelete = { scope.launch { dao.delete(current); done() } },
    ) {
        FormSection(
            "Public key",
            "Add this line to ~/.ssh/authorized_keys on the server. Prefix it with command=\"…\" to restrict the key to one command.",
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                SelectionContainer {
                    Text(
                        current.publicKey,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
            Text(
                fingerprint(current.publicKey.substringBeforeLast(' ')),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Public key", current.publicKey))
                }) {
                    Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Copy")
                }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, current.publicKey)
                    context.startActivity(Intent.createChooser(send, "Share public key"))
                }) {
                    Icon(painterResource(R.drawable.ic_share), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Share")
                }
            }
        }
        if (users.isNotEmpty()) Hint("Used by ${users.joinToString()}.")
    }
}

@Composable
fun NewKeyDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!generating) onDone() },
        icon = { Icon(painterResource(R.drawable.ic_key), null) },
        title = { Text("New Ed25519 key") },
        text = {
            if (generating) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    placeholder = { Text("pi-wake") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !generating,
                onClick = {
                    generating = true
                    scope.launch {
                        val key = withContext(Dispatchers.Default) {
                            val generated = generateEd25519("wolmobile-" + name.trim().replace(Regex("\\s+"), "-"))
                            SshKey(
                                name = name.trim(),
                                publicKey = generated.publicKey,
                                encryptedPrivateKey = KeyCipher.encrypt(generated.privateKeyPem.toByteArray()),
                            )
                        }
                        context.db.keys().upsert(key)
                        onDone()
                    }
                },
            ) { Text("Generate") }
        },
        dismissButton = { TextButton(enabled = !generating, onClick = onDone) { Text("Cancel") } },
    )
}
