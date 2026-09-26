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

    /**
     * The end-to-end contract for a Windows guest, which is the loop that had
     * no coverage: the UA picked after login, the path the router answers, and
     * the exact body Windows insists on before it drops the sign-in sheet.
     * Passing a null UA here is what sent the laptop to /generate_204 forever.
     */
    @Test
    fun `a windows guest gets the exact online body after login`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        val path = ProbeRouter.postLoginProbePath(ua)

        assertEquals("/connecttest.txt", path)
        assertTrue(ProbeRouter.isProbePath(path))

        val online = ProbeRouter.responseFor(path, authorized = true, portalHtml = "<html>portal</html>")
        assertEquals("Microsoft Connect Test", online.body)
        assertEquals(200, online.status)

        // And the same path must still show the portal while blocked.
        val blocked = ProbeRouter.responseFor(path, authorized = false, portalHtml = "<html>portal</html>")
        assertEquals("<html>portal</html>", blocked.body)
    }

    @Test
    fun `every post-login probe answers with the online body its OS expects`() {
        val cases = mapOf(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)" to "Success",
            "Mozilla/5.0 (Windows NT 10.0; Win64)" to "Microsoft Connect Test",
            "Dalvik/2.1 (Linux; Android 14)" to "", // 204, empty body
        )
        for ((ua, expected) in cases) {
            val path = ProbeRouter.postLoginProbePath(ua)
            val res = ProbeRouter.responseFor(path, authorized = true, portalHtml = "x")
            if (expected.isEmpty()) {
                assertEquals("204 for $ua", 204, res.status)
            } else {
                assertEquals("body for $ua", expected, res.body)
            }
        }
    }
}
