package com.example.hotspotportal.auth

/**
 * An authorised device. Held in memory only: the portal is armed while the
 * service runs, and a stale session surviving a reboot would be a security
 * hole, not a convenience.
 */
data class PortalSession(
    val mac: String,
    val username: String,
    val token: String,
    val createdAt: Long,
    val expiresAt: Long,
    val lastSeenAt: Long,
) {
    fun isExpired(now: Long) = now >= expiresAt
    fun isIdle(now: Long, idleMillis: Long) = now - lastSeenAt >= idleMillis
}
