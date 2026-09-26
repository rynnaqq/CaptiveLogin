package com.example.hotspotportal.net

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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.InetAddress

/** Live hotspot state: which interface carries the tethering network, and its address. */
data class HotspotState(
    val interfaceName: String,
    val gatewayIp: String,
    val prefixLength: Int,
    val ssid: String?,
) {
    val cidr: String get() = "$gatewayIp/$prefixLength"
    val inOctets: ByteArray get() = InetAddress.getByName(gatewayIp).address
}

/**
 * Finds the hotspot interface by name.
 *
 * Never hardcodes 192.168.43.1 — Android 11+ randomises the tethering subnet,
 * and the gateway IP is what every redirect and DNS answer depends on.
 * Interface names are OEM-specific (AOSP wlan1, Samsung swlan0, some
 * Qualcomm ap0), so we probe a regex and fall back to the administrator
 * override from Settings.
 */
class HotspotDetector(
    private val shell: ShellRunner,
    private val ifaceOverride: () -> String?,
) {
    private val _state = MutableStateFlow<HotspotState?>(null)
    val state: StateFlow<HotspotState?> = _state.asStateFlow()

    /**
     * Soft-AP interface names, most specific first.
     *
     * `wlan0` is last on purpose: on MediaTek it carries station traffic, so
     * it is only correct when the OEM genuinely puts the AP there - never
     * preferred over a dedicated `ap*`/`swlan*` name.
     */
    private val ifacePriority = listOf(
        "ap0", "ap1", "ap2",
        "softap0", "softap1",
        "swlan0", "swlan1", "swlan2", "swlan3",
        "wlan1", "wlan2",
        "wlan0",
    )

    suspend fun refresh(): HotspotState? = withContext(Dispatchers.IO) {
        val override = ifaceOverride()?.takeIf { it.isNotBlank() }
        val found = override?.let { name ->
            parseIpv4(shell.exec(listOf("ip", "-4", "addr", "show", "dev", name)).stdout)
                ?.let { HotspotState(name, it.first, it.second, ssidFor(name)) }
        } ?: detectViaShell() ?: detectViaInterfaces()
        _state.value = found
        found
    }

    fun startPolling(scope: kotlinx.coroutines.CoroutineScope, intervalMs: Long = 5_000) {
        scope.launch(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                refresh()
                delay(intervalMs)
            }
        }
    }

    private suspend fun detectViaShell(): HotspotState? {
        val out = shell.exec(listOf("ip", "-4", "addr", "show")).stdout
        val addrs = parseIfaceAddrs(out)
        val pick = pickByPriority(addrs.keys) ?: return null
        val (ip, prefix) = addrs.getValue(pick)
        return HotspotState(pick, ip, prefix, ssidFor(pick))
    }

    /**
     * No-root fallback for the name lookup only. Prefix length is not available
     * here, so 24 - the Android hotspot default - is assumed.
     */
    private suspend fun detectViaInterfaces(): HotspotState? = withContext(Dispatchers.IO) {
        val up = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && it.inetAddresses.toList().any { a -> a is Inet4Address } }
                .mapNotNull { ni ->
                    val addr = ni.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull()
                    addr?.hostAddress?.let { ni.name to (it to 24) }
                }
                .toMap()
        }.getOrDefault(emptyMap())

        val pick = pickByPriority(up.keys) ?: return@withContext null
        val (ip, prefix) = up.getValue(pick)
        HotspotState(interfaceName = pick, gatewayIp = ip, prefixLength = prefix, ssid = ssidFor(pick))
    }

    /** Highest-priority candidate present, so the choice never depends on the
     *  order `ip` or the JVM happened to enumerate interfaces in. */
    private fun pickByPriority(names: Collection<String>): String? {
        val present = names.map { it.substringBefore('@') }.toSet()
        return ifacePriority.firstOrNull { it in present }
    }

    @Volatile private var ssidCache: Pair<String, String?>? = null

    /** `dumpsys wifi` is a very large dump; read it only when the AP changes. */
    private suspend fun ssidFor(iface: String): String? {
        ssidCache?.takeIf { it.first == iface }?.let { return it.second }
        val ssid = readSsid()
        ssidCache = iface to ssid
        return ssid
    }

    /** Best-effort SSID for the dashboard. Not every build exposes it. */
    private suspend fun readSsid(): String? {
        val out = shell.exec(listOf("dumpsys", "wifi")).stdout
        val m = Regex("""(?m)^\s*(?:mWifiInfo SSID:|SSID:)\s*"?([^",\n]+)"?""").find(out)
        return m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    private fun parseIpv4(output: String): Pair<String, Int>? = parseIpv4Line(output)

    companion object {
        /**
         * Interface name -> (ipv4, prefix) from `ip -4 addr show` output.
         *
         * The header is `16: ccmni2: <NOARP,UP,LOWER_UP> mtu 1500 ...` followed by
         * an indented `inet 10.0.0.1/8`, so the name comes from the header and the
         * address from the following lines. Splitting the header on whitespace
         * cannot work: the name carries its own colon (`ccmni2:`), never a bare
         * `:` in the second field.
         */
        fun parseIfaceAddrs(output: String): Map<String, Pair<String, Int>> {
            val header = Regex("""^\d+:\s+([^:@\s]+)""")
            val result = LinkedHashMap<String, Pair<String, Int>>()
            var name: String? = null
            for (line in output.lineSequence()) {
                val h = header.find(line)
                if (h != null) {
                    name = h.groupValues[1]
                    continue
                }
                val current = name ?: continue
                parseIpv4Line(line)?.let { result[current] = it }
            }
            return result
        }

        private val inet = Regex("""inet (\d+\.\d+\.\d+\.\d+)/(\d+)""")

        fun parseIpv4Line(line: String): Pair<String, Int>? =
            inet.find(line)?.let { it.groupValues[1] to it.groupValues[2].toInt() }
    }
}
