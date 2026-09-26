package com.example.hotspotportal.auth

import com.example.hotspotportal.net.FirewallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the set of authorised devices and their firewall rules.
 *
 * Both the expiry clock and the idle clock live here so a session can never
 * outlive the rule that grants it, or the reverse.
 */
class SessionManager(
    private val firewall: FirewallManager,
    private val config: () -> SessionConfig,
    private val onEvent: (String, String) -> Unit = { _, _ -> },
) {
    data class SessionConfig(val durationMillis: Long, val idleMillis: Long)

    private val _sessions = MutableStateFlow<Map<String, PortalSession>>(emptyMap())
    val sessions: StateFlow<Map<String, PortalSession>> = _sessions.asStateFlow()

    fun isAuthorized(mac: String): Boolean = _sessions.value.containsKey(mac)

    fun sessionFor(mac: String): PortalSession? = _sessions.value[mac]

    /** How many of a user's device slots are currently in use. */
    fun activeDeviceCount(username: String): Int =
        _sessions.value.values.count { it.username.equals(username, ignoreCase = true) }

    suspend fun create(mac: String, username: String, token: String): PortalSession {
        val now = System.currentTimeMillis()
        val session = PortalSession(
            mac = mac,
            username = username,
            token = token,
            createdAt = now,
            expiresAt = now + config().durationMillis,
            lastSeenAt = now,
        )
        firewall.allowMac(mac)
        _sessions.value = _sessions.value + (mac to session)
        onEvent("login_ok", "$username from $mac")
        return session
    }

    /** Called when a client is seen on the network. Keeps it out of the idle bucket. */
    fun touch(mac: String) {
        _sessions.value[mac]?.let { s ->
            _sessions.value = _sessions.value + (mac to s.copy(lastSeenAt = System.currentTimeMillis()))
        }
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

    /**
     * Ticks every 60s. STALE alone is not absence (spec gotcha 9) — the
     * caller passes in which MACs are currently present, so a full idle
     * window is required before anything is revoked.
     */
    suspend fun tick(presentMacs: Set<String>, now: Long = System.currentTimeMillis()) {
        val cfg = config()
        val expired = _sessions.value.values.filter { it.isExpired(now) }
        expired.forEach { revoke(it.mac, "expired") }

        val idle = _sessions.value.values.filter { it.isIdle(now, cfg.idleMillis) }
        idle.forEach { s ->
            if (s.mac !in presentMacs) revoke(s.mac, "idle")
        }
    }
}
