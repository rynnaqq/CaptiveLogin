package com.example.hotspotportal.store

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PortalUserDao {
    @Query("SELECT * FROM portal_users ORDER BY username_display COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PortalUserEntity>>

    @Query("SELECT * FROM portal_users WHERE username_lower = :username LIMIT 1")
    suspend fun byUsername(username: String): PortalUserEntity?

    @Query("SELECT COUNT(*) FROM portal_users")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: PortalUserEntity): Long

    @Update
    suspend fun update(user: PortalUserEntity)

    @Delete
    suspend fun delete(user: PortalUserEntity)
}

@Dao
interface PortalLogDao {
    @Query("SELECT * FROM portal_logs ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 1000): Flow<List<PortalLogEntity>>

    @Insert
    suspend fun insert(log: PortalLogEntity)

    @Query("DELETE FROM portal_logs")
    suspend fun clear()

    /** Keeps only the newest [keep] rows. */
    @Query(
        "DELETE FROM portal_logs WHERE id NOT IN " +
            "(SELECT id FROM portal_logs ORDER BY id DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int = 1000)
}
