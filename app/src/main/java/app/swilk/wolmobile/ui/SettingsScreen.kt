package app.swilk.wolmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.data.DEFAULT_RELAY_COMMAND
import app.swilk.wolmobile.data.Settings
import app.swilk.wolmobile.data.TILE_COUNT
import app.swilk.wolmobile.data.get
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.refreshTiles
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val scope = rememberCoroutineScope()
    val targets by db.targets().all().collectAsStateWithLifecycle(emptyList())
    val actions by db.actions().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(false) }
    var probeAddress by rememberSaveable { mutableStateOf("") }
    var probePort by rememberSaveable { mutableStateOf("22") }
    var relayTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var relayCommand by rememberSaveable { mutableStateOf(DEFAULT_RELAY_COMMAND) }
    var tiles by rememberSaveable { mutableStateOf(List<Long?>(TILE_COUNT) { null }) }
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val settings = db.settings().get()
        probeAddress = settings.homeProbeAddress.orEmpty()
        probePort = settings.homeProbePort.toString()
        relayTargetId = settings.relayTargetId
        relayCommand = settings.relayCommand
        tiles = settings.tiles
        loaded = true
    }

    val probeValid = probeAddress.isBlank() || parsePort(probePort) != null
    EditorScaffold(
        title = "Settings",
        onBack = done,
        canSave = loaded && probeValid && relayCommand.isNotBlank(),
        onSave = {
            scope.launch {
                val base = Settings(
                    homeProbeAddress = probeAddress.trim().ifEmpty { null },
                    homeProbePort = parsePort(probePort) ?: 22,
                    relayTargetId = relayTargetId,
                    relayCommand = relayCommand.trim(),
                )
                db.settings().upsert(tiles.foldIndexed(base) { slot, settings, actionId -> settings.withTile(slot, actionId) })
                refreshTiles(context)
                done()
            }
        },
    ) {
        FormSection(
            "Home detection",
            "Before waking a host, the app connects to this address over Wi-Fi, bypassing the VPN. " +
                "If it answers, you're home and the magic packet is sent directly. Leave empty to always send directly.",
        ) {
            Field("LAN address", probeAddress, KeyboardType.Uri, placeholder = "the Pi, e.g. 192.168.1.2") { probeAddress = it }
            Field("TCP port", probePort, KeyboardType.Number, error = !probeValid) { probePort = it }
        }
        FormSection(
            "Remote wake",
            "Away from home, this command runs on the relay over SSH.",
        ) {
            Picker("Relay SSH target", targets.map { it.id to it.name }, relayTargetId, noneLabel = "None") { relayTargetId = it }
            Field("Relay command", relayCommand, supporting = "Placeholders: {mac}, {broadcast}, {port}") { relayCommand = it }
        }
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
    }
}
