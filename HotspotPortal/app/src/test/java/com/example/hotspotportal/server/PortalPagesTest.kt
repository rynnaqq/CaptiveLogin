package com.example.hotspotportal.server

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guest sign-in page.
 *
 * The bug these cover: the page's failure handler writes into
 * document.getElementById('err') and reloads the page when that element is
 * absent. The server rendered the error paragraph with class="err" but no id,
 * so the element never existed, every failed sign-in silently reloaded the
 * form, and a guest could not tell a wrong username from a wrong password.
 * Their report was "the login page keeps reappearing".
 */
class PortalPagesTest {

    private val copy = PortalCopy(
        title = "Wi-Fi Login",
        welcome = "Welcome aboard.",
        footer = "Powered by HotspotPortal",
    )

    @Test
    fun `the error target the page script writes into exists`() {
        val html = PortalPages.portalHtml(copy)

        assertTrue(
            "page script needs an element with id=\"err\"",
            html.contains("id=\"err\""),
        )
    }

    @Test
    fun `a server-rendered error is shown inside that element`() {
        val html = PortalPages.portalHtml(copy, message = "That password is wrong.")

        assertTrue(html.contains("id=\"err\""))
        assertTrue(html.contains("That password is wrong."))
    }

    @Test
    fun `the form has no external assets to load`() {
        val html = PortalPages.portalHtml(copy)

        // A captive webview has no route to the internet, so any CDN reference
        // would hang the page forever.
        assertTrue("no remote script", !html.contains("http://") || !html.contains("<script src="))
        assertTrue("no protocol-relative script", !html.contains("<script src=\"//"))
        assertTrue("page is small", html.length < 100_000)
    }

    @Test
    fun `the page reports which half of the credential was wrong`() {
        // The handler takes the server's message verbatim; the two specific
        // messages are what let a guest tell the cases apart.
        val noSuchUser = PortalPages.portalHtml(copy, message = "There is no account with that username.")
        val wrongPassword = PortalPages.portalHtml(copy, message = "That username exists, but the password is wrong.")

        assertTrue(noSuchUser.contains("no account with that username"))
        assertTrue(wrongPassword.contains("password is wrong"))
    }

    /**
     * The page must use the probe the SERVER chose.
     *
     * The server works out which connectivity check the client's OS actually
     * makes and returns it as `nextProbe`, precisely because the page cannot
     * know: the page runs inside the captive portal webview, whose user agent
     * is the webview's, not the connectivity checker's. An earlier version
     * ignored `nextProbe` and re-guessed from `navigator.userAgent`, so the
     * guest was sent to a probe the OS never makes. The OS keeps seeing a
     * captive portal and re-checking, which is the reported delay between
     * signing in and actually getting internet.
     */
    @Test
    fun `the page uses the probe the server returned rather than re-guessing`() {
        val html = PortalPages.portalHtml(copy)

        assertTrue(
            "the page must read nextProbe from the login response",
            html.contains("j.nextProbe"),
        )
        // The guess stays only as a fallback, so it must not be the sole source.
        assertTrue(
            "nextProbe needs a fallback for a response without it",
            html.contains("j&&j.nextProbe") || html.contains("(j&&j.nextProbe)||"),
        )
    }

    @Test
    fun `user supplied text is escaped in every rendering path`() {        val nasty = "<script>alert(1)</script>"
        val html = PortalPages.portalHtml(
            copy.copy(title = nasty, welcome = nasty, footer = nasty),
            message = nasty,
        )

        assertTrue("raw script tag must not survive", !html.contains(nasty))
        assertTrue(html.contains("&lt;script&gt;"))
    }
}
