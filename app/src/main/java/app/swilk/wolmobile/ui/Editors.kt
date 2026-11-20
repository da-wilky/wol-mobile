package app.swilk.wolmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.SshTarget
import app.swilk.wolmobile.data.normalizeMac
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.onActionsChanged
import app.swilk.wolmobile.ssh.fingerprint
import kotlinx.coroutines.launch

private fun cascadeWarning(count: Int) =
    if (count == 0) "This cannot be undone." else "This also deletes $count action(s) that use it."

@Composable
fun HostEditor(id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val dao = context.db.hosts()
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var mac by rememberSaveable { mutableStateOf("") }
    var broadcast by rememberSaveable { mutableStateOf("255.255.255.255") }
    var wolPort by rememberSaveable { mutableStateOf("9") }
    var probeAddress by rememberSaveable { mutableStateOf("") }
    var probePort by rememberSaveable { mutableStateOf("") }
    var usages by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        usages = dao.actionCount(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; mac = it.mac; broadcast = it.broadcastAddress; wolPort = it.wolPort.toString()
            probeAddress = it.probeAddress.orEmpty(); probePort = it.probePort?.toString().orEmpty()
        }
        loaded = true
    }

    val normalizedMac = normalizeMac(mac)
    val probeValid = probeAddress.isBlank() && probePort.isBlank() ||
        probeAddress.isNotBlank() && parsePort(probePort) != null
    val valid = loaded && name.isNotBlank() && normalizedMac != null && broadcast.isNotBlank() &&
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
                        broadcastAddress = broadcast.trim(), wolPort = parsePort(wolPort)!!,
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
        Field("Name", name) { name = it }
        Field("MAC address", mac, error = mac.isNotEmpty() && normalizedMac == null) { mac = it }
        Field(
            "Broadcast address", broadcast, KeyboardType.Uri,
            supporting = "255.255.255.255, or your subnet's broadcast, e.g. 192.168.1.255",
        ) { broadcast = it }
        Field("WoL port", wolPort, KeyboardType.Number, error = parsePort(wolPort) == null) { wolPort = it }
        SectionTitle("Status check (optional)")
        Hint("A TCP port that answers while the host is up, e.g. 22. Must be reachable from the phone at home and over the VPN.")
        Field("Address", probeAddress, KeyboardType.Uri) { probeAddress = it }
        Field("TCP port", probePort, KeyboardType.Number, error = !probeValid) { probePort = it }
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
    var usages by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        usages = dao.actionCount(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; address = it.address; port = it.port.toString(); user = it.user
            keyId = it.keyId; hostKey = it.hostKey
        }
        loaded = true
    }

    val valid = loaded && name.isNotBlank() && address.isNotBlank() && parsePort(port) != null &&
        user.isNotBlank() && keys.any { it.id == keyId }

    EditorScaffold(
        title = if (id == null) "New SSH target" else "Edit SSH target",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                val previous = id?.let { dao.get(it) }
                val sameEndpoint = previous?.address == address.trim() && previous.port == parsePort(port)
                // A pin can arrive from a run while editing, so take the stored one unless the user forgot it.
                val pinned = if (sameEndpoint && hostKey != null) previous.hostKey else null
                dao.upsert(SshTarget(id ?: 0, name.trim(), address.trim(), parsePort(port)!!, user.trim(), keyId!!, pinned))
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
        Field("Name", name) { name = it }
        Field("Address", address, KeyboardType.Uri) { address = it }
        Field("Port", port, KeyboardType.Number, error = parsePort(port) == null) { port = it }
        Field("User", user) { user = it }
        if (keys.isEmpty()) Text("Create a key in the Keys tab first.", color = MaterialTheme.colorScheme.error)
        Picker("Key", keys.map { it.id to it.name }, keyId) { keyId = it }
        SectionTitle("Host key")
        val pinned = hostKey
        if (pinned == null) {
            Hint("Not pinned yet. The key presented on the first successful connection is trusted and pinned.")
        } else {
            Text(fingerprint(pinned), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { hostKey = null }) { Text("Forget host key") }
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
    LaunchedEffect(id) {
        if (loaded) return@LaunchedEffect
        dao.get(id!!)?.let {
            label = it.label; type = it.type; hostId = it.hostId; targetId = it.targetId
            command = it.command.orEmpty(); confirm = it.confirmBeforeRun; shortcut = it.shortcut
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
                        confirmBeforeRun = confirm, shortcut = shortcut,
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
        Field("Label", label) { label = it }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ActionType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t },
                    shape = SegmentedButtonDefaults.itemShape(i, ActionType.entries.size),
                ) { Text(if (t == ActionType.WAKE) "Wake host" else "SSH command") }
            }
        }
        val hostOptions = hosts.map { it.id to it.name }
        when (type) {
            ActionType.WAKE -> {
                if (hosts.isEmpty()) Text("Add a host first.", color = MaterialTheme.colorScheme.error)
                Picker("Host", hostOptions, hostId) { hostId = it }
                Hint("At home a magic packet is sent directly; elsewhere the relay from Settings is used.")
            }
            ActionType.SSH -> {
                if (targets.isEmpty()) Text("Add an SSH target first.", color = MaterialTheme.colorScheme.error)
                Picker("SSH target", targets.map { it.id to it.name }, targetId) { targetId = it }
                Field("Command", command, singleLine = false) { command = it }
                Picker("Affected host (optional)", hostOptions, hostId, noneLabel = "None") { hostId = it }
                Hint("If set, the host's status is followed after the command, e.g. until it is down after a poweroff.")
            }
        }
        SwitchRow("Confirm before running", confirm) { confirm = it }
        SwitchRow("Show in app shortcuts", shortcut) { shortcut = it }
    }
}
