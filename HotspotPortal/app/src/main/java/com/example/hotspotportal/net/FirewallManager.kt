package com.example.hotspotportal.net

import com.example.hotspotportal.root.ShellRunner

/**
 * Installs and removes the portal's iptables/ip6tables rules.
 *
 * Everything the app owns lives in two private chains
 * (`PORTAL_AUTH` in nat, `PORTAL_AUTH6` in filter) with a single jump from
 * the built-in chains. Teardown is therefore exact: flush our chains, remove
 * our two jumps, delete our chains. No built-in chain is ever flushed.
 */
class FirewallManager(private val shell: ShellRunner) {

    private var activeIface: String? = null
    private val allowedV4 = LinkedHashSet<String>()
    private val allowedV6 = LinkedHashSet<String>()

    /**
     * Resolved command prefix per family. The defaults are what the unit tests
     * assert; [resolveTools] replaces them when the bare binary is absent, so
     * every later rule - including teardown - uses the tool that was found.
     */
    private var v4Tool = listOf("iptables")
    private var v6Tool = listOf("ip6tables")

    val authorizedMacs: Set<String> get() = allowedV4.toSet()

    /**
     * Locates a usable iptables AND ip6tables, or explains what was tried.
     *
     * A bare `iptables` in PATH is not a given. Android 10+ dropped the
     * binaries from most system images, Magisk ships its own copy, and
     * KernelSU ships none at all - a KernelSU phone with no iptables module has
     * no `iptables` anywhere, which is spec gotcha 11.10. busybox is the
     * near-universal fallback and is probed as a two-word prefix.
     *
     * Both families are required: without ip6tables the v6 chain cannot be
     * installed and clients bypass the portal over IPv6 (spec 5.4).
     *
     * Returns null on success, or a reason naming every candidate and the fix.
     */
    suspend fun resolveTools(): String? {
        val v4 = probe(V4_CANDIDATES)
        val v6 = probe(V6_CANDIDATES)
        if (v4 != null && v6 != null) {
            v4Tool = v4
            v6Tool = v6
            return null
        }
        return buildString {
            if (v4 == null) append("no iptables. ")
            if (v6 == null) append("no ip6tables. ")
            append(probeFailures.joinToString("; "))
            append(". Install an iptables module or a busybox module, then reactivate.")
        }
    }

    /** Why each candidate was rejected - a bare "not found" hides PATH problems. */
    private val probeFailures = mutableListOf<String>()

    private suspend fun probe(candidates: List<List<String>>): List<String>? {
        for (candidate in candidates) {
            val r = shell.exec(candidate + "--version")
            if (r.ok) return candidate
            val why = r.stderr.trim().ifEmpty { r.stdout.trim() }.ifEmpty { "exit ${r.exitCode}" }
            probeFailures += "${candidate.joinToString(" ")} -> ${why.lineSequence().first()}"
        }
        return null
    }

    /**
     * Flushes and rebuilds from scratch every time, so a partially-installed
     * state from a previous run self-heals.
     */
    suspend fun install(state: HotspotState, httpPort: Int, dnsPort: Int, tlsPort: Int) {
        val iface = state.interfaceName
        activeIface = iface
        allowedV4.clear()
        allowedV6.clear()

        // "-N" fails when the chain already exists; that is the expected
        // re-activation path, so the failure is deliberately ignored.
        run(FirewallCommands.createV4Chain())
        run(FirewallCommands.createV6Chain())

        // Drop the jump first so the flush cannot be bypassed mid-rewrite.
        run(FirewallCommands.deleteV4Jump(iface))
        run(FirewallCommands.deleteV6Jump(iface))

        run(FirewallCommands.flushV4Chain())
        run(FirewallCommands.flushV6Chain())

        FirewallCommands.baseChain(iface, httpPort, dnsPort, tlsPort).forEach { run(it) }
        run(FirewallCommands.rejectAllV6())

        // -C first: never duplicate the jump if a third party already flushed
        // and we are re-arming on a live interface.
        if (!run(FirewallCommands.checkV4Jump(iface)).ok) run(FirewallCommands.insertV4Jump(iface))
        if (!run(FirewallCommands.checkV6Jump(iface)).ok) run(FirewallCommands.insertV6Jump(iface))
    }

    suspend fun allowMac(mac: String) {
        if (allowedV4.add(mac)) run(FirewallCommands.allowMacV4(mac))
        if (allowedV6.add(mac)) run(FirewallCommands.allowMacV6(mac))
    }

    suspend fun removeMac(mac: String) {
        if (allowedV4.remove(mac)) run(FirewallCommands.removeMacV4(mac))
        if (allowedV6.remove(mac)) run(FirewallCommands.removeMacV6(mac))
    }

    /** Idempotent: safe to call from onDestroy, onTaskRemoved, and Stop. */
    suspend fun uninstall() {
        val iface = activeIface
        allowedV4.forEach { run(FirewallCommands.removeMacV4(it)) }
        allowedV6.forEach { run(FirewallCommands.removeMacV6(it)) }
        allowedV4.clear()
        allowedV6.clear()

        if (iface != null) {
            // -D fails when the jump is already gone, which is fine.
            run(FirewallCommands.deleteV4Jump(iface))
            run(FirewallCommands.deleteV6Jump(iface))
        }
        run(FirewallCommands.flushV4Chain())
        run(FirewallCommands.flushV6Chain())
        run(FirewallCommands.deleteV4Chain())
        run(FirewallCommands.deleteV6Chain())
        activeIface = null
    }

    /**
     * Watchdog: confirms our jump rules are still present. A third party (or
     * a network-stack reset) can flush the table under us, which would
     * silently unblock every client.
     */
    suspend fun rulesIntact(): Boolean {
        val iface = activeIface ?: return true
        return run(FirewallCommands.checkV4Jump(iface)).ok && run(FirewallCommands.checkV6Jump(iface)).ok
    }

    /**
     * Swaps the placeholder binary at index 0 for the tool [resolveTools]
     * actually found. FirewallCommands always emits a plain `iptables` /
     * `ip6tables` head, so redirecting here keeps the tested arg arrays intact
     * while every real command - installs, whitelists and teardown alike - goes
     * to the resolved binary.
     */
    private suspend fun run(args: List<String>) = shell.exec(
        when (args.firstOrNull()) {
            "ip6tables" -> v6Tool + args.drop(1)
            "iptables" -> v4Tool + args.drop(1)
            else -> args
        }
    )

    private companion object {
        val V4_CANDIDATES = listOf(
            listOf("iptables"),
            listOf("/system/bin/iptables"),
            listOf("/system/sbin/iptables"),
            listOf("/sbin/iptables"),
            listOf("/vendor/bin/iptables"),
            listOf("busybox", "iptables"),
        )
        val V6_CANDIDATES = listOf(
            listOf("ip6tables"),
            listOf("/system/bin/ip6tables"),
            listOf("/system/sbin/ip6tables"),
            listOf("/sbin/ip6tables"),
            listOf("/vendor/bin/ip6tables"),
            listOf("busybox", "ip6tables"),
        )
    }
}
