package com.example.hotspotportal.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hotspot interface detection.
 *
 * The `ip -4 addr show` fixture is a real capture from the target device
 * (Infinix X6882, Android 14) with the AP interface blocks appended in the
 * exact format the kernel prints - that format is what the previous parser got
 * wrong, splitting the header on whitespace and expecting a bare ":" where the
 * name actually carries its own colon.
 */
class HotspotDetectorTest {

    private val realCapture = """
        1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
            inet 127.0.0.1/8 scope host lo
               valid_lft forever preferred_lft forever
        16: ccmni2: <NOARP,UP,LOWER_UP> mtu 1500 qdisc mq state UNKNOWN group default qlen 1000
            inet 10.110.97.179/8 scope global ccmni2
               valid_lft forever preferred_lft forever
    """.trimIndent()

    @Test
    fun `interface names are read from the header, not by splitting on whitespace`() {
        val addrs = HotspotDetector.parseIfaceAddrs(realCapture)

        // The old parser looked for parts[1] == ":", which is never true here.
        assertEquals("127.0.0.1/8", addrs["lo"]?.let { "${it.first}/${it.second}" })
        assertEquals("10.110.97.179/8", addrs["ccmni2"]?.let { "${it.first}/${it.second}" })
    }

    @Test
    fun `an AP block is parsed with its own address and prefix`() {
        val withAp = realCapture + "\n" + """
            42: ap0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default qlen 1000
                inet 192.168.43.1/24 brd 192.168.43.255 scope global ap0
        """.trimIndent()

        val ap = HotspotDetector.parseIfaceAddrs(withAp)["ap0"]

        assertEquals("192.168.43.1", ap?.first)
        assertEquals(24, ap?.second)
    }

    @Test
    fun `Android 11 randomised tethering subnets are read, not assumed`() {
        val randomised = """
            5: wlan1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default qlen 1000
                inet 192.168.146.1/24 brd 192.168.146.255 scope global wlan1
        """.trimIndent()

        assertEquals("192.168.146.1", HotspotDetector.parseIfaceAddrs(randomised)["wlan1"]?.first)
    }

    @Test
    fun `a link alias keeps the base interface name`() {
        // "5: wlan0@if3:" - iptables needs wlan0, not the alias.
        val aliased = """
            5: wlan0@if3: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default qlen 1000
                inet 192.168.43.1/24 scope global wlan0
        """.trimIndent()

        assertEquals(setOf("wlan0"), HotspotDetector.parseIfaceAddrs(aliased).keys)
    }

    @Test
    fun `an interface with no IPv4 yields nothing`() {
        // What wlan0 looks like while the AP is off: present, but unnumbered.
        val down = """
            5: wlan0: <BROADCAST,MULTICAST> mtu 1500 qdisc noop state DOWN group default qlen 1000
                link/ether 02:00:00:00:00:00 brd ff:ff:ff:ff:ff:ff
        """.trimIndent()

        assertEquals(emptyMap<String, Pair<String, Int>>(), HotspotDetector.parseIfaceAddrs(down))
    }

    @Test
    fun `a hotspot that is off produces no interface`() {
        // Exactly what the device reported while the AP was down: no ap*, no
        // wlan1, nothing for the app to attach rules to.
        assertNull(HotspotDetector.parseIfaceAddrs(realCapture)["ap0"])
    }
}
