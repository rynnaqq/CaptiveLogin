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

    /**
     * Resolves a client IP to its MAC, or null if the kernel has not learned it
     * yet. Matching goes through [canonicalIp] on both sides: a client that
     * reaches the portal over IPv6 sends a different spelling than `ip neigh`
     * prints, and a literal compare would silently miss it.
     */
    suspend fun macFor(ip: String): String? {
        refresh()
        val want = canonicalIp(ip) ?: return null
        return _clients.value.firstOrNull { canonicalIp(it.info.ip) == want }?.info?.mac
    }

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

    companion object {
        /**
         * One spelling for an address, so the kernel's `ip neigh` output and
         * the HTTP layer's peer address compare equal. IPv6 in particular is
         * written in many equivalent forms, and a scope suffix appears on
         * link-local addresses. Non-numeric input is rejected before
         * InetAddress sees it, so this can never become a DNS lookup.
         */
        fun canonicalIp(raw: String): String? {
            val s = raw.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
            if (s.isEmpty() || !NUMERIC.matches(s)) return null
            return runCatching {
                java.net.InetAddress.getByName(s).address.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }.getOrNull()
        }

        /**
         * Parses `ip neigh show` output into clients.
         *
         * Two things had to change, both found on the device:
         *
         * - No field-count heuristic. The app runs `ip neigh show dev <if>`,
         *   and because the interface is implied the kernel omits `dev <if>`
         *   from every line, leaving 4 fields. Requiring 5 rejected every
         *   single entry, IPv4 included, so no client was ever known.
         * - IPv6 entries are kept. Dropping every address containing ':'
         *   looked like removing link-local noise, but a client whose OS
         *   prefers IPv6 - Windows does - has its only MAC mapping on a global
         *   v6 address, so the connectivity probe fired straight after login
         *   could not be matched to a MAC, the server treated the guest as
         *   unauthorized, and the login page came back forever.
         *
         * Only link-local (fe80::/10), multicast and scope-suffixed entries are
         * dropped: those are neighbour-discovery artefacts, not clients.
         */
        fun parseNeighbours(output: String): List<ClientInfo> = output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val lladdr = parts.indexOf("lladdr")
                if (lladdr < 0 || lladdr + 1 >= parts.size) return@mapNotNull null
                val ip = parts[0]
                if (ip.contains('%')) return@mapNotNull null
                val lower = ip.lowercase()
                if (lower.startsWith("fe80:") || lower.startsWith("ff")) return@mapNotNull null
                val mac = parts[lladdr + 1]
                if (!MAC.matches(mac)) return@mapNotNull null
                ClientInfo(ip, mac.lowercase(), null, parts.last().uppercase())
            }
            .toList()

        private val MAC = Regex("""[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}""")
        private val NUMERIC = Regex("""[0-9a-fA-F:.%]+""")
    }
}

/**
 * One row per device, for display only.
 *
 * A MAC legitimately appears under several addresses - a laptop with an IPv4
 * lease and two global IPv6 addresses is three neighbour entries - so the
 * Clients list is grouped by MAC and the IPv4 entry wins where there is one,
 * because that is the address an admin reads out to a guest. Ungrouped, one
 * guest shows three rows and the LazyColumn is handed duplicate keys, which
 * Compose treats as fatal.
 *
 * The monitor itself keeps every address: macFor() has to resolve whichever
 * address the guest actually connected from, IPv6 included.
 */
fun groupByDevice(clients: List<ObservedClient>): List<ObservedClient> =
    clients.groupBy { it.info.mac }.map { (_, rows) ->
        rows.firstOrNull { ':' !in it.info.ip } ?: rows.first()
    }
