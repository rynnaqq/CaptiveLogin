package com.example.hotspotportal.clients

import com.example.hotspotportal.root.ShellRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClientInfo(
    val ip: String,
    val mac: String,
    val hostname: String? = null,
    /** REACHABLE / STALE / DELAY / PROBE mean present; FAILED means gone. */
    val state: String = "UNKNOWN",
) {
    /** STALE alone is not absence (spec gotcha 9) — it still counts as present. */
    val present: Boolean get() = state != "FAILED" && state != "INCOMPLETE"
}

data class ObservedClient(
    val info: ClientInfo,
    val username: String? = null,
    val expiresAt: Long? = null,
) {
    val authorized: Boolean get() = username != null
}

/**
 * Polls the neighbour table to build the IP -> MAC map.
 *
 * Login requests carry only a source IP, so the MAC whitelist rule cannot be
 * written until we know which MAC owns that address. The kernel already knows
 * it; we just have to read it.
 */
class ClientMonitor(private val shell: ShellRunner) {

    private val _clients = MutableStateFlow<List<ObservedClient>>(emptyList())
    val clients: StateFlow<List<ObservedClient>> = _clients.asStateFlow()

    private var iface: String? = null

    /** Live sessions, so the Clients tab can show who is logged in. */
    @Volatile
    private var sessionLookup: (String) -> Pair<String?, Long?> = { null to null }

    fun bind(iface: String) {
        this.iface = iface
    }

    fun onSessionsChanged(lookup: (String) -> Pair<String?, Long?>) {
        sessionLookup = lookup
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val dev = iface ?: return@withContext
        val entries = parseNeighbours(shell.exec(listOf("ip", "neigh", "show", "dev", dev)).stdout)
        if (entries.isEmpty()) {
            val fallback = parseArp()
            if (fallback.isNotEmpty()) publish(fallback)
            return@withContext
        }
        publish(entries)
    }

    fun startPolling(scope: kotlinx.coroutines.CoroutineScope, intervalMs: Long = 10_000) {
        scope.launch(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                refresh()
                delay(intervalMs)
            }
        }
    }

    private fun publish(entries: List<ClientInfo>) {
        _clients.value = entries.map { c ->
            val (user, exp) = sessionLookup(c.mac)
            ObservedClient(c, user, exp)
        }
    }

    /** Resolves a client IP to its MAC, or null if the kernel has not learned it yet. */
    suspend fun macFor(ip: String): String? {
        refresh()
        return _clients.value.firstOrNull { it.info.ip == ip }?.info?.mac
    }

    private fun parseNeighbours(output: String): List<ClientInfo> = output.lineSequence()
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return@mapNotNull null
            val ip = parts[0]
            if (ip.contains(':')) return@mapNotNull null // link-local IPv6 noise
            val mac = parts[parts.indexOfFirst { it == "lladdr" } + 1]
                .takeIf { parts.contains("lladdr") } ?: return@mapNotNull null
            val state = parts.lastOrNull()?.uppercase() ?: "UNKNOWN"
            ClientInfo(ip, mac.lowercase(), null, state)
        }
        .toList()

    /** Fallback for kernels without `ip neigh` (some older/vendor builds). */
    private fun parseArp(): List<ClientInfo> {
        val content = runCatching { java.io.File("/proc/net/arp").readText() }.getOrElse { return emptyList() }
        return content.lineSequence().drop(1).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 4) return@mapNotNull null
            val mac = p[3]
            if (mac == "00:00:00:00:00:00") return@mapNotNull null
            ClientInfo(p[0], mac.lowercase(), null, if (p[2] == "0x2") "REACHABLE" else "STALE")
        }.toList()
    }
}
