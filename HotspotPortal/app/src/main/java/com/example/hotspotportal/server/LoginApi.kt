package com.example.hotspotportal.server

import com.example.hotspotportal.auth.AuthStore
import com.example.hotspotportal.auth.LoginFailure
import com.example.hotspotportal.auth.LoginResult
import com.example.hotspotportal.auth.RateLimiter
import com.example.hotspotportal.auth.SessionManager
import java.security.SecureRandom

sealed interface ApiResponse {
    val status: Int
    val body: String

    data class Ok(override val body: String, override val status: Int = 200, val token: String? = null) : ApiResponse
    data class Err(override val status: Int, override val body: String) : ApiResponse
}

/**
 * Login, logout and status.
 *
 * A login arrives from an IP, not a MAC, so the MAC is resolved from the
 * neighbour table first — without it no whitelist rule can be written, and
 * silently authorising the wrong device would be worse than refusing.
 */
class LoginApi(
    private val authStore: AuthStore,
    private val sessions: SessionManager,
    private val macResolver: suspend (ip: String) -> String?,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val onEvent: (String, String) -> Unit = { _, _ -> },
) {

    suspend fun login(username: String, password: String, ip: String, userAgent: String? = null): ApiResponse {
        val mac = macResolver(ip)
        android.util.Log.i("LoginDiag", "POST login user=$username ip=$ip ua=$userAgent mac=$mac")
        if (mac == null) {
            // Logged: this path returns a 400 without touching the rate limiter
            // or emitting an event, so a client the kernel has not learned yet
            // is otherwise invisible in the Logs tab.
            onEvent("unknown_device", "login from $ip could not be matched to a MAC")
            return ApiResponse.Err(
                400,
                json(mapOf("status" to "unknown_device", "message" to "Cannot identify this device yet. Reconnect and try again.")),
            )
        }

        if (!rateLimiter.allow(mac)) {
            onEvent("rate_limited", "$username from $ip")
            return ApiResponse.Err(
                429,
                json(
                    mapOf(
                        "status" to "locked",
                        "message" to "Too many failed attempts. Try again in ${rateLimiter.retryAfterSeconds(mac)}s.",
                        "retryAfter" to rateLimiter.retryAfterSeconds(mac),
                    )
                ),
            )
        }

        return when (val result = authStore.verify(username, password)) {
            is LoginResult.Failure -> {
                rateLimiter.recordFailure(mac)
                onEvent("login_fail", "$username from $ip (${result.reason})")
                val message = when (result.reason) {
                    // Specific on purpose: the guest is told which half was
                    // wrong, which is what the admin asked for. This does let a
                    // guest enumerate usernames; the rate limit above is the
                    // brute-force defence.
                    LoginFailure.NO_SUCH_USER -> "There is no account with that username."
                    LoginFailure.WRONG_PASSWORD -> "That username exists, but the password is wrong."
                    LoginFailure.DISABLED -> "This account is disabled."
                    LoginFailure.EXPIRED -> "This account has expired."
                    else -> "Invalid username or password."
                }
                ApiResponse.Err(
                    401,
                    json(
                        mapOf(
                            "status" to "invalid",
                            "detail" to result.reason.name,
                            "message" to message,
                            "remainingAttempts" to rateLimiter.remainingAttempts(mac),
                        )
                    ),
                )
            }

            is LoginResult.Success -> {
                val user = result.user
                val existing = sessions.sessionFor(mac)
                val alreadyCounted = existing != null && existing.username.equals(user.username, true)
                if (!alreadyCounted && sessions.activeDeviceCount(user.username) >= user.deviceLimit) {
                    onEvent("device_limit", "${user.username} at $mac")
                    return ApiResponse.Err(
                        403,
                        json(mapOf("status" to "device_limit", "message" to "Device limit reached for this account.")),
                    )
                }

                rateLimiter.reset(mac)
                val token = newToken()
                val session = sessions.create(mac, user.username, token)
                onEvent("login_ok", "${user.username} at $mac from $ip")
                ApiResponse.Ok(
                    body = json(
                        mapOf(
                            "status" to "ok",
                            "username" to user.username,
                            "expiresAt" to session.expiresAt,
                            "remainingSeconds" to ((session.expiresAt - System.currentTimeMillis()) / 1000),
                            // The OS closes its own window only when it sees the
                            // response it expects, and which probe that is
                            // depends on the client: Windows never asks
                            // /generate_204, so passing a null UA here left it
                            // re-showing the login page forever.
                            "nextProbe" to ProbeRouter.postLoginProbePath(userAgent),
                        )
                    ),
                    token = token,
                )
            }
        }
    }

    suspend fun logout(mac: String?): ApiResponse {
        if (mac == null) return ApiResponse.Err(400, json(mapOf("status" to "unknown_device")))
        sessions.revoke(mac, "logout")
        return ApiResponse.Ok(json(mapOf("status" to "ok")))
    }

    fun status(mac: String?): ApiResponse.Ok {
        val session = mac?.let { sessions.sessionFor(it) }
            ?: return ApiResponse.Ok(json(mapOf("authorized" to false)))
        val now = System.currentTimeMillis()
        return ApiResponse.Ok(
            json(
                mapOf(
                    "authorized" to !session.isExpired(now),
                    "username" to session.username,
                    "expiresAt" to session.expiresAt,
                    "remainingSeconds" to ((session.expiresAt - now) / 1000),
                )
            )
        )
    }

    /** Kicks a client immediately from the admin UI. */
    suspend fun kick(mac: String) {
        sessions.revoke(mac, "kicked")
    }

    private fun newToken(): String {
        val bytes = ByteArray(32) // 256 bits, spec section 10
        SecureRandom().nextBytes(bytes)
        // android.util.Base64, not java.util.Base64: the latter is API 26+,
        // and minSdk here is 24.
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE,
        )
    }

    private fun json(fields: Map<String, Any?>): String = fields.entries.joinToString(
        prefix = "{", postfix = "}",
        separator = ",",
    ) { (k, v) ->
        val value = when (v) {
            null -> "null"
            is Number, is Boolean -> v.toString()
            else -> "\"" + jsonEscape(v.toString()) + "\""
        }
        "\"$k\":$value"
    }

    private fun jsonEscape(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}
