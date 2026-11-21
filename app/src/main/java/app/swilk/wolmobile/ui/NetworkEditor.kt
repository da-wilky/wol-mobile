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
import app.swilk.wolmobile.data.Network
import app.swilk.wolmobile.data.setRelays
import app.swilk.wolmobile.db
import kotlinx.coroutines.launch

@Composable
fun NetworkEditor(id: Long?, done: () -> Unit) {
    val context = LocalContext.current
    val dao = context.db.networks()
    val scope = rememberCoroutineScope()
    val targets by context.db.targets().all().collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var probeAddress by rememberSaveable { mutableStateOf("") }
    var probePort by rememberSaveable { mutableStateOf("22") }
    var broadcast by rememberSaveable { mutableStateOf("255.255.255.255") }
    var relayCommand by rememberSaveable { mutableStateOf(DEFAULT_RELAY_COMMAND) }
    var relays by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var hosts by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        hosts = dao.hostNames(id)
        if (loaded) return@LaunchedEffect
        dao.get(id)?.let {
            name = it.name; probeAddress = it.probeAddress.orEmpty(); probePort = it.probePort.toString()
            broadcast = it.broadcastAddress; relayCommand = it.relayCommand
        }
        relays = dao.relayIds(id)
        loaded = true
    }

    val probeValid = probeAddress.isBlank() || parsePort(probePort) != null
    val valid = loaded && name.isNotBlank() && probeValid && broadcast.isNotBlank() && relayCommand.isNotBlank()

    EditorScaffold(
        title = if (id == null) "New network" else "Edit network",
        onBack = done,
        canSave = valid,
        onSave = {
            scope.launch {
                val network = Network(
                    id = id ?: 0, name = name.trim(),
                    probeAddress = probeAddress.trim().ifEmpty { null }, probePort = parsePort(probePort) ?: 22,
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
            if (hosts.isNotEmpty()) Hint("Hosts: ${hosts.joinToString()}. Move them to another network to delete this one.")
        }
        FormSection(
            "Presence",
            "Before waking a host, the app connects to this address over Wi-Fi, bypassing the VPN. " +
                "If it answers, you're on this network and the magic packet is sent directly. Leave empty to always send directly.",
        ) {
            Field("LAN address", probeAddress, KeyboardType.Uri, placeholder = "e.g. the Pi, 192.168.1.2") { probeAddress = it }
            Field("TCP port", probePort, KeyboardType.Number, error = !probeValid) { probePort = it }
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
            relays.forEachIndexed { i, targetId ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}. ${targets.find { it.id == targetId }?.name.orEmpty()}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    IconButton(onClick = { relays = relays.swap(i, i - 1) }, enabled = i > 0) {
                        Icon(painterResource(R.drawable.ic_arrow_up), "Move up")
                    }
                    IconButton(onClick = { relays = relays.swap(i, i + 1) }, enabled = i < relays.lastIndex) {
                        Icon(painterResource(R.drawable.ic_arrow_down), "Move down")
                    }
                    IconButton(onClick = { relays = relays - targetId }) {
                        Icon(painterResource(R.drawable.ic_close), "Remove")
                    }
                }
            }
            val available = targets.filter { it.id !in relays }
            if (targets.isEmpty()) {
                Hint("Add the relay as an SSH target first.")
            } else if (available.isNotEmpty()) {
                Picker("Add relay", available.map { it.id to it.name }, null) { picked -> picked?.let { relays = relays + it } }
            }
        }
    }
}

private fun <T> List<T>.swap(i: Int, j: Int) = toMutableList().also { it[i] = this[j]; it[j] = this[i] }
