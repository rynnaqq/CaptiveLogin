package com.example.hotspotportal.server

/**
 * The captive-portal probe matrix (spec section 4).
 *
 * Every OS decides "am I online?" by fetching one known URL and checking the
 * response. Serving a 200 with a page instead of the expected "I'm online"
 * answer is what makes each OS open its captive-portal window. The bodies
 * below are exact — iOS in particular will not close its sheet unless the
 * body is exactly `Success`.
 *
 * Pure logic, no HTTP types, so the whole matrix is unit-testable.
 */
object ProbeRouter {

    data class Response(val status: Int, val contentType: String, val body: String)

    private const val TEXT = "text/plain; charset=utf-8"
    private const val HTML = "text/html; charset=utf-8"

    const val ANDROID_204 = "204 No Content"
    const val APPLE_SUCCESS = "Success"
    const val WINDOWS_CONNECT = "Microsoft Connect Test"
    const val WINDOWS_NCSI = "Microsoft NCSI"
    const val FIREFOX_SUCCESS = "success"

    /** Probe paths this router answers, matched on any Host header. */
    private val PROBE_PATHS = setOf(
        "/generate_204",
        "/gen_204",
        "/hotspot-detect.html",
        "/captive.apple.com/hotspot-detect.html",
        "/connecttest.txt",
        "/ncsi.txt",
        "/success.txt",
        "/canonical.html",
        "/redirect",
    )

    fun isProbePath(path: String): Boolean = path in PROBE_PATHS

    /**
     * Android's connectivity check is the *only* probe whose status code
     * differs between blocked and online: 204 means "no content, you're
     * online". Any other 2xx body opens the sign-in sheet.
     */
    fun responseFor(path: String, authorized: Boolean, portalHtml: String): Response = when (path) {
        "/generate_204", "/gen_204" ->
            if (authorized) Response(204, TEXT, "")
            else Response(200, HTML, portalHtml)

        // iOS/macOS: body must be exactly "Success" to close the CNA sheet.
        "/hotspot-detect.html", "/captive.apple.com/hotspot-detect.html" ->
            if (authorized) Response(200, TEXT, APPLE_SUCCESS)
            else Response(200, HTML, portalHtml)

        "/connecttest.txt" ->
            if (authorized) Response(200, TEXT, WINDOWS_CONNECT)
            else Response(200, HTML, portalHtml)

        "/ncsi.txt" ->
            if (authorized) Response(200, TEXT, WINDOWS_NCSI)
            else Response(200, HTML, portalHtml)

        "/success.txt" ->
            if (authorized) Response(200, TEXT, FIREFOX_SUCCESS)
            else Response(200, HTML, portalHtml)

        // Ubuntu connectivity-check, and the older redirect probe.
        "/canonical.html", "/redirect" ->
            if (authorized) Response(204, TEXT, "")
            else Response(200, HTML, portalHtml)

        else -> Response(200, HTML, portalHtml)
    }

    /**
     * Picks the probe to hit after a successful login so the OS closes its
     * own window without the guest touching anything. Chosen from the
     * User-Agent of the browser that submitted the form.
     */
    fun postLoginProbePath(userAgent: String?): String {
        val ua = userAgent.orEmpty()
        return when {
            ua.contains("iPhone") || ua.contains("iPad") || ua.contains("Macintosh") || ua.contains("iPod") ->
                "/hotspot-detect.html"
            ua.contains("Windows") -> "/connecttest.txt"
            // Android's captive webview UA contains "Linux" too, so it has to
            // be tested before the desktop-Linux branch.
            ua.contains("Android") -> "/generate_204"
            ua.contains("Ubuntu") || ua.contains("Linux") -> "/canonical.html"
            else -> "/generate_204"
        }
    }
}
