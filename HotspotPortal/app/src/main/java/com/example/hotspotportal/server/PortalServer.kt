package com.example.hotspotportal.server

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The guest-facing HTTP server.
 *
 * NanoHTTPD 2.3.1 notes: [NanoHTTPD.Response.IStatus] ships no constants
 * (hence [Status] below), headers are added to the returned [Response]
 * rather than the session, and
 * [NanoHTTPD.IHTTPSession.getRemoteIpAddress] already returns a String.
 *
 * NanoHTTPD is blocking, so suspending work is bridged with runBlocking and
 * bounded by a timeout — a wedged BCrypt must not hold a worker forever.
 */
class PortalServer(
    port: Int,
    private val loginApi: LoginApi,
    private val copyProvider: () -> PortalCopy,
    private val macResolver: suspend (ip: String) -> String?,
    private val isAuthorized: (mac: String) -> Boolean,
    private val logoProvider: () -> ByteArray? = { null },
) : NanoHTTPD(port) {

    private val logoCache = AtomicReference<ByteArray?>(null)

    /** Read once: the asset cannot change while the service runs. */
    private fun logo(): ByteArray? {
        logoCache.get()?.let { return it }
        val read = logoProvider()
        if (read != null) logoCache.compareAndSet(null, read)
        return logoCache.get()
    }

    /**
     * Minimal IStatus: this library provides none of its own.
     *
     * The interface declares getDescription()/getRequestStatus() as Java
     * methods, so they must be overridden as functions — Kotlin properties
     * do not satisfy a Java getter here.
     */
    private class Status(
        private val code: Int,
        private val desc: String,
    ) : NanoHTTPD.Response.IStatus {
        override fun getDescription(): String = desc
        override fun getRequestStatus(): Int = code
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.trimEnd('/').ifEmpty { "/" }
        val isPost = session.method == Method.POST

        // Probes answer on any Host header: DNS is hijacked, so the hostname
        // in the URL is whatever the OS decided to ask for.
        if (ProbeRouter.isProbePath(path)) return respondProbe(session, path)

        return when {
            // The sign-in page's artwork, served from the phone. Inlined as a
            // data URI it would add ~80 KB to every response, and the spec
            // caps the page at 100 KB.
            path == LOGO_PATH -> respondLogo(session)
            path == "/api/login" && isPost -> respondLogin(session)
            path == "/api/logout" && isPost -> respondLogout(session)
            path == "/api/status" && !isPost -> respond(loginApi.status(clientMac(session)))
            path == "/" && !isPost -> respondPortal(session)
            // Any other GET is a client trying to reach the internet: send it
            // to the portal rather than a dead end.
            !isPost -> respondPortal(session)
            else -> respond(ApiResponse.Err(404, """{"status":"not_found"}"""))
        }
    }

    private fun respondLogo(session: IHTTPSession): Response {
        val bytes = logo() ?: return html(404, "<p>logo unavailable</p>")
        val etag = "\"${bytes.size}-${bytes.fold(0L) { a, b -> a * 31 + b }}\""
        if (session.headers["if-none-match"] == etag) {
            return newFixedLengthResponse(
                Status(304, "Not Modified"),
                "image/png",
                java.io.ByteArrayInputStream(ByteArray(0)),
                0L,
            ).noCache()
        }
        return newFixedLengthResponse(
            Status(200, "OK"),
            "image/png",
            java.io.ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        )
            .apply { addHeader("ETag", etag) }
            .noCache()
    }

    private fun respondPortal(session: IHTTPSession): Response {
        val mac = clientMac(session)
        val authorized = mac != null && isAuthorized(mac)
        val html = PortalPages.portalHtml(
            copy = copyProvider(),
            signedInAs = if (authorized) loginApi.status(mac).signedInAs() else null,
        )
        return html(200, html)
    }

    private fun respondProbe(session: IHTTPSession, path: String): Response {
        val mac = clientMac(session)
        val authorized = mac != null && isAuthorized(mac)
        val r = ProbeRouter.responseFor(path, authorized, PortalPages.portalHtml(copyProvider()))
        return newFixedLengthResponse(statusOf(r.status), r.contentType, r.body).noCache()
    }

    private fun respondLogin(session: IHTTPSession): Response {
        val parms = readFormBody(session)
        val username = parms["username"].orEmpty()
        val password = parms["password"].orEmpty()
        if (username.isBlank() || password.isBlank()) {
            return respond(ApiResponse.Err(400, """{"status":"invalid","message":"Username and password are required."}"""))
        }
        val result = runBlocking {
            withTimeoutOrNull(LOGIN_TIMEOUT_MS) {
                // NanoHTTPD lower-cases header names. The UA decides which probe
                // the client is told to hit next so its window closes itself.
                loginApi.login(username, password, session.remoteIpAddress, session.headers["user-agent"])
            }
        } ?: return respond(ApiResponse.Err(503, """{"status":"busy","message":"Please try again."}"""))
        return respond(result)
    }

    /**
     * Reads an `application/x-www-form-urlencoded` body.
     *
     * NanoHTTPD 2.3.1's own `parseBody` is not usable here. Verified on the
     * device with a hand-built POST carrying a correct Content-Type and
     * Content-Length: `getBodySize()` returns the right length, the input
     * stream is non-null, the content type matches, and the map still comes
     * back empty - so every sign-in parsed as username='' passwordLen=0 and
     * the endpoint answered "Username and password are required" no matter
     * what the guest typed. Reading the stream directly sidesteps it.
     *
     * The length comes from Content-Length and is capped, so a bogus or hostile
     * header cannot make the app allocate without bound.
     */
    private fun readFormBody(session: IHTTPSession): Map<String, String> {
        val declared = session.headers.entries.firstOrNull { it.key.equals("content-length", true) }
            ?.value?.trim()?.toIntOrNull() ?: return emptyMap()
        val length = declared.coerceIn(0, MAX_BODY_BYTES)
        if (length == 0) return emptyMap()

        val raw = ByteArray(length)
        var read = 0
        runCatching {
            val stream = session.getInputStream()
            while (read < length) {
                val n = stream.read(raw, read, length - read)
                if (n <= 0) break
                read += n
            }
        }
        if (read == 0) return emptyMap()

        val text = String(raw, 0, read, Charsets.UTF_8)
        return parseUrlEncoded(text)
    }

    private fun respondLogout(session: IHTTPSession): Response {
        val mac = clientMac(session)
            ?: return respond(ApiResponse.Err(400, """{"status":"unknown_device"}"""))
        runBlocking { withTimeoutOrNull(LOGIN_TIMEOUT_MS) { loginApi.logout(mac) } }
        return respond(ApiResponse.Ok("""{"status":"ok"}"""))
    }

    private fun respond(api: ApiResponse): Response {
        val response = newFixedLengthResponse(statusOf(api.status), JSON, api.body).noCache()
        if (api is ApiResponse.Ok && api.token != null) {
            response.addHeader(
                "Set-Cookie",
                "hp_token=${api.token}; HttpOnly; SameSite=Lax; Path=/; Max-Age=28800",
            )
        }
        return response
    }

    private fun html(code: Int, body: String): Response =
        newFixedLengthResponse(statusOf(code), HTML, body).noCache()

    /** Probes must never be cached or an OS can act on a stale answer. */
    private fun Response.noCache(): Response = apply {
        addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        addHeader("Pragma", "no-cache")
    }

    private fun statusOf(code: Int): NanoHTTPD.Response.IStatus = when (code) {
        200 -> Status(200, "OK")
        204 -> Status(204, "No Content")
        400 -> Status(400, "Bad Request")
        401 -> Status(401, "Unauthorized")
        403 -> Status(403, "Forbidden")
        404 -> Status(404, "Not Found")
        429 -> Status(429, "Too Many Requests")
        503 -> Status(503, "Service Unavailable")
        else -> Status(500, "Internal Error")
    }

    private fun clientMac(session: IHTTPSession): String? =
        runBlocking { withTimeoutOrNull(MAC_LOOKUP_TIMEOUT_MS) { macResolver(session.remoteIpAddress) } }

    private fun ApiResponse.Ok.signedInAs(): String? =
        Regex("\"username\":\"([^\"]*)\"").find(body)?.groupValues?.getOrNull(1)

    companion object {
        private const val TAG = "PortalServer"
        private const val JSON = "application/json; charset=utf-8"
        private const val HTML = "text/html; charset=utf-8"
        private const val LOGIN_TIMEOUT_MS = 10_000L
        const val LOGO_PATH = "/logo.png"

        private const val MAC_LOOKUP_TIMEOUT_MS = 2_000L
        private const val MAX_BODY_BYTES = 8 * 1024

        /** Percent-decoding, and "+" as a space, per the form-urlencoded rules. */
        private fun urlDecode(s: String): String = runCatching {
            java.net.URLDecoder.decode(s, "UTF-8")
        }.getOrDefault(s)

        /**
         * `a=1&b=2` to a map. Malformed pairs are skipped, never fatal.
         *
         * The page sends encodeURIComponent, so a generated password containing
         * reserved characters arrives percent-encoded and has to be decoded
         * before BCrypt ever sees it.
         */
        fun parseUrlEncoded(text: String): Map<String, String> {
            val parms = LinkedHashMap<String, String>()
            for (pair in text.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                parms[urlDecode(pair.substring(0, eq))] = urlDecode(pair.substring(eq + 1))
            }
            return parms
        }


        fun start(server: PortalServer, port: Int): Boolean = runCatching {
            server.start(SOCKET_READ_TIMEOUT, true)
            Log.i(TAG, "portal server listening on $port")
            true
        }.getOrElse {
            Log.e(TAG, "cannot bind HTTP $port", it)
            false
        }
    }
}
