package app.swilk.wolmobile.run

import android.content.Context
import app.swilk.wolmobile.data.Host
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.db
import app.swilk.wolmobile.net.pingReachable
import app.swilk.wolmobile.net.tcpReachable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Probes the status port, or pings without one, at the address [route] picks. Null if the host isn't checked. */
suspend fun checkHost(context: Context, host: Host): Boolean? {
    if (!host.checkStatus) {
        context.db.status().clear(host.id)
        return null
    }
    val route = route(context, host) ?: return null
    // Ping can't be bound to Wi-Fi; at home the LAN address is normally routed there anyway.
    val up = host.statusPort?.let { tcpReachable(route.network, route.address, it, timeoutMs = 2_000) } ?: pingReachable(route.network, route.address)
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
