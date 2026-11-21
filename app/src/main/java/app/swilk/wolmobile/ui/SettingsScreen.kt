package app.swilk.wolmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val actions by db.actions().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(false) }
    var tiles by rememberSaveable { mutableStateOf(List<Long?>(TILE_COUNT) { null }) }
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val settings = db.settings().get()
        tiles = settings.tiles
        loaded = true
    }

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
    }
}
