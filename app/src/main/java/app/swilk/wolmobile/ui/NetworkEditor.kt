package app.swilk.wolmobile.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.DEFAULT_RELAY_COMMAND
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.Network
import app.swilk.wolmobile.data.setRelays
import app.swilk.wolmobile.db
import kotlinx.coroutines.launch

@Composable
fun NetworkEditor(id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val dao = context.db.networks()
    val scope = rememberCoroutineScope()
    val logins by context.db.logins().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var presenceHostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var broadcast by rememberSaveable { mutableStateOf("255.255.255.255") }
    var relayCommand by rememberSaveable { mutableStateOf(DEFAULT_RELAY_COMMAND) }
    var relays by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var hosts by remember { mutableStateOf(emptyList<Host>()) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        hosts = context.db.hosts().onNetwork(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; presenceHostId = it.presenceHostId
            broadcast = it.broadcastAddress; relayCommand = it.relayCommand
        }
        relays = dao.relayIds(id)
        loaded = true
    }

    val valid = loaded && name.isNotBlank() && broadcast.isNotBlank() && relayCommand.isNotBlank()

    EditorScaffold(
        title = if (id == null) "New network" else "Edit network",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                val network = Network(
                    id = id ?: 0, name = name.trim(), presenceHostId = presenceHostId,
                    broadcastAddress = broadcast.trim(), relayCommand = relayCommand.trim(),
                )
                val saved = dao.upsert(network).takeIf { it != -1L } ?: id!!
                dao.setRelays(saved, relays)
                done()
            }
        },
        deleteWarning = id?.takeIf { hosts.isEmpty() }?.let { "Its relay list is removed as well." },
        onDelete = {
            scope.launch {
                dao.get(id!!)?.let { dao.delete(it) }
                done()
            }
        },
    ) {
        FormSection("Network") {
            Field("Name", name, placeholder = "Home") { name = it }
            if (hosts.isNotEmpty()) Hint("Hosts: ${hosts.joinToString { it.name }}. Move them to another network to delete this one.")
        }
        FormSection(
            "Presence",
            "You're on this network when this host answers at its local address and SSH port over Wi-Fi, bypassing the VPN. " +
                "If its host key is pinned it must present it, so another Wi-Fi where the same address answers doesn't count. " +
                "Then hosts are reached at their local addresses and magic packets are sent directly.",
        ) {
            if (hosts.isEmpty()) Hint("Add hosts with a local address to this network first.")
            Picker(
                "Presence host", hosts.filter { it.localAddress != null }.map { it.id to it.name }, presenceHostId,
                noneLabel = "None: always on this network",
            ) { presenceHostId = it }
        }
        FormSection("Wake") {
            Field(
                "Broadcast address", broadcast, KeyboardType.Uri,
                supporting = "Or the subnet's broadcast, e.g. 192.168.1.255",
            ) { broadcast = it }
            Field("Relay command", relayCommand, supporting = "Runs on the relay. Placeholders: {mac}, {broadcast}, {port}") { relayCommand = it }
        }
        FormSection(
            "Relays",
            "Away from this network, the relays are tried in this order until one runs the command successfully.",
        ) {
            relays.forEachIndexed { i, loginId ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}. ${logins.find { it.login.id == loginId }?.label.orEmpty()}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    IconButton(onClick = { relays = relays.swap(i, i - 1) }, enabled = i > 0) {
                        Icon(painterResource(R.drawable.ic_arrow_up), "Move up")
                    }
                    IconButton(onClick = { relays = relays.swap(i, i + 1) }, enabled = i < relays.lastIndex) {
                        Icon(painterResource(R.drawable.ic_arrow_down), "Move down")
                    }
                    IconButton(onClick = { relays = relays - loginId }) {
                        Icon(painterResource(R.drawable.ic_close), "Remove")
                    }
                }
            }
            val available = logins.filter { it.login.id !in relays }
            if (logins.isEmpty()) {
                Hint("Add a login to the relay host first, e.g. the Pi.")
            } else if (available.isNotEmpty()) {
                Picker("Add relay", available.map { it.login.id to it.label }, null) { picked -> picked?.let { relays = relays + it } }
            }
        }
    }
}

private fun <T> List<T>.swap(i: Int, j: Int) = toMutableList().also { it[i] = this[j]; it[j] = this[i] }
