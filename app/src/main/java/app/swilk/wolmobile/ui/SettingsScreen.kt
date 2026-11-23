package app.swilk.wolmobile.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Settings
import app.swilk.wolmobile.data.TILE_COUNT
import app.swilk.wolmobile.data.get
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.exportSetup
import app.swilk.wolmobile.run.importSetup
import app.swilk.wolmobile.run.refreshTiles
import app.swilk.wolmobile.widget.GridWidgetReceiver
import app.swilk.wolmobile.widget.SingleWidgetReceiver
import app.swilk.wolmobile.widget.widgetConfigIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

@Composable
fun SettingsScreen(done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val scope = rememberCoroutineScope()
    val actions by db.actions().all().collectAsStateWithLifecycle(emptyList())
    val configs by db.widgets().all().collectAsStateWithLifecycle(emptyList())
    var placed by remember { mutableStateOf(emptyList<Pair<Int, Boolean>>()) }
    // Widgets are added and removed on the home screen, so re-read them whenever the user comes back.
    LifecycleResumeEffect(Unit) {
        val manager = AppWidgetManager.getInstance(context)
        placed = listOf(GridWidgetReceiver::class.java to true, SingleWidgetReceiver::class.java to false).flatMap { (receiver, grid) ->
            manager.getAppWidgetIds(ComponentName(context, receiver)).map { it to grid }
        }
        onPauseOrDispose {}
    }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var tiles by rememberSaveable { mutableStateOf(List<Long?>(TILE_COUNT) { null }) }
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val settings = db.settings().get()
        tiles = settings.tiles
        loaded = true
    }

    // Held only in memory while the file picker is open; never saved.
    var exportPassphrase by remember { mutableStateOf<String?>(null) }
    var askExport by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var transferring by remember { mutableStateOf(false) }
    fun transfer(block: suspend () -> String) {
        transferring = true
        scope.launch {
            val message = try {
                block()
            } catch (e: IOException) {
                "Couldn't access the file: ${e.message}"
            } catch (e: GeneralSecurityException) {
                "Couldn't read the stored keys: ${e.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            transferring = false
        }
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val passphrase = exportPassphrase ?: return@rememberLauncherForActivityResult
        exportPassphrase = null
        if (uri != null) transfer {
            val text = exportSetup(context, passphrase)
            withContext(Dispatchers.IO) {
                (context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")).use { it.write(text.toByteArray()) }
            }
            "Setup exported"
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it }

    EditorScaffold(
        title = "Settings",
        onBack = done,
        canSave = loaded,
        onSave = {
            scope.launch {
                db.settings().upsert(tiles.foldIndexed(Settings()) { slot, settings, actionId -> settings.withTile(slot, actionId) })
                refreshTiles(context)
                done()
            }
        },
    ) {
        FormSection(
            "Quick Settings tiles",
            "Add the tiles with the edit button in the Quick Settings panel.",
        ) {
            repeat(TILE_COUNT) { slot ->
                Picker("Tile ${slot + 1}", actions.map { it.id to it.label }, tiles[slot], noneLabel = "None") { id ->
                    tiles = tiles.toMutableList().also { it[slot] = id }
                }
            }
        }
        FormSection("Home screen widgets", "Add widgets from the home screen's widget picker. Tap one here to change it.") {
            if (placed.isEmpty()) Hint("No widgets placed yet.")
            placed.forEach { (id, grid) ->
                val labels = configs.find { it.appWidgetId == id }?.actionIdList.orEmpty().mapNotNull { a -> actions.find { it.id == a }?.label }
                WidgetRow(grid, labels.joinToString().ifEmpty { "Not set up" }) { context.startActivity(widgetConfigIntent(context, id)) }
            }
        }
        FormSection(
            "Transfer setup",
            "Move hosts, networks, keys, logins, actions and tiles to another install, e.g. from the debug to the release build or to a new phone. " +
                "Private keys in the file are encrypted with a passphrase you choose.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !transferring, onClick = { askExport = true }) { Text("Export") }
                OutlinedButton(enabled = !transferring, onClick = { openFile.launch(arrayOf("*/*")) }) { Text("Import") }
                if (transferring) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
    }

    if (askExport) {
        PassphraseDialog("Export setup", "Choose a passphrase. You'll need it to import the file.", confirm = true, onDismiss = { askExport = false }) {
            askExport = false
            exportPassphrase = it
            createFile.launch("wol-setup.json")
        }
    }
    importUri?.let { uri ->
        PassphraseDialog(
            "Import setup",
            "This replaces all hosts, networks, keys, logins, actions and tiles here. Placed widgets need setting up again.",
            confirm = false,
            onDismiss = { importUri = null },
        ) { passphrase ->
            importUri = null
            transfer {
                val text = withContext(Dispatchers.IO) {
                    (context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")).use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val outcome = importSetup(context, text, passphrase)
                if (outcome.ok) tiles = db.settings().get().tiles
                outcome.message
            }
        }
    }
}

@Composable
private fun PassphraseDialog(title: String, text: String, confirm: Boolean, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val valid = passphrase.length >= 8 && (!confirm || repeated == passphrase)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                PassphraseField("Passphrase", passphrase, if (confirm) "At least 8 characters" else null) { passphrase = it }
                if (confirm) PassphraseField("Repeat passphrase", repeated, null) { repeated = it }
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { onDone(passphrase) }) { Text(if (confirm) "Choose file" else "Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PassphraseField(label: String, value: String, supporting: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun WidgetRow(grid: Boolean, summary: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(if (grid) R.drawable.ic_widget_grid else R.drawable.ic_power)
            Column(Modifier.weight(1f)) {
                Text(if (grid) "Actions grid" else "Single action", style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.ic_settings), "Edit widget", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
