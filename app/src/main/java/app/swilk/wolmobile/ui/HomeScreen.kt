package app.swilk.wolmobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.data.Network
import app.swilk.wolmobile.data.RunState
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.checkAllHosts
import app.swilk.wolmobile.run.enqueueRun
import app.swilk.wolmobile.run.onActionsChanged
import app.swilk.wolmobile.ssh.fingerprint
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class Tab(val label: String, @DrawableRes val icon: Int, val add: String) {
    ACTIONS("Actions", R.drawable.ic_power, "New action"),
    HOSTS("Hosts", R.drawable.ic_host, "New host"),
    KEYS("Keys", R.drawable.ic_key, "New key"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(open: (NavKey) -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.ACTIONS) }
    val actions by db.actions().all().collectAsStateWithLifecycle(null)
    var ordered by remember { mutableStateOf(emptyList<Action>()) }
    LaunchedEffect(actions) { actions?.let { ordered = it } }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = ordered.indexOfFirst { it.id == from.key }
        val toIndex = ordered.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) ordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    val hosts by db.hosts().all().collectAsStateWithLifecycle(emptyList())
    val networks by db.networks().all().collectAsStateWithLifecycle(emptyList())
    val relays by db.networks().allRelays().collectAsStateWithLifecycle(emptyList())
    val logins by db.logins().all().collectAsStateWithLifecycle(emptyList())
    val keys by db.keys().all().collectAsStateWithLifecycle(emptyList())
    val states by db.runs().states().collectAsStateWithLifecycle(emptyList())
    val statuses by db.status().all().collectAsStateWithLifecycle(emptyList())
    val log by db.runs().log().collectAsStateWithLifecycle(null)
    var refreshing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<Action?>(null) }
    var newKey by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var localNetwork by remember { mutableStateOf(true) }

    suspend fun refresh() {
        refreshing = true
        checkAllHosts(context)
        refreshing = false
    }
    LaunchedEffect(Unit) { refresh() }
    LifecycleResumeEffect(Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        if (granted && !localNetwork) scope.launch { refresh() }
        localNetwork = granted
        onPauseOrDispose {}
    }

    // Announce runs that finish while this screen is open; entries that existed on entry are not news.
    var lastSeen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(log) {
        val latest = log?.firstOrNull() ?: return@LaunchedEffect
        val seen = lastSeen
        lastSeen = latest.id
        if (seen == null || latest.id <= seen) return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "${latest.label}: ${latest.message}",
            actionLabel = "Details",
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) open(LogRoute(latest.id))
    }

    fun run(action: Action) {
        when {
            action.requireUnlock -> context.startActivity(runIntent(context, action.id))
            action.confirmBeforeRun -> confirming = action
            else -> enqueueRun(context, action.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake on LAN") },
                actions = {
                    IconButton(onClick = { open(LogRoute()) }) { Icon(painterResource(R.drawable.ic_history), "Log") }
                    IconButton(onClick = { open(SettingsRoute) }) { Icon(painterResource(R.drawable.ic_settings), "Settings") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(painterResource(it.icon), null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (tab) {
                        Tab.ACTIONS -> open(EditAction(null))
                        Tab.HOSTS -> open(EditHost(null))
                        Tab.KEYS -> newKey = true
                    }
                },
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text(tab.add) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!localNetwork) LocalNetworkBanner()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { refresh() } },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                val currentActions = actions ?: return@PullToRefreshBox
                val empty = when (tab) {
                    Tab.ACTIONS -> currentActions.isEmpty()
                    Tab.HOSTS -> hosts.isEmpty() && networks.isEmpty()
                    Tab.KEYS -> keys.isEmpty()
                }
                if (empty) {
                    // Scrollable so pull-to-refresh still works on an empty tab.
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (tab) {
                            Tab.ACTIONS -> if (hosts.isEmpty()) {
                                EmptyState(
                                    R.drawable.ic_power, "Nothing to run yet",
                                    "Add a host to wake it or to run commands on it.",
                                    EmptyAction("Add host") { open(EditHost(null)) },
                                )
                            } else {
                                EmptyState(
                                    R.drawable.ic_power, "No actions yet",
                                    "Actions run from here, from home screen widgets, Quick Settings tiles and app shortcuts.",
                                    EmptyAction("New action") { open(EditAction(null)) },
                                )
                            }
                            Tab.HOSTS -> EmptyState(
                                R.drawable.ic_host, "No hosts",
                                "Add a machine with its addresses, its MAC address to wake it and logins to run commands on it.",
                                EmptyAction("Add host") { open(EditHost(null)) },
                            )
                            Tab.KEYS -> EmptyState(
                                R.drawable.ic_key, "No keys",
                                "Generate an Ed25519 key, then add its public key to the server's authorized_keys.",
                                EmptyAction("Generate key") { newKey = true },
                            )
                        }
                    }
                    return@PullToRefreshBox
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (tab) {
                        Tab.ACTIONS -> items(ordered, key = { it.id }) { action ->
                            ReorderableItem(reorderState, key = action.id) { dragging ->
                                ActionCard(
                                    action,
                                    host = hosts.find { it.id == action.hostId },
                                    status = statuses.find { it.hostId == action.hostId },
                                    loginLabel = logins.find { it.login.id == action.loginId }?.label,
                                    state = states.find { it.actionId == action.id }?.state ?: RunState.IDLE,
                                    onRun = { run(action) },
                                    onEdit = { open(EditAction(action.id)) },
                                    dragging = dragging,
                                    handle = Modifier.draggableHandle(onDragStopped = {
                                        scope.launch {
                                            db.actions().update(ordered.mapIndexed { i, a -> a.copy(position = i) })
                                            onActionsChanged(context)
                                        }
                                    }),
                                )
                            }
                        }
                        Tab.HOSTS -> {
                            fun hostItems(members: List<Host>) = items(members, key = { it.id }) { host ->
                                ItemCard(
                                    R.drawable.ic_host, host.name,
                                    listOfNotNull(host.localAddress, host.vpnAddress, host.mac).joinToString(" · ").ifEmpty { "No address" },
                                    onClick = { open(EditHost(host.id)) },
                                ) { statuses.find { it.hostId == host.id }?.let { StatusChip(it) } }
                            }
                            networks.forEach { network ->
                                item(key = "network-${network.id}") {
                                    val relayNames = relays.filter { it.networkId == network.id }.mapNotNull { r -> logins.find { it.login.id == r.loginId }?.label }
                                    NetworkHeader(network, hosts.find { it.id == network.presenceHostId }?.name, relayNames) { open(EditNetwork(network.id)) }
                                }
                                val members = hosts.filter { it.networkId == network.id }
                                if (members.isEmpty()) item(key = "empty-${network.id}") { Hint("No hosts on this network yet.") }
                                hostItems(members)
                            }
                            val unassigned = hosts.filter { it.networkId == null }
                            if (unassigned.isNotEmpty()) {
                                item(key = "no-network") {
                                    Text(
                                        "No network", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                }
                                hostItems(unassigned)
                            }
                            item(key = "add-network") {
                                TextButton(onClick = { open(EditNetwork(null)) }) {
                                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                                    Text("Add network", Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Tab.KEYS -> items(keys, key = { it.id }) { key ->
                            ItemCard(
                                R.drawable.ic_key, key.name, fingerprint(key.publicKey.substringBeforeLast(' ')),
                                onClick = { open(KeyDetail(key.id)) },
                            )
                        }
                    }
                }
            }
        }
    }

    confirming?.let { action ->
        ConfirmRunDialog(
            action,
            onConfirm = { enqueueRun(context, action.id); confirming = null },
            onDismiss = { confirming = null },
        )
    }
    if (newKey) NewKeyDialog(onDone = { newKey = false })
}

@Composable
private fun LocalNetworkBanner() {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Local network access is off", style = MaterialTheme.typography.titleSmall)
                Text("Waking and status checks at home won't work without a VPN.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text("Allow") }
        }
    }
}

@Composable
private fun ActionCard(
    action: Action,
    host: Host?,
    status: HostStatus?,
    loginLabel: String?,
    state: RunState,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    dragging: Boolean,
    handle: Modifier,
) {
    val wake = action.type == ActionType.WAKE
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_drag_handle), "Reorder",
                handle.size(40.dp).padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconBadge(if (wake) R.drawable.ic_power else R.drawable.ic_terminal)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(action.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (wake) "Wakes ${host?.name}" else "On $loginLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!wake) {
                    Text(
                        action.command.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                status?.let { StatusChip(it) }
            }
            RunButton(state, onRun)
        }
    }
}

@Composable
private fun RunButton(state: RunState, onRun: () -> Unit) {
    when (state) {
        RunState.RUNNING -> CircularProgressIndicator(Modifier.size(40.dp).padding(6.dp), strokeWidth = 3.dp)
        RunState.OK -> Icon(painterResource(R.drawable.ic_check_circle), "Succeeded", tint = upColor, modifier = Modifier.size(40.dp).padding(4.dp))
        RunState.FAILED -> Icon(painterResource(R.drawable.ic_error), "Failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp).padding(4.dp))
        RunState.IDLE -> FilledIconButton(onClick = onRun) { Icon(painterResource(R.drawable.ic_play), "Run") }
    }
}

@Composable
private fun ItemCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

@Composable
private fun NetworkHeader(network: Network, presenceHost: String?, relays: List<String>, onClick: () -> Unit) {
    val summary = when {
        presenceHost == null -> "Always counts as here"
        relays.isEmpty() -> "Here when $presenceHost answers · no relays"
        else -> "Here when $presenceHost answers · relays: ${relays.joinToString()}"
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_network), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(network.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_settings), "Edit network", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
