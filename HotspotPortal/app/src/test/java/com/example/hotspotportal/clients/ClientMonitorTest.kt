package com.example.hotspotportal.clients

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Neighbour-table parsing, from a capture taken on the target device
 * (Infinix X6882, Android 14, KernelSU) while a Windows laptop sat behind the
 * portal.
 *
 * The decisive detail: the laptop's only MAC mapping was on a GLOBAL IPv6
 * address, and its IPv4 entry had no lladdr at all. A parser that drops every
 * address containing ':' therefore found nothing, so the connectivity probe
 * that Windows fires right after login could not be matched to a MAC, the
 * server treated the guest as unauthorized, and served the login page again.
 */
class ClientMonitorTest {

    /** Verbatim from the device while the laptop was connected. */
    private val realCapture = """
        10.206.236.9  FAILED
        2400:9800:9b3:4e30:2dda:8879:749:4b71 lladdr 02:ea:30:f9:b8:3c STALE
        2400:9800:9b3:4e30:407:1996:8c2a:787e lladdr 02:ea:30:f9:b8:3c STALE
        2400:9800:9b3:4e30:acfc:50bc:3cd1:2777  FAILED
        fe80::9d98:4ca8:e98c:6ade lladdr 02:ea:30:f9:b8:3c STALE
    """.trimIndent()

    @Test
    fun `a global IPv6 client keeps its MAC`() {
        val clients = ClientMonitor.parseNeighbours(realCapture)

        val macs = clients.map { it.mac }.distinct()
        assertEquals(listOf("02:ea:30:f9:b8:3c"), macs)
        assertEquals(2, clients.size) // the two STALE v6 entries, FAILED ones carry no lladdr
    }

    @Test
    fun `link-local neighbour discovery is not a client`() {
        val clients = ClientMonitor.parseNeighbours(realCapture)

        assertNull(clients.firstOrNull { it.ip.startsWith("fe80:") })
    }

    @Test
    fun `an IPv4 client is still parsed`() {
        val v4 = "192.168.43.77 dev ap0 lladdr aa:bb:cc:dd:ee:ff REACHABLE"

        val client = ClientMonitor.parseNeighbours(v4).single()

        assertEquals("192.168.43.77", client.ip)
        assertEquals("aa:bb:cc:dd:ee:ff", client.mac)
        assertEquals("REACHABLE", client.state)
    }

    @Test
    fun `an IPv4 entry with no MAC is skipped, not half-read`() {
        assertEquals(emptyList<ClientInfo>(), ClientMonitor.parseNeighbours("10.206.236.9  FAILED"))
    }

    @Test
    fun `STALE still counts as present`() {
        val stale = ClientMonitor.parseNeighbours(
            "2400:9800:9b3:4e30:2dda:8879:749:4b71 lladdr 02:ea:30:f9:b8:3c STALE"
        ).single()

        // Spec gotcha 9: STALE alone is not absence.
        assertEquals(true, stale.present)
    }

    @Test
    fun `the kernel spelling and the HTTP peer spelling resolve to one address`() {
        val fromKernel = "2400:9800:9b3:4e30:2dda:8879:749:4b71"
        val upperCase = "2400:9800:9B3:4E30:2DDA:8879:749:4B71"
        val bracketed = "[2400:9800:9b3:4e30:2dda:8879:749:4b71]"

        val canonical = ClientMonitor.canonicalIp(fromKernel)
        assertEquals(canonical, ClientMonitor.canonicalIp(upperCase))
        assertEquals(canonical, ClientMonitor.canonicalIp(bracketed))
    }

    @Test
    fun `a scope suffix does not change the address`() {
        assertEquals(
            ClientMonitor.canonicalIp("fe80::1"),
            ClientMonitor.canonicalIp("fe80::1%ap0"),
        )
    }

    @Test
    fun `a hostname is never resolved through DNS`() {
        // Anything non-numeric must return null rather than hit the network.
        assertNull(ClientMonitor.canonicalIp("example.com"))
        assertNull(ClientMonitor.canonicalIp(""))
    }

    @Test
    fun `one laptop with three addresses is one row, showing its IPv4`() {
        val mac = "02:ea:30:f9:b8:3c"
        val rows = listOf(
            ObservedClient(ClientInfo("2400:9800:9b3:4e30:2dda:8879:749:4b71", mac, null, "STALE")),
            ObservedClient(ClientInfo("2400:9800:9b3:4e30:407:1996:8c2a:787e", mac, null, "STALE")),
            ObservedClient(ClientInfo("10.206.236.9", mac, null, "REACHABLE")),
        )

        val devices = groupByDevice(rows)

        // Duplicate MACs in a LazyColumn are a fatal Compose error, and one
        // guest shown three times is its own bug.
        assertEquals(1, devices.size)
        assertEquals("10.206.236.9", devices.single().info.ip)
    }

    @Test
    fun `an IPv6-only device still gets a row`() {
        val mac = "02:ea:30:f9:b8:3c"
        val rows = listOf(
            ObservedClient(ClientInfo("2400:9800:9b3:4e30:2dda:8879:749:4b71", mac, null, "STALE")),
            ObservedClient(ClientInfo("2400:9800:9b3:4e30:407:1996:8c2a:787e", mac, null, "STALE")),
        )

        val devices = groupByDevice(rows)

        assertEquals(1, devices.size)
        assertEquals("2400:9800:9b3:4e30:2dda:8879:749:4b71", devices.single().info.ip)
    }

    @Test
    fun `distinct devices stay separate`() {
        val rows = listOf(
            ObservedClient(ClientInfo("10.0.0.2", "aa:bb:cc:dd:ee:01", null, "REACHABLE")),
            ObservedClient(ClientInfo("2400::2", "aa:bb:cc:dd:ee:02", null, "STALE")),
        )

        assertEquals(2, groupByDevice(rows).size)
    }

    @Test
    fun `an unprobed STALE cannot undo a probed REACHABLE`() {
        // This is the combination that pinned a connected guest to "offline":
        // macFor republishes on every probe request the OS makes, and the raw
        // state of a live device is STALE.
        assertEquals(
            "REACHABLE",
            ClientMonitor.mergeState(prior = "REACHABLE", incoming = "STALE", probed = false),
        )
    }

    @Test
    fun `a kernel FAILED still wins over a previous REACHABLE`() {
        // Otherwise a departed device would never be noticed.
        assertEquals(
            "FAILED",
            ClientMonitor.mergeState(prior = "REACHABLE", incoming = "FAILED", probed = false),
        )
    }

    @Test
    fun `a probe result always wins`() {
        assertEquals("FAILED", ClientMonitor.mergeState("REACHABLE", "FAILED", probed = true))
        assertEquals("REACHABLE", ClientMonitor.mergeState("FAILED", "REACHABLE", probed = true))
    }

    @Test
    fun `with no prior state the raw state is used`() {
        assertEquals("STALE", ClientMonitor.mergeState(null, "STALE", probed = false))
    }
}
