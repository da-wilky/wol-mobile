package app.swilk.wolmobile.ui

import android.app.KeyguardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.navigation3.runtime.NavKey
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.Login
import app.swilk.wolmobile.data.SshLogin
import app.swilk.wolmobile.data.normalizeMac
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.Outcome
import app.swilk.wolmobile.run.onActionsChanged
import app.swilk.wolmobile.run.pinShortcut
import app.swilk.wolmobile.run.scanHostKey
import app.swilk.wolmobile.run.testLogin
import app.swilk.wolmobile.ssh.fingerprint
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private fun cascadeWarning(count: Int) =
    if (count == 0) "This cannot be undone." else "This also deletes $count action(s) and relay(s) that use it."

@Composable
fun HostEditor(id: Long?, open: (NavKey) -> Unit, done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val dao = db.hosts()
    val networks by db.networks().all().collectAsStateWithLifecycle(emptyList())
    val keys by db.keys().all().collectAsStateWithLifecycle(emptyList())
    val logins by remember(id) { id?.let { db.logins().forHost(it) } ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var networkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var localAddress by rememberSaveable { mutableStateOf("") }
    var vpnAddress by rememberSaveable { mutableStateOf("") }
    var mac by rememberSaveable { mutableStateOf("") }
    var wolPort by rememberSaveable { mutableStateOf("9") }
    var sshPort by rememberSaveable { mutableStateOf("22") }
    var hostKey by rememberSaveable { mutableStateOf<String?>(null) }
    var checkStatus by rememberSaveable { mutableStateOf(true) }
    var statusPort by rememberSaveable { mutableStateOf("") }
    var usages by rememberSaveable { mutableIntStateOf(0) }
    var pinning by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(networks) {
        if (id == null && networkId == null) networkId = networks.firstOrNull()?.id
    }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        usages = dao.actionCount(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; networkId = it.networkId
            localAddress = it.localAddress.orEmpty(); vpnAddress = it.vpnAddress.orEmpty()
            mac = it.mac.orEmpty(); wolPort = it.wolPort.toString()
            sshPort = it.sshPort.toString(); hostKey = it.hostKey
            checkStatus = it.checkStatus; statusPort = it.statusPort?.toString().orEmpty()
        }
        loaded = true
    }

    val normalizedMac = normalizeMac(mac)
    val macValid = mac.isEmpty() || normalizedMac != null
    val hasAddress = localAddress.isNotBlank() || vpnAddress.isNotBlank()
    val statusValid = !checkStatus || hasAddress && (statusPort.isBlank() || parsePort(statusPort) != null)
    val valid = loaded && name.isNotBlank() && macValid && (mac.isEmpty() || networkId != null) &&
        parsePort(wolPort) != null && parsePort(sshPort) != null && statusValid
    fun current() = Host(
        id = id ?: 0, name = name.trim(), networkId = networkId, mac = normalizedMac, wolPort = parsePort(wolPort)!!,
        localAddress = localAddress.trim().ifEmpty { null }, vpnAddress = vpnAddress.trim().ifEmpty { null },
        sshPort = parsePort(sshPort)!!, hostKey = hostKey, checkStatus = checkStatus, statusPort = parsePort(statusPort),
    )

    EditorScaffold(
        title = if (id == null) "New host" else "Edit host",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                dao.upsert(current())
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
            Picker("Network", networks.map { it.id to it.name }, networkId, noneLabel = "None") { networkId = it }
            Hint("The network decides how the host is reached: directly at home, through the VPN or relays elsewhere.")
        }
        FormSection("Addresses") {
            Field(
                "Local address", localAddress, KeyboardType.Uri, placeholder = "192.168.1.20",
                supporting = "Used over Wi-Fi while you're on the host's network",
            ) { localAddress = it }
            Field(
                "VPN address", vpnAddress, KeyboardType.Uri, placeholder = "nas.netbird.cloud",
                supporting = "Used everywhere else",
            ) { vpnAddress = it }
        }
        FormSection("Wake-on-LAN", "Optional. Needs a network: the magic packet is broadcast there or sent by its relays.") {
            MacField(mac) { mac = it }
            if (mac.isNotEmpty() && networkId == null) Text("Pick a network to wake this host.", color = MaterialTheme.colorScheme.error)
            Field("UDP port", wolPort, KeyboardType.Number, error = parsePort(wolPort) == null) { wolPort = it }
        }
        FormSection(
            "SSH",
            "Pin the host key once while the host is reachable; every login must then present the same one. Logins only work once it's pinned.",
        ) {
            Field("Port", sshPort, KeyboardType.Number, error = parsePort(sshPort) == null) { sshPort = it }
            val pinned = hostKey
            if (pinned == null) {
                Text("Host key not pinned", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                Text(fingerprint(pinned), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            pinError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = valid && hasAddress && !pinning,
                    onClick = {
                        pinning = true
                        pinError = null
                        scope.launch {
                            val scan = scanHostKey(context, current())
                            when {
                                scan == null -> pinError = "Set an address first"
                                scan.hostKey == null -> pinError = "No SSH server answered at ${scan.address}:$sshPort"
                                else -> hostKey = scan.hostKey
                            }
                            pinning = false
                        }
                    },
                ) { Text(if (pinned == null) "Pin host key" else "Pin again") }
                if (pinning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                if (pinned != null) TextButton(onClick = { hostKey = null; pinError = null }) { Text("Forget") }
            }
        }
        FormSection("Logins", "A user and key to log in with, used by SSH actions and relays. Use one per key, e.g. one for wakeonlan and one for poweroff.") {
            if (id == null) {
                Hint("Save the host to add logins.")
            } else {
                logins.forEach { login ->
                    Surface(
                        onClick = { open(EditLogin(id, login.id)) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(login.name, style = MaterialTheme.typography.bodyLarge)
                            Hint("${login.user} · ${keys.find { it.id == login.keyId }?.let { "key ${it.name}" } ?: "no key"}")
                        }
                    }
                }
                OutlinedButton(onClick = { open(EditLogin(id, null)) }) { Text("Add login") }
            }
        }
        FormSection("Status check") {
            SwitchRow("Check status", checkStatus, "Pings the host, or connects to a TCP port if you set one, e.g. 22 when ping is blocked.") { checkStatus = it }
            if (checkStatus) {
                if (!hasAddress) Text("Set an address to check.", color = MaterialTheme.colorScheme.error)
                Field("TCP port", statusPort, KeyboardType.Number, error = !statusValid, placeholder = "Empty to ping") { statusPort = it }
            }
        }
    }
}

@Composable
fun LoginEditor(hostId: Long, id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val db = context.db
    val dao = db.logins()
    val scope = rememberCoroutineScope()
    val keys by db.keys().all().collectAsStateWithLifecycle(emptyList())
    var host by remember { mutableStateOf<Host?>(null) }
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var keyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var usages by rememberSaveable { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Outcome?>(null) }
    LaunchedEffect(id) {
        host = db.hosts().get(hostId)
        if (id == null) return@LaunchedEffect
        usages = dao.usageCount(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.login?.let { name = it.name; user = it.user; keyId = it.keyId }
        loaded = true
    }

    val valid = loaded && name.isNotBlank() && user.isNotBlank() && keys.any { it.id == keyId }
    fun current() = SshLogin(id ?: 0, hostId, name.trim(), user.trim(), keyId!!)

    EditorScaffold(
        title = if (id == null) "New login" else "Edit login",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                dao.upsert(current())
                onActionsChanged(context)
                done()
            }
        },
        deleteWarning = id?.let { cascadeWarning(usages) },
        onDelete = {
            scope.launch {
                dao.get(id!!)?.let { dao.delete(it.login) }
                onActionsChanged(context)
                done()
            }
        },
    ) {
        FormSection("Login on ${host?.name.orEmpty()}") {
            Field("Name", name, placeholder = "poweroff") { name = it }
            Field("User", user) { user = it }
            if (keys.isEmpty()) Text("Create a key in the Keys tab first.", color = MaterialTheme.colorScheme.error)
            Picker("Key", keys.map { it.id to it.name }, keyId) { keyId = it }
        }
        FormSection("Test", "Logs in and runs `true` at the address the host is reachable at right now.") {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = valid && host != null && !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = testLogin(context, Login(current(), host!!))
                            testing = false
                        }
                    },
                ) { Text("Test login") }
                if (testing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
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
    val logins by db.logins().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var label by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(ActionType.WAKE) }
    var hostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var loginId by rememberSaveable { mutableStateOf<Long?>(null) }
    var followStatus by rememberSaveable { mutableStateOf(false) }
    var command by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var shortcut by rememberSaveable { mutableStateOf(false) }
    var unlock by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) {
        if (loaded) return@LaunchedEffect
        dao.get(id!!)?.let {
            label = it.label; type = it.type; hostId = it.hostId; loginId = it.loginId
            followStatus = it.type == ActionType.SSH && it.hostId != null
            command = it.command.orEmpty(); confirm = it.confirmBeforeRun; shortcut = it.shortcut; unlock = it.requireUnlock; position = it.position
        }
        loaded = true
    }

    val wakeable = hosts.filter { it.mac != null && it.networkId != null }
    val login = logins.find { it.login.id == loginId }
    val valid = loaded && label.isNotBlank() && when (type) {
        ActionType.WAKE -> wakeable.any { it.id == hostId }
        ActionType.SSH -> login != null && command.isNotBlank()
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
                        hostId = if (wake) hostId else login!!.host.id.takeIf { followStatus },
                        loginId = loginId.takeUnless { wake },
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
        when (type) {
            ActionType.WAKE -> FormSection(
                "Wake",
                "On the host's network the magic packet is sent directly; elsewhere the network's relays send it.",
            ) {
                if (wakeable.isEmpty()) Text("Add a host with a MAC address and a network first.", color = MaterialTheme.colorScheme.error)
                Picker("Host", wakeable.map { it.id to it.name }, hostId) { hostId = it }
            }
            ActionType.SSH -> FormSection("Command") {
                if (logins.isEmpty()) Text("Add a login to a host first.", color = MaterialTheme.colorScheme.error)
                Picker("Login", logins.map { it.login.id to it.label }, loginId) { loginId = it }
                Field("Command", command, singleLine = false, placeholder = "sudo poweroff") { command = it }
                SwitchRow(
                    "Follow host status", followStatus,
                    "For commands that turn the host off or on: its status is checked until it changes.",
                ) { followStatus = it }
            }
        }
        FormSection("Behaviour") {
            SwitchRow("Confirm before running", confirm, "Ask before running from the app, widgets, tiles and shortcuts.") { confirm = it }
            SwitchRow("Require unlock", unlock, "Ask for fingerprint or screen lock before running, from anywhere.") { unlock = it }
            if (unlock && !context.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                Text("This phone has no screen lock, so the action won't run until you set one.", color = MaterialTheme.colorScheme.error)
            }
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
