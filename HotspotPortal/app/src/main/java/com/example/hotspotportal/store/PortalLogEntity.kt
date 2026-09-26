package com.example.hotspotportal.store

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One admin-visible event. Capped at 1000 rows; see [PortalLogDao.trim]. */
@Entity(tableName = "portal_logs")
data class PortalLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val event: String,
    val detail: String = "",
)
