package com.example.hotspotportal.server

import android.util.Log
import fi.iki.elonen.NanoHTTPD
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
) : NanoHTTPD(port) {

    /** Minimal IStatus: this library provides none of its own. */
    private data class Status(
        override val requestStatus: Int,
        override val description: String,
    ) : NanoHTTPD.Response.IStatus

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.trimEnd('/').ifEmpty { "/" }
        val isPost = session.method == Method.POST

        // Probes answer on any Host header: DNS is hijacked, so the hostname
        // in the URL is whatever the OS decided to ask for.
        if (ProbeRouter.isProbePath(path)) return respondProbe(session, path)

        return when {
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
        val parms = HashMap<String, String>()
        runCatching { session.parseBody(parms) }
        val username = parms["username"].orEmpty()
        val password = parms["password"].orEmpty()
        if (username.isBlank() || password.isBlank()) {
            return respond(ApiResponse.Err(400, """{"status":"invalid","message":"Username and password are required."}"""))
        }
        val result = runBlocking {
            withTimeoutOrNull(LOGIN_TIMEOUT_MS) {
                loginApi.login(username, password, session.remoteIpAddress)
            }
        } ?: return respond(ApiResponse.Err(503, """{"status":"busy","message":"Please try again."}"""))
        return respond(result)
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
        private const val MAC_LOOKUP_TIMEOUT_MS = 2_000L

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
