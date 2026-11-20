package app.swilk.wolmobile.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.data.WidgetConfig
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.enqueueStatusCheck
import app.swilk.wolmobile.run.syncPeriodicStatus
import app.swilk.wolmobile.ui.WolTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
        val grid = provider == GridWidgetReceiver::class.java.name
        enableEdgeToEdge()
        setContent {
            WolTheme {
                WidgetConfigScreen(appWidgetId, grid) {
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(appWidgetId: Int, grid: Boolean, done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val scope = rememberCoroutineScope()
    val actions by db.actions().all().collectAsStateWithLifecycle(emptyList())
    val selected = remember { mutableStateListOf<Long>() }
    var showStatus by remember { mutableStateOf(false) }
    LaunchedEffect(appWidgetId) {
        db.widgets().get(appWidgetId)?.let {
            selected.addAll(it.actionIdList)
            showStatus = it.showStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (grid) "Widget actions" else "Widget action") },
                actions = {
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                db.widgets().upsert(WidgetConfig(appWidgetId, selected.joinToString(","), showStatus))
                                syncPeriodicStatus(context)
                                if (showStatus) enqueueStatusCheck(context)
                                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                                (if (grid) GridWidget() else SingleWidget()).update(context, glanceId)
                                done()
                            }
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (actions.isEmpty()) {
                Text("Create an action in the app first.", color = MaterialTheme.colorScheme.error)
            }
            actions.forEach { action ->
                val checked = action.id in selected
                Row(
                    Modifier.fillMaxWidth().toggleable(checked) {
                        when {
                            !grid -> { selected.clear(); selected.add(action.id) }
                            checked -> selected.remove(action.id)
                            else -> selected.add(action.id)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (grid) Checkbox(checked, onCheckedChange = null) else RadioButton(checked, onClick = null)
                    Text(action.label)
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show host status", Modifier.weight(1f))
                Switch(checked = showStatus, onCheckedChange = { showStatus = it })
            }
            if (showStatus) {
                Text(
                    "Checked every ~15 minutes while this is on, and after each action.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
