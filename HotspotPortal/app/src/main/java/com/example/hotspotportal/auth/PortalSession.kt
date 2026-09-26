package com.example.hotspotportal.auth

/**
 * An authorised device. Held in memory only: the portal is armed while the
 * service runs, and a stale session surviving a reboot would be a security
 * hole, not a convenience.
 *
 * There is deliberately no expiry and no idle clock. A session ends when the
 * admin kicks it, when the account is disabled or deleted, or when the portal
 * stops - never because a timer ran out.
 */
data class PortalSession(
    val mac: String,
    val username: String,
    val token: String,
    val createdAt: Long,
)
