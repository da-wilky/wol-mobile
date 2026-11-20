package app.swilk.wolmobile.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.RunLog
import app.swilk.wolmobile.db
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private fun dayLabel(context: android.content.Context, at: Long): String = when {
    DateUtils.isToday(at) -> "Today"
    DateUtils.isToday(at + TimeUnit.DAYS.toMillis(1)) -> "Yesterday"
    else -> DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(focus: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val dao = context.db.runs()
    val scope = rememberCoroutineScope()
    val entries by dao.log().collectAsStateWithLifecycle(null)
    var expanded by rememberSaveable { mutableStateOf(focus) }
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                navigationIcon = {
                    IconButton(onClick = done) { Icon(painterResource(R.drawable.ic_back), "Back") }
                },
                actions = {
                    if (!entries.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(painterResource(R.drawable.ic_delete), "Clear log") }
                    }
                },
            )
        },
    ) { padding ->
        val list = entries ?: return@Scaffold
        if (list.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(R.drawable.ic_history, "Nothing has run yet", "Every run is recorded here with its result and output.")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.groupBy { dayLabel(context, it.at) }.forEach { (day, dayEntries) ->
                stickyHeader(key = day) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            day,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(dayEntries, key = { it.id }) { entry ->
                    LogEntry(entry, expanded == entry.id) { expanded = entry.id.takeUnless { it == expanded } }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear log?") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { dao.clearLog() } }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogEntry(entry: RunLog, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(context, entry.at, DateUtils.FORMAT_SHOW_TIME)
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (entry.ok) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (entry.ok) R.drawable.ic_check_circle else R.drawable.ic_error),
                    if (entry.ok) "Succeeded" else "Failed",
                    tint = if (entry.ok) upColor else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(entry.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            if (entry.output.isNotBlank()) {
                if (expanded) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer {
                            Text(
                                entry.output,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }
                } else {
                    Text("Show output", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
