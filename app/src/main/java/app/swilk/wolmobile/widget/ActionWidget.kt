package app.swilk.wolmobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.swilk.wolmobile.WolApp
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.data.RunState
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.enqueueRun
import app.swilk.wolmobile.run.formatAge
import app.swilk.wolmobile.run.syncPeriodicStatus
import app.swilk.wolmobile.ui.runIntent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private val ActionIdKey = ActionParameters.Key<Long>("actionId")
private val CellSize = 72.dp

private class Item(val action: Action, val state: RunState, val status: HostStatus?)
private class WidgetData(val configured: Boolean, val showStatus: Boolean, val items: List<Item>)

abstract class ActionWidget(private val grid: Boolean) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = context.db
        val data = combine(
            db.widgets().flow(appWidgetId), db.actions().all(), db.runs().states(), db.status().all(),
        ) { config, actions, states, statuses ->
            val byId = actions.associateBy { it.id }
            val items = config?.actionIdList.orEmpty().mapNotNull { byId[it] }.map { action ->
                Item(
                    action,
                    states.find { it.actionId == action.id }?.state ?: RunState.IDLE,
                    statuses.find { it.hostId == action.hostId },
                )
            }
            WidgetData(items.isNotEmpty(), config?.showStatus == true, items)
        }
        provideContent {
            val widget by data.collectAsState(initial = null)
            GlanceTheme {
                val current = widget ?: return@GlanceTheme
                if (!current.configured) {
                    Unconfigured(appWidgetId)
                } else if (grid) {
                    Grid(current)
                } else {
                    Cell(current.items.first(), current.showStatus, GlanceModifier.fillMaxSize())
                }
            }
        }
    }
}

class SingleWidget : ActionWidget(grid = false)
class GridWidget : ActionWidget(grid = true)

@Composable
private fun Grid(data: WidgetData) {
    val columns = (LocalSize.current.width / CellSize).toInt().coerceAtLeast(1)
    LazyVerticalGrid(GridCells.Fixed(columns), GlanceModifier.fillMaxSize()) {
        items(data.items, itemId = { it.action.id }) { item ->
            Box(GlanceModifier.padding(2.dp)) {
                Cell(item, data.showStatus, GlanceModifier.fillMaxWidth().height(CellSize - 4.dp))
            }
        }
    }
}

@Composable
private fun Cell(item: Item, showStatus: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val (background, foreground) = when (item.state) {
        RunState.IDLE -> colors.secondaryContainer to colors.onSecondaryContainer
        RunState.RUNNING -> colors.tertiaryContainer to colors.onTertiaryContainer
        RunState.OK -> colors.primary to colors.onPrimary
        RunState.FAILED -> colors.error to colors.onError
    }
    val onClick = if (item.action.confirmBeforeRun) {
        actionStartActivity(runIntent(context, item.action.id))
    } else {
        actionRunCallback<RunCallback>(actionParametersOf(ActionIdKey to item.action.id))
    }
    val detail = when (item.state) {
        RunState.RUNNING -> "…"
        RunState.OK -> "✓"
        RunState.FAILED -> "✗"
        RunState.IDLE -> item.status?.takeIf { showStatus }?.let {
            val age = formatAge(System.currentTimeMillis() - it.checkedAt)
            if (it.up) "● up · $age" else "○ down · $age"
        }
    }
    Column(
        modifier.background(background).cornerRadius(16.dp).clickable(onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            item.action.label,
            maxLines = 2,
            style = TextStyle(color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
        detail?.let { Text(it, maxLines = 1, style = TextStyle(color = foreground, fontSize = 11.sp)) }
    }
}

@Composable
private fun Unconfigured(appWidgetId: Int) {
    val context = LocalContext.current
    val intent = Intent(context, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surfaceVariant).cornerRadius(16.dp)
            .clickable(actionStartActivity(intent)).padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Tap to set up",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
}

class RunCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        enqueueRun(context, parameters[ActionIdKey]!!)
    }
}

abstract class ActionWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as WolApp
        app.scope.launch {
            context.db.widgets().delete(appWidgetIds.toList())
            syncPeriodicStatus(context)
        }
    }
}

class SingleWidgetReceiver : ActionWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleWidget()
}

class GridWidgetReceiver : ActionWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GridWidget()
}
