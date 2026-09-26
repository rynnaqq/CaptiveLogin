package com.example.hotspotportal.net

import com.example.hotspotportal.root.ShellResult
import com.example.hotspotportal.root.ShellRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test 5 (spec 13): the command builder produces the exact expected argv, and
 * a hostile interface or MAC cannot smuggle shell syntax through.
 */
class FirewallCommandsTest {

    @Test
    fun `whitelist rule is inserted at position 1 in the v4 chain`() {
        assertEquals(
            listOf("iptables", "-w", "5", "-t", "nat", "-I", "PORTAL_AUTH", "1",
                "-m", "mac", "--mac-source", "aa:bb:cc:dd:ee:ff", "-j", "RETURN"),
            FirewallCommands.allowMacV4("aa:bb:cc:dd:ee:ff"),
        )
    }

    @Test
    fun `removal mirrors the insert exactly`() {
        val mac = "aa:bb:cc:dd:ee:ff"
        val insert = FirewallCommands.allowMacV4(mac)
        val remove = FirewallCommands.removeMacV4(mac)

        // -I/-D sit at index 5; the "1" position argument only exists on insert
        // because -D takes no position.
        assertEquals("iptables", insert[0])
        assertEquals("-I", insert[5])
        assertEquals("PORTAL_AUTH", insert[6])
        assertEquals("1", insert[7])

        assertEquals("-D", remove[5])
        assertEquals("PORTAL_AUTH", remove[6])

        // The rest of the rule must be byte-identical or iptables will not
        // match the rule when deleting it.
        assertEquals(insert.drop(8), remove.drop(7))
    }

    @Test
    fun `ipv6 chain lives in the filter table and rejects by default`() {
        assertEquals(
            listOf("ip6tables", "-w", "5", "-t", "filter", "-N", "PORTAL_AUTH6"),
            FirewallCommands.createV6Chain(),
        )
        assertEquals(
            listOf("ip6tables", "-w", "5", "-t", "filter", "-A", "PORTAL_AUTH6", "-j", "REJECT"),
            FirewallCommands.rejectAllV6(),
        )
    }

    @Test
    fun `base chain never redirects DHCP`() {
        val base = FirewallCommands.baseChain("wlan1", 8080, 5353, 8443)
        val dhcp = base.first { it.contains("67") }
        // udp/67 must RETURN or clients cannot join or renew a lease.
        assertTrue(dhcp.contains("RETURN"))
        assertFalse(dhcp.contains("REDIRECT"))
    }

    @Test
    fun `base chain redirects the three service ports and blackholes the rest`() {
        val base = FirewallCommands.baseChain("wlan1", 8080, 5353, 8443)
        assertTrue(base.any { it.contains("5353") && it.contains("--dport") && it.contains("53") && it.contains("REDIRECT") })
        assertTrue(base.any { it.contains("8080") && it.contains("--dport") && it.contains("80") })
        assertTrue(base.any { it.contains("8443") && it.contains("--dport") && it.contains("443") })
        // Catch-alls: one TCP, one UDP, both with no --dport.
        assertTrue(base.any { it.contains("-p") && it.contains("tcp") && !it.contains("--dport") && it.contains("REDIRECT") })
        assertTrue(base.any { it.contains("-p") && it.contains("udp") && !it.contains("--dport") && it.contains("REDIRECT") })
    }

    @Test
    fun `every command waits on the xtables lock`() {
        val all = listOf(
            FirewallCommands.createV4Chain(), FirewallCommands.flushV4Chain(),
            FirewallCommands.insertV4Jump("wlan1"), FirewallCommands.allowMacV4("a:b"),
            FirewallCommands.createV6Chain(), FirewallCommands.insertV6Jump("wlan1"),
            FirewallCommands.allowMacV6("a:b"), FirewallCommands.rejectAllV6(),
        )
        assertTrue(all.all { it.contains("-w") && it[it.indexOf("-w") + 1] == "5" })
    }

    @Test
    fun `a hostile MAC stays a single argv element`() {
        // Injection attempt: if this were ever concatenated into a shell
        // string, the "; iptables -F" would flush the whole table.
        val evil = "aa:bb:cc:dd:ee:ff; iptables -F"
        val args = FirewallCommands.allowMacV4(evil)
        assertTrue(args.any { it == evil })
        // No element is a standalone shell metacharacter sequence.
        assertFalse(args.any { it == ";" })
    }

    @Test
    fun `a hostile interface name stays a single argv element`() {
        val evil = "wlan1; reboot"
        val args = FirewallCommands.insertV4Jump(evil)
        assertTrue(args.any { it == evil })
    }

    @Test
    fun `teardown removes both jumps and deletes both chains`() {
        assertEquals(
            listOf("iptables", "-w", "5", "-t", "nat", "-D", "PREROUTING", "-i", "wlan1", "-j", "PORTAL_AUTH"),
            FirewallCommands.deleteV4Jump("wlan1"),
        )
        assertEquals(
            listOf("ip6tables", "-w", "5", "-t", "filter", "-D", "FORWARD", "-i", "wlan1", "-j", "PORTAL_AUTH6"),
            FirewallCommands.deleteV6Jump("wlan1"),
        )
        assertEquals(
            listOf("iptables", "-w", "5", "-t", "nat", "-X", "PORTAL_AUTH"),
            FirewallCommands.deleteV4Chain(),
        )
        assertEquals(
            listOf("ip6tables", "-w", "5", "-t", "filter", "-X", "PORTAL_AUTH6"),
            FirewallCommands.deleteV6Chain(),
        )
    }

    /** Answers `--version` only for the tool names the device actually has. */
    private class ToolProbeShell(available: Set<String>) : ShellRunner {
        val commands = mutableListOf<List<String>>()
        private val available = available
        override suspend fun isAvailable() = true
        override suspend fun toolVersion(tool: String) = "1.0"
        override suspend fun exec(args: List<String>): ShellResult {
            commands += args
            if (args.lastOrNull() == "--version") {
                val tool = args.dropLast(1).joinToString(" ")
                return if (tool in available) ShellResult(0, "iptables v1.8.7", "") else ShellResult(127, "", "not found")
            }
            return ShellResult(0, "", "")
        }
    }

    @Test
    fun `busybox is used when the device has no bare iptables`() = runTest {
        // KernelSU with no iptables module: only busybox carries the applet.
        val shell = ToolProbeShell(setOf("busybox iptables", "busybox ip6tables"))
        val firewall = FirewallManager(shell)

        assertNull(firewall.resolveTools())

        firewall.allowMac("aa:bb:cc:dd:ee:ff")
        val v4 = shell.commands.first { it.contains("--mac-source") && it.contains("nat") }
        val v6 = shell.commands.first { it.contains("--mac-source") && it.contains("filter") }
        assertEquals(listOf("busybox", "iptables"), v4.take(2))
        assertEquals(listOf("busybox", "ip6tables"), v6.take(2))
    }

    @Test
    fun `a missing ip6tables is fatal and says what to install`() = runTest {
        val firewall = FirewallManager(ToolProbeShell(setOf("iptables")))

        val reason = firewall.resolveTools()

        // v4 alone would let clients walk past the portal over IPv6.
        assertNotNull(reason)
        assertTrue(reason!!.contains("ip6tables"))
        assertTrue(reason.contains("busybox"))
    }
}
