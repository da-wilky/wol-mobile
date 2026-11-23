package app.swilk.wolmobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.data.RunState
import app.swilk.wolmobile.data.WidgetConfig
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.enqueueStatusCheck
import app.swilk.wolmobile.run.syncPeriodicStatus
import app.swilk.wolmobile.ui.EditorScaffold
import app.swilk.wolmobile.ui.FormSection
import app.swilk.wolmobile.ui.Hint
import app.swilk.wolmobile.ui.IconBadge
import app.swilk.wolmobile.ui.Picker
import app.swilk.wolmobile.ui.SwitchRow
import app.swilk.wolmobile.ui.WolTheme
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn

fun widgetConfigIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

/** Opened by the launcher when a widget is placed or reconfigured, and from the app's settings. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        // Exported for the launcher, so any app can start it; only configure widgets that are really ours.
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (info?.provider?.packageName != packageName) return finish()
        val grid = isGridWidget(this, appWidgetId)
        enableEdgeToEdge()
        setContent {
            WolTheme {
                WidgetConfigScreen(appWidgetId, grid, onBack = ::finish) {
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigScreen(appWidgetId: Int, grid: Boolean, onBack: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val scope = rememberCoroutineScope()
    val actions by db.actions().all().collectAsStateWithLifecycle(emptyList())
    val statuses by db.status().all().collectAsStateWithLifecycle(emptyList())
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptyList<Long>()) }
    var showStatus by remember { mutableStateOf(false) }
    LaunchedEffect(appWidgetId) {
        db.widgets().get(appWidgetId)?.let {
            selected = it.actionIdList
            showStatus = it.showStatus
        }
        loaded = true
    }
    val byId = actions.associateBy { it.id }
    val chosen = selected.mapNotNull { byId[it] }

    EditorScaffold(
        title = if (grid) "Widget actions" else "Widget action",
        onBack = onBack,
        canSave = loaded && chosen.isNotEmpty(),
        onSave = {
            scope.launch {
                db.widgets().upsert(WidgetConfig(appWidgetId, chosen.joinToString(",") { it.id.toString() }, showStatus))
                syncPeriodicStatus(context)
                if (showStatus) enqueueStatusCheck(context)
                ActionWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
                saved()
            }
        },
    ) {
        FormSection("Preview") {
            WidgetPreview(widgetSize(context, appWidgetId, grid), grid, chosen, statuses.takeIf { showStatus }.orEmpty())
        }
        if (actions.isEmpty()) {
            FormSection("Actions") { Text("Create an action in the app first.", color = MaterialTheme.colorScheme.error) }
        } else if (grid) {
            FormSection("Actions", "Drag to reorder. The widget scrolls when they don't all fit.") {
                ReorderableColumn(
                    list = chosen,
                    onSettle = { from, to -> selected = chosen.map { it.id }.toMutableList().apply { add(to, removeAt(from)) } },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { _, action, dragging ->
                    key(action.id) {
                        ReorderableItem {
                            ChosenAction(action, dragging, Modifier.draggableHandle()) { selected = selected - action.id }
                        }
                    }
                }
                val available = actions.filter { it.id !in selected }
                if (available.isNotEmpty()) {
                    Picker("Add action", available.map { it.id to it.label }, null) { picked -> picked?.let { selected = selected + it } }
                } else {
                    Hint("All actions are on this widget.")
                }
            }
        } else {
            FormSection("Action") {
                Picker("Action", actions.map { it.id to it.label }, chosen.firstOrNull()?.id) { picked -> selected = listOfNotNull(picked) }
            }
        }
        FormSection("Host status") {
            SwitchRow("Show host status", showStatus, "Checked every ~15 minutes while on, and after each action.") { showStatus = it }
        }
    }
}

@Composable
private fun ChosenAction(action: Action, dragging: Boolean, handle: Modifier, onRemove: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_drag_handle), "Reorder",
                handle.size(40.dp).padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconBadge(if (action.type == ActionType.WAKE) R.drawable.ic_power else R.drawable.ic_terminal)
            Text(action.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            IconButton(onClick = onRemove) { Icon(painterResource(R.drawable.ic_close), "Remove") }
        }
    }
}

/** The widget's portrait size as the launcher reports it, or its default size before the first layout. */
private fun widgetSize(context: Context, appWidgetId: Int, grid: Boolean): DpSize {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    if (width > 0 && height > 0) return DpSize(width.dp, height.dp)
    return if (grid) DpSize(CellSize * 2, CellSize * 2) else DpSize(CellSize, CellSize)
}

/** Mirrors the Glance layout in [ActionWidget], which can't be rendered inside the app. */
@Composable
private fun WidgetPreview(size: DpSize, grid: Boolean, actions: List<Action>, statuses: List<HostStatus>) {
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(size.width).height(size.height).clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            if (actions.isEmpty()) {
                Text(
                    "Pick an action",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (grid) {
                val columns = (size.width / CellSize).toInt().coerceAtLeast(1)
                Column {
                    actions.chunked(columns).forEach { row ->
                        Row {
                            row.forEach { action ->
                                PreviewCell(action, statuses, Modifier.width(size.width / columns).height(CellSize).padding(2.dp))
                            }
                        }
                    }
                }
            } else {
                PreviewCell(actions.first(), statuses, Modifier.width(size.width).height(size.height))
            }
        }
    }
}

@Composable
private fun PreviewCell(action: Action, statuses: List<HostStatus>, modifier: Modifier) {
    val foreground = MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(cellIcon(action, RunState.IDLE)), null, tint = foreground, modifier = Modifier.size(20.dp))
        Text(
            action.label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
        )
        statuses.find { it.hostId == action.hostId }?.let {
            Text(statusLine(it), color = foreground, fontSize = 11.sp, maxLines = 1)
        }
    }
}
