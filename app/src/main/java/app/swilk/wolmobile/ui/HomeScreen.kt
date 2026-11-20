package app.swilk.wolmobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.data.RunState
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.checkAllHosts
import app.swilk.wolmobile.run.formatAge
import kotlinx.coroutines.launch

private val tabs = listOf("Actions", "Hosts", "SSH", "Keys")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(open: (NavKey) -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val actions by db.actions().all().collectAsStateWithLifecycle(emptyList())
    val hosts by db.hosts().all().collectAsStateWithLifecycle(emptyList())
    val targets by db.targets().all().collectAsStateWithLifecycle(emptyList())
    val keys by db.keys().all().collectAsStateWithLifecycle(emptyList())
    val states by db.runs().states().collectAsStateWithLifecycle(emptyList())
    val statuses by db.status().all().collectAsStateWithLifecycle(emptyList())
    var refreshing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<Action?>(null) }
    var newKey by remember { mutableStateOf(false) }

    suspend fun refresh() {
        refreshing = true
        checkAllHosts(context)
        refreshing = false
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake on LAN") },
                actions = {
                    TextButton(onClick = { open(LogRoute) }) { Text("Log") }
                    TextButton(onClick = { open(SettingsRoute) }) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (tab) {
                    0 -> open(EditAction(null))
                    1 -> open(EditHost(null))
                    2 -> open(EditTarget(null))
                    else -> newKey = true
                }
            }) { Icon(painterResource(R.drawable.ic_add), contentDescription = "Add") }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refresh() } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { i, title ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                        }
                    }
                }
                when (tab) {
                    0 -> items(actions, key = { it.id }) { action ->
                        val detail = when (action.type) {
                            ActionType.WAKE -> "Wake ${hosts.find { it.id == action.hostId }?.name}"
                            ActionType.SSH -> "${targets.find { it.id == action.targetId }?.name}: ${action.command}"
                        }
                        val state = states.find { it.actionId == action.id }?.state ?: RunState.IDLE
                        ListItem(
                            supportingContent = { Text(detail, maxLines = 1) },
                            trailingContent = {
                                when (state) {
                                    RunState.RUNNING -> Text("…")
                                    RunState.OK -> Text("✓", color = MaterialTheme.colorScheme.primary)
                                    RunState.FAILED -> Text("✗", color = MaterialTheme.colorScheme.error)
                                    RunState.IDLE -> IconButton(onClick = {
                                        if (action.confirmBeforeRun) confirming = action else startRun(context, action)
                                    }) { Icon(painterResource(R.drawable.ic_play), "Run") }
                                }
                            },
                            modifier = Modifier.clickable { open(EditAction(action.id)) },
                        ) { Text(action.label) }
                    }
                    1 -> items(hosts, key = { it.id }) { host ->
                        ListItem(
                            supportingContent = { Text(host.mac) },
                            trailingContent = { statuses.find { it.hostId == host.id }?.let { StatusText(it) } },
                            modifier = Modifier.clickable { open(EditHost(host.id)) },
                        ) { Text(host.name) }
                    }
                    2 -> items(targets, key = { it.id }) { target ->
                        ListItem(
                            supportingContent = { Text("${target.user}@${target.address}:${target.port}", maxLines = 1) },
                            modifier = Modifier.clickable { open(EditTarget(target.id)) },
                        ) { Text(target.name) }
                    }
                    else -> items(keys, key = { it.id }) { key ->
                        ListItem(
                            supportingContent = { Text(key.publicKey, maxLines = 1) },
                            modifier = Modifier.clickable { open(KeyDetail(key.id)) },
                        ) { Text(key.name) }
                    }
                }
            }
        }
    }

    confirming?.let { action ->
        ConfirmRunDialog(
            action,
            onConfirm = { startRun(context, action); confirming = null },
            onDismiss = { confirming = null },
        )
    }
    if (newKey) NewKeyDialog(onDone = { newKey = false })
}

@Composable
private fun StatusText(status: HostStatus) {
    val age = formatAge(System.currentTimeMillis() - status.checkedAt)
    if (status.up) {
        Text("● up · $age", color = MaterialTheme.colorScheme.primary)
    } else {
        Text("○ down · $age", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
