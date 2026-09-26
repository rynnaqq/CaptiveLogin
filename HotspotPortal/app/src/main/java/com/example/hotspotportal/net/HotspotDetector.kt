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

    // SPEC 5.1: match the OEM soft-AP interface names.
    private val ifaceRegex = Regex("^(wlan1|wlan2|swlan0|swlan1|ap0|softap0|swlan\\d)$")

    suspend fun refresh(): HotspotState? = withContext(Dispatchers.IO) {
        val override = ifaceOverride()?.takeIf { it.isNotBlank() }
        val found = override?.let { name ->
            parseIpv4(shell.exec(listOf("ip", "-4", "addr", "show", "dev", name)).stdout)
                ?.let { HotspotState(name, it.first, it.second, readSsid()) }
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
        var current: String? = null
        for (line in out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("inet ")) {
                parseIpv4(current ?: return null)?.let { (ip, prefix) ->
                    return HotspotState(current!!, ip, prefix, readSsid())
                }
            }
            // "1234: name: <if> ..." — index 3 is the interface name.
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 4 && parts[1] == ":" && parts[2].endsWith(":")) {
                current = parts[3]
            }
        }
        return null
    }

    /**
     * No-root fallback for the name lookup only. Cannot read the address from
     * procfs without a shell, so the IP still comes from the shell path.
     * Prefix length is not available here — 24 is the Android hotspot default.
     */
    private suspend fun detectViaInterfaces(): HotspotState? = withContext(Dispatchers.IO) {
        val candidate = runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.firstOrNull { ni ->
                    ifaceRegex.matches(ni.name) && ni.isUp &&
                        ni.inetAddresses.toList().any { it is Inet4Address }
                }
        }.getOrNull() ?: return@withContext null

        val addr = candidate.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull()
            ?: return@withContext null
        HotspotState(
            interfaceName = candidate.name,
            gatewayIp = addr.hostAddress ?: return@withContext null,
            prefixLength = 24,
            ssid = readSsid(),
        )
    }

    /** Best-effort SSID for the dashboard. Not every build exposes it. */
    private suspend fun readSsid(): String? {
        val out = shell.exec(listOf("dumpsys", "wifi")).stdout
        val m = Regex("""(?m)^\s*(?:mWifiInfo SSID:|SSID:)\s*"?([^",\n]+)"?""").find(out)
        return m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    private fun parseIpv4(output: String): Pair<String, Int>? {
        val m = Regex("""inet (\d+\.\d+\.\d+\.\d+)/(\d+)""").find(output) ?: return null
        return m.groupValues[1] to m.groupValues[2].toInt()
    }
}
