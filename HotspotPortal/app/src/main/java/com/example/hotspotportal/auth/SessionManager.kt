package com.example.hotspotportal.auth

import com.example.hotspotportal.net.FirewallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the set of authorised devices and their firewall rules.
 *
 * A session lives until it is revoked - by the admin, by the account being
 * disabled or deleted, or by the portal stopping. There is no expiry and no
 * idle timeout, so a connected guest is never disconnected automatically.
 */
class SessionManager(
    private val firewall: FirewallManager,
    private val onEvent: (String, String) -> Unit = { _, _ -> },
) {
    private val _sessions = MutableStateFlow<Map<String, PortalSession>>(emptyMap())
    val sessions: StateFlow<Map<String, PortalSession>> = _sessions.asStateFlow()

    fun isAuthorized(mac: String): Boolean = _sessions.value.containsKey(mac)

    fun sessionFor(mac: String): PortalSession? = _sessions.value[mac]

    /** How many of a user's device slots are currently in use. */
    fun activeDeviceCount(username: String): Int =
        _sessions.value.values.count { it.username.equals(username, ignoreCase = true) }

    suspend fun create(mac: String, username: String, token: String): PortalSession {
        val session = PortalSession(
            mac = mac,
            username = username,
            token = token,
            createdAt = System.currentTimeMillis(),
        )
        firewall.allowMac(mac)
        _sessions.value = _sessions.value + (mac to session)
        onEvent("login_ok", "$username from $mac")
        return session
    }

    suspend fun revoke(mac: String, reason: String) {
        val session = _sessions.value[mac] ?: return
        _sessions.value = _sessions.value - mac
        firewall.removeMac(mac)
        onEvent("session_end", "$reason ${session.username} ($mac)")
    }

    suspend fun revokeAll(reason: String = "portal_stopped") {
        val current = _sessions.value
        if (current.isEmpty()) return
        _sessions.value = emptyMap()
        current.keys.forEach { firewall.removeMac(it) }
        onEvent("sessions_cleared", "${current.size} sessions removed ($reason)")
    }
}
