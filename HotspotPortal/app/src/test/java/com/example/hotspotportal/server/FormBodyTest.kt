package com.example.hotspotportal.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign-in form body.
 *
 * Context: NanoHTTPD 2.3.1's parseBody returned an empty map for every login
 * on the device - correct Content-Type, correct Content-Length, non-null stream
 * - so the endpoint always answered "Username and password are required" and a
 * guest could never sign in. The body is now read and decoded here, and this
 * covers the decoding half of that.
 */
class FormBodyTest {

    @Test
    fun `a plain login body is parsed`() {
        val parms = PortalServer.parseUrlEncoded("username=alice&password=hunter2")

        assertEquals("alice", parms["username"])
        assertEquals("hunter2", parms["password"])
    }

    @Test
    fun `the page's percent-encoded values are decoded`() {
        // The page sends encodeURIComponent, so a generated password with
        // reserved characters arrives escaped.
        val parms = PortalServer.parseUrlEncoded("username=a%40b.com&password=p%40ss%3Aword")

        assertEquals("a@b.com", parms["username"])
        assertEquals("p@ss:word", parms["password"])
    }

    @Test
    fun `plus is a space, as the form encoding requires`() {
        assertEquals("a b c", PortalServer.parseUrlEncoded("username=a+b+c")["username"])
    }

    @Test
    fun `a value containing an equals sign survives`() {
        val parms = PortalServer.parseUrlEncoded("username=alice&password=a=b=c")

        assertEquals("a=b=c", parms["password"])
    }

    @Test
    fun `malformed percent escapes do not throw`() {
        val parms = PortalServer.parseUrlEncoded("username=%zz&password=%E4%B8")

        // Decoded best-effort: the point is that a hostile body cannot 500.
        assertEquals(2, parms.size)
    }

    @Test
    fun `junk pairs are skipped rather than throwing`() {
        val parms = PortalServer.parseUrlEncoded("&&novalue&=novalue&username=alice&&password=x")

        assertEquals("alice", parms["username"])
        assertEquals("x", parms["password"])
    }

    @Test
    fun `an empty body yields nothing`() {
        assertTrue(PortalServer.parseUrlEncoded("").isEmpty())
    }
}
