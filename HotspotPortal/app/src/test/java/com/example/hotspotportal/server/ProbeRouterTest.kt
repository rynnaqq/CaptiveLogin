package com.example.hotspotportal.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test 2 (spec 13): every row of the section 4 matrix, blocked and authorised.
 *
 * The exact bodies matter more than the status codes — iOS will not close its
 * sheet unless the body is exactly "Success", and Android will not open it
 * unless the response is anything other than 204.
 */
class ProbeRouterTest {

    private val portal = "<html>portal</html>"

    @Test
    fun `android unauthorised gets 200 and portal HTML, never 204`() {
        val r = ProbeRouter.responseFor("/generate_204", authorized = false, portalHtml = portal)
        assertEquals(200, r.status)
        assertEquals(portal, r.body)
    }

    @Test
    fun `android authorised gets 204`() {
        assertEquals(204, ProbeRouter.responseFor("/generate_204", true, portal).status)
        assertEquals(204, ProbeRouter.responseFor("/gen_204", true, portal).status)
    }

    @Test
    fun `apple authorised body is exactly Success`() {
        val r = ProbeRouter.responseFor("/hotspot-detect.html", true, portal)
        assertEquals(200, r.status)
        assertEquals("Success", r.body)
    }

    @Test
    fun `apple unauthorised body does not contain Success`() {
        val r = ProbeRouter.responseFor("/hotspot-detect.html", false, portal)
        assertEquals(200, r.status)
        // This is the whole trigger: a 200 whose body lacks "Success".
        assertEquals(false, r.body.contains("Success"))
    }

    @Test
    fun `windows probes`() {
        assertEquals("Microsoft Connect Test", ProbeRouter.responseFor("/connecttest.txt", true, portal).body)
        assertEquals("Microsoft NCSI", ProbeRouter.responseFor("/ncsi.txt", true, portal).body)
        assertEquals(portal, ProbeRouter.responseFor("/connecttest.txt", false, portal).body)
        assertEquals(portal, ProbeRouter.responseFor("/ncsi.txt", false, portal).body)
    }

    @Test
    fun `firefox and ubuntu probes`() {
        assertEquals("success", ProbeRouter.responseFor("/success.txt", true, portal).body)
        assertEquals(204, ProbeRouter.responseFor("/canonical.html", true, portal).status)
        assertEquals(portal, ProbeRouter.responseFor("/success.txt", false, portal).body)
        assertEquals(portal, ProbeRouter.responseFor("/canonical.html", false, portal).body)
    }

    @Test
    fun `post-login probe is chosen from the user agent`() {
        assertEquals("/hotspot-detect.html", ProbeRouter.postLoginProbePath("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"))
        assertEquals("/hotspot-detect.html", ProbeRouter.postLoginProbePath("Mozilla/5.0 (Macintosh; Intel Mac OS X)"))
        assertEquals("/connecttest.txt", ProbeRouter.postLoginProbePath("Mozilla/5.0 (Windows NT 10.0; Win64)"))
        assertEquals("/generate_204", ProbeRouter.postLoginProbePath("Dalvik/2.1 (Linux; Android 14)"))
        assertEquals("/generate_204", ProbeRouter.postLoginProbePath(null))

        // A Windows browser hits its own probe first, so /connecttest.txt
        // must be a path the router actually answers.
        assertTrue(ProbeRouter.isProbePath(ProbeRouter.postLoginProbePath("Mozilla/5.0 (Windows NT 10.0; Win64)")))
    }
}
