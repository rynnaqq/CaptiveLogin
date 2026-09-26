package com.example.hotspotportal.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PortalUserEntity::class, PortalLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PortalDatabase : RoomDatabase() {
    abstract fun userDao(): PortalUserDao
    abstract fun logDao(): PortalLogDao

    companion object {
        @Volatile
        private var instance: PortalDatabase? = null

        fun get(context: Context): PortalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PortalDatabase::class.java,
                "hotspotportal.db",
            ).build().also { instance = it }
        }
    }
}
