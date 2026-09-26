package com.example.hotspotportal.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Identical apart from -I -> -D: the rule must match to be deletable.
        assertEquals(insert.toSet(), remove.toSet())
        assertEquals("-I", insert[5])
        assertEquals("-D", remove[5])
        // The position argument is present on insert and absent on delete.
        assertEquals("1", insert[6])
        assertEquals("PORTAL_AUTH", remove[5 + 1])
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
}
