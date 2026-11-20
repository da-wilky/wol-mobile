package app.swilk.wolmobile.run

import android.content.Context
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.db
import app.swilk.wolmobile.net.tcpReachable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Probes without binding to Wi-Fi so it works both at home and through the VPN. Null if no probe is configured. */
suspend fun checkHost(context: Context, host: Host): Boolean? {
    val address = host.probeAddress ?: return null
    val up = tcpReachable(null, address, host.probePort!!, timeoutMs = 2_000)
    context.db.status().set(HostStatus(host.id, up, System.currentTimeMillis()))
    return up
}

suspend fun checkAllHosts(context: Context) {
    coroutineScope {
        context.db.hosts().list().map { async { checkHost(context, it) } }.awaitAll()
    }
    refreshWidgets(context)
}

fun formatAge(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}
