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
}
