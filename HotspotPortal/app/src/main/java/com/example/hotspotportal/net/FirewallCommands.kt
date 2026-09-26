package com.example.hotspotportal.net

/**
 * Builds the exact iptables/ip6tables argument arrays the firewall needs.
 *
 * Kept separate from [FirewallManager] so the arrays can be asserted in unit
 * tests without a rooted device. Every method returns a list of arguments,
 * never a single string: libsu execs argv directly, so a MAC address or
 * interface name can never be re-interpreted as shell syntax.
 */
object FirewallCommands {

    const val CHAIN_V4 = "PORTAL_AUTH"
    const val CHAIN_V6 = "PORTAL_AUTH6"

    /** Matches a MAC with the lowest possible cost, valid in nat + filter. */
    private fun macSource(mac: String) = listOf("-m", "mac", "--mac-source", mac)

    /** The base chain contents applied to every unauthenticated client. */
    fun baseChain(iface: String, httpPort: Int, dnsPort: Int, tlsPort: Int): List<List<String>> = listOf(
        // DHCP must never be redirected or clients cannot join or renew leases.
        listOf("iptables", "-w", "5", "-t", "nat", "-A", CHAIN_V4, "-p", "udp", "--dport", "67", "-j", "RETURN"),
        listOf("iptables", "-w", "5", "-t", "nat", "-A", CHAIN_V4, "-p", "udp", "--dport", "53", "-j", "REDIRECT", "--to-ports", "$dnsPort"),
        listOf("iptables", "-w", "5", "-t", "nat", "-A", CHAIN_V4, "-p", "tcp", "--dport", "80", "-j", "REDIRECT", "--to-ports", "$httpPort"),
        // TLS is reset by accept-and-close rather than REJECT: REJECT is not a
        // valid nat PREROUTING target (spec gotcha 4).
        listOf("iptables", "-w", "5", "-t", "nat", "-A", CHAIN_V4, "-p", "tcp", "--dport", "443", "-j", "REDIRECT", "--to-ports", "$tlsPort"),
        // Catch-alls. Without these a blocked client still reaches the internet
        // over QUIC/UDP-443 or any non-HTTP port.
        listOf("iptables", "-w", "5", "-t", "nat", "-A", CHAIN_V4, "-p", "tcp", "-j", "REDIRECT", "--to-ports", "$httpPort"),
        listOf("iptables", "-w", "5", "-t", "nat", "-A", CHAIN_V4, "-p", "udp", "-j", "REDIRECT", "--to-ports", "$dnsPort"),
    )

    fun createV4Chain() = listOf("iptables", "-w", "5", "-t", "nat", "-N", CHAIN_V4)
    fun flushV4Chain() = listOf("iptables", "-w", "5", "-t", "nat", "-F", CHAIN_V4)
    fun deleteV4Chain() = listOf("iptables", "-w", "5", "-t", "nat", "-X", CHAIN_V4)

    /** -C is the idempotency check: only insert the PREROUTING jump if absent. */
    fun checkV4Jump(iface: String) =
        listOf("iptables", "-w", "5", "-t", "nat", "-C", "PREROUTING", "-i", iface, "-j", CHAIN_V4)

    fun insertV4Jump(iface: String) =
        listOf("iptables", "-w", "5", "-t", "nat", "-I", "PREROUTING", "1", "-i", iface, "-j", CHAIN_V4)

    fun deleteV4Jump(iface: String) =
        listOf("iptables", "-w", "5", "-t", "nat", "-D", "PREROUTING", "-i", iface, "-j", CHAIN_V4)

    /**
     * Authorises a MAC. Inserted at position 1 so the RETURN is evaluated
     * before the DHCP/DNS/redirect rules below it.
     */
    fun allowMacV4(mac: String) =
        listOf("iptables", "-w", "5", "-t", "nat", "-I", CHAIN_V4, "1") + macSource(mac) + listOf("-j", "RETURN")

    fun removeMacV4(mac: String) =
        listOf("iptables", "-w", "5", "-t", "nat", "-D", CHAIN_V4) + macSource(mac) + listOf("-j", "RETURN")

    fun createV6Chain() = listOf("ip6tables", "-w", "5", "-t", "filter", "-N", CHAIN_V6)
    fun flushV6Chain() = listOf("ip6tables", "-w", "5", "-t", "filter", "-F", CHAIN_V6)
    fun deleteV6Chain() = listOf("ip6tables", "-w", "5", "-t", "filter", "-X", CHAIN_V6)

    fun checkV6Jump(iface: String) =
        listOf("ip6tables", "-w", "5", "-t", "filter", "-C", "FORWARD", "-i", iface, "-j", CHAIN_V6)

    fun insertV6Jump(iface: String) =
        listOf("ip6tables", "-w", "5", "-t", "filter", "-I", "FORWARD", "1", "-i", iface, "-j", CHAIN_V6)

    fun deleteV6Jump(iface: String) =
        listOf("ip6tables", "-w", "5", "-t", "filter", "-D", "FORWARD", "-i", iface, "-j", CHAIN_V6)

    fun allowMacV6(mac: String) =
        listOf("ip6tables", "-w", "5", "-t", "filter", "-I", CHAIN_V6, "1") + macSource(mac) + listOf("-j", "RETURN")

    fun removeMacV6(mac: String) =
        listOf("ip6tables", "-w", "5", "-t", "filter", "-D", CHAIN_V6) + macSource(mac) + listOf("-j", "RETURN")

    /** Terminal v6 rule: everything not already RETURNed is rejected. */
    fun rejectAllV6() = listOf("ip6tables", "-w", "5", "-t", "filter", "-A", CHAIN_V6, "-j", "REJECT")
}
