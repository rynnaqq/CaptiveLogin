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

    val authorizedMacs: Set<String> get() = allowedV4.toSet()

    suspend fun hasIptables(): Boolean =
        shell.toolVersion("iptables") != null || shell.exec(listOf("iptables", "-w", "5", "-t", "nat", "-L", "-n")).ok

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

    private suspend fun run(args: List<String>) = shell.exec(args)
}
