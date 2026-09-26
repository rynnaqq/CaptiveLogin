package com.example.hotspotportal.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A credential the admin hands to guests.
 *
 * No default account is ever created: the first launch shows a card asking
 * the admin to make one, so there are no hardcoded credentials in the APK.
 */
@Entity(
    tableName = "portal_users",
    indices = [Index(value = ["username_lower"], unique = true)],
)
data class PortalUserEntity(
    // autoGenerate is required: without it every row is written with id = 0
    // and the second account an admin ever creates fails with
    // "UNIQUE constraint failed: portal_users.id".
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored lower-cased so login lookup is case-insensitive and unique. */
    val username_lower: String,
    /** Original casing, for display. */
    val username_display: String,
    val passwordHash: String,
    val enabled: Boolean = true,
    val deviceLimit: Int = 1,
    val expiresAt: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val username: String get() = username_display
    val expired: Boolean get() = expiresAt?.let { it <= System.currentTimeMillis() } ?: false
}
