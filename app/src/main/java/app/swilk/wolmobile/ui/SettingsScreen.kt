package app.swilk.wolmobile.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Settings
import app.swilk.wolmobile.data.TILE_COUNT
import app.swilk.wolmobile.data.get
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.refreshTiles
import app.swilk.wolmobile.widget.GridWidgetReceiver
import app.swilk.wolmobile.widget.SingleWidgetReceiver
import app.swilk.wolmobile.widget.widgetConfigIntent
import kotlinx.coroutines.launch

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
    }
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
