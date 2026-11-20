package app.swilk.wolmobile.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.db

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(done: () -> Unit) {
    val context = LocalContext.current
    val entries by context.db.runs().log().collectAsStateWithLifecycle(emptyList())
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                navigationIcon = {
                    IconButton(onClick = done) { Icon(painterResource(R.drawable.ic_back), "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) item { ListItem { Text("Nothing has run yet.") } }
            items(entries, key = { it.id }) { entry ->
                val time = DateUtils.formatDateTime(context, entry.at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)
                ListItem(
                    leadingContent = {
                        if (entry.ok) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("✗", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    supportingContent = {
                        Column {
                            Text("$time · ${entry.message}")
                            if (expanded == entry.id && entry.output.isNotBlank()) {
                                SelectionContainer {
                                    Text(entry.output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { expanded = entry.id.takeUnless { it == expanded } },
                ) { Text(entry.label) }
            }
        }
    }
}
