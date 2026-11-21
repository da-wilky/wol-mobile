package app.swilk.wolmobile.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.SshTarget
import app.swilk.wolmobile.data.normalizeMac
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.Outcome
import app.swilk.wolmobile.run.onActionsChanged
import app.swilk.wolmobile.run.pinShortcut
import app.swilk.wolmobile.run.testSsh
import app.swilk.wolmobile.ssh.fingerprint
import kotlinx.coroutines.launch

private fun cascadeWarning(count: Int) =
    if (count == 0) "This cannot be undone." else "This also deletes $count action(s) that use it."

@Composable
fun HostEditor(id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val dao = context.db.hosts()
    val networks by context.db.networks().all().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var mac by rememberSaveable { mutableStateOf("") }
    var networkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var wolPort by rememberSaveable { mutableStateOf("9") }
    var probeAddress by rememberSaveable { mutableStateOf("") }
    var probePort by rememberSaveable { mutableStateOf("") }
    var usages by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(networks) {
        if (id == null && networkId == null) networkId = networks.firstOrNull()?.id
    }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        usages = dao.actionCount(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; mac = it.mac; networkId = it.networkId; wolPort = it.wolPort.toString()
            probeAddress = it.probeAddress.orEmpty(); probePort = it.probePort?.toString().orEmpty()
        }
        loaded = true
    }

    val normalizedMac = normalizeMac(mac)
    val probeValid = probePort.isBlank() || probeAddress.isNotBlank() && parsePort(probePort) != null
    val valid = loaded && name.isNotBlank() && normalizedMac != null && networks.any { it.id == networkId } &&
        parsePort(wolPort) != null && probeValid

    EditorScaffold(
        title = if (id == null) "New host" else "Edit host",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                dao.upsert(
                    Host(
                        id = id ?: 0, name = name.trim(), mac = normalizedMac!!,
                        networkId = networkId!!, wolPort = parsePort(wolPort)!!,
                        probeAddress = probeAddress.trim().ifEmpty { null }, probePort = parsePort(probePort),
                    ),
                )
                onActionsChanged(context)
                done()
            }
        },
        deleteWarning = id?.let { cascadeWarning(usages) },
        onDelete = {
            scope.launch {
                dao.get(id!!)?.let { dao.delete(it) }
                onActionsChanged(context)
                done()
            }
        },
    ) {
        FormSection("Host") {
            Field("Name", name, placeholder = "nas") { name = it }
        }
        FormSection("Wake-on-LAN") {
            MacField(mac) { mac = it }
            Picker("Network", networks.map { it.id to it.name }, networkId) { networkId = it }
            Hint("The network decides how the packet gets there: directly, or through one of its relays.")
            Field("UDP port", wolPort, KeyboardType.Number, error = parsePort(wolPort) == null) { wolPort = it }
        }
        FormSection(
            "Status check",
            "Optional. Pings the address, or connects to a TCP port if you set one, e.g. 22 when ping is blocked. Must be reachable at home and over the VPN.",
        ) {
            Field("Address", probeAddress, KeyboardType.Uri, placeholder = "192.168.1.20") { probeAddress = it }
            Field("TCP port", probePort, KeyboardType.Number, error = !probeValid, placeholder = "Empty to ping") { probePort = it }
        }
    }
}

@Composable
fun TargetEditor(id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val dao = db.targets()
    val scope = rememberCoroutineScope()
    val keys by db.keys().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("22") }
    var user by rememberSaveable { mutableStateOf("") }
    var keyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var hostKey by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedEndpoint by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedHostKey by rememberSaveable { mutableStateOf<String?>(null) }
    var usages by rememberSaveable { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Outcome?>(null) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        usages = dao.actionCount(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; address = it.address; port = it.port.toString(); user = it.user
            keyId = it.keyId; hostKey = it.hostKey
            loadedEndpoint = "${it.address}:${it.port}"; loadedHostKey = it.hostKey
        }
        loaded = true
    }

    val valid = loaded && name.isNotBlank() && address.isNotBlank() && parsePort(port) != null &&
        user.isNotBlank() && keys.any { it.id == keyId }
    fun current(pinned: String?) = SshTarget(id ?: 0, name.trim(), address.trim(), parsePort(port)!!, user.trim(), keyId!!, pinned)
    // A pin belongs to an endpoint: moving the target to another address drops a pin that wasn't renewed by a test.
    fun pinToSave() = if ("${address.trim()}:${parsePort(port)}" != loadedEndpoint && hostKey == loadedHostKey) null else hostKey

    EditorScaffold(
        title = if (id == null) "New SSH target" else "Edit SSH target",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                dao.upsert(current(pinToSave()))
                onActionsChanged(context)
                done()
            }
        },
        deleteWarning = id?.let { cascadeWarning(usages) },
        onDelete = {
            scope.launch {
                dao.get(id!!)?.let { dao.delete(it) }
                onActionsChanged(context)
                done()
            }
        },
    ) {
        FormSection("Connection") {
            Field("Name", name, placeholder = "pi") { name = it }
            Field("Address", address, KeyboardType.Uri, placeholder = "192.168.1.2 or a NetBird name") { address = it }
            Field("Port", port, KeyboardType.Number, error = parsePort(port) == null) { port = it }
            Field("User", user) { user = it }
        }
        FormSection("Authentication") {
            if (keys.isEmpty()) Text("Create a key in the Keys tab first.", color = MaterialTheme.colorScheme.error)
            Picker("Key", keys.map { it.id to it.name }, keyId) { keyId = it }
        }
        FormSection(
            "Host key",
            "The server's key is trusted on the first connection and checked on every later one.",
        ) {
            val pinned = hostKey
            if (pinned == null) {
                Text("Not pinned yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(fingerprint(pinned), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            testResult?.let { result ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painterResource(if (result.ok) R.drawable.ic_check_circle else R.drawable.ic_error), null,
                        tint = if (result.ok) upColor else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(result.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = valid && !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = testSsh(context, current(hostKey)) { hostKey = it }
                            testing = false
                        }
                    },
                ) { Text("Test connection") }
                if (testing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                if (pinned != null) TextButton(onClick = { hostKey = null; testResult = null }) { Text("Forget") }
            }
        }
    }
}

@Composable
fun ActionEditor(id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val dao = db.actions()
    val scope = rememberCoroutineScope()
    val hosts by db.hosts().all().collectAsStateWithLifecycle(emptyList())
    val targets by db.targets().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var label by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(ActionType.WAKE) }
    var hostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var targetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var command by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var shortcut by rememberSaveable { mutableStateOf(false) }
    var unlock by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) {
        if (loaded) return@LaunchedEffect
        dao.get(id!!)?.let {
            label = it.label; type = it.type; hostId = it.hostId; targetId = it.targetId
            command = it.command.orEmpty(); confirm = it.confirmBeforeRun; shortcut = it.shortcut; unlock = it.requireUnlock; position = it.position
        }
        loaded = true
    }

    val valid = loaded && label.isNotBlank() && when (type) {
        ActionType.WAKE -> hosts.any { it.id == hostId }
        ActionType.SSH -> targets.any { it.id == targetId } && command.isNotBlank()
    }

    EditorScaffold(
        title = if (id == null) "New action" else "Edit action",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                val wake = type == ActionType.WAKE
                dao.upsert(
                    Action(
                        id = id ?: 0, label = label.trim(), type = type,
                        hostId = hostId, targetId = targetId.takeUnless { wake },
                        command = command.trim().takeUnless { wake },
                        confirmBeforeRun = confirm, shortcut = shortcut, requireUnlock = unlock,
                        position = position ?: dao.nextPosition(),
                    ),
                )
                onActionsChanged(context)
                done()
            }
        },
        deleteWarning = id?.let { "Widgets and tiles using this action stop working." },
        onDelete = {
            scope.launch {
                dao.get(id!!)?.let { dao.delete(it) }
                onActionsChanged(context)
                done()
            }
        },
    ) {
        FormSection("Action") {
            Field("Label", label, placeholder = "Wake NAS") { label = it }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ActionType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, ActionType.entries.size),
                        icon = {
                            Icon(
                                painterResource(if (t == ActionType.WAKE) R.drawable.ic_power else R.drawable.ic_terminal), null,
                                Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        },
                    ) { Text(if (t == ActionType.WAKE) "Wake host" else "SSH command") }
                }
            }
        }
        val hostOptions = hosts.map { it.id to it.name }
        when (type) {
            ActionType.WAKE -> FormSection(
                "Wake",
                "On the host's network the magic packet is sent directly; elsewhere the network's relays send it.",
            ) {
                if (hosts.isEmpty()) Text("Add a host first.", color = MaterialTheme.colorScheme.error)
                Picker("Host", hostOptions, hostId) { hostId = it }
            }
            ActionType.SSH -> FormSection("Command") {
                if (targets.isEmpty()) Text("Add an SSH target first.", color = MaterialTheme.colorScheme.error)
                Picker("SSH target", targets.map { it.id to it.name }, targetId) { targetId = it }
                Field("Command", command, singleLine = false, placeholder = "sudo poweroff") { command = it }
                Picker("Affected host (optional)", hostOptions, hostId, noneLabel = "None") { hostId = it }
                Hint("If set, the host's status is followed after the command, e.g. until it is down after a poweroff.")
            }
        }
        FormSection("Behaviour") {
            SwitchRow("Confirm before running", confirm, "Ask before running from the app, widgets, tiles and shortcuts.") { confirm = it }
            SwitchRow("Require unlock", unlock, "Ask for fingerprint or screen lock before running, from anywhere.") { unlock = it }
            SwitchRow("App shortcut", shortcut, "Show when long-pressing the app icon.") { shortcut = it }
            if (id != null) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val pinned = dao.get(id)?.let { pinShortcut(context, it) } ?: false
                        if (!pinned) Toast.makeText(context, "This launcher can't add shortcuts", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Add to home screen") }
            }
        }
    }
}
