package com.example.hotspotportal.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PortalUserEntity::class, PortalLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PortalDatabase : RoomDatabase() {
    abstract fun userDao(): PortalUserDao
    abstract fun logDao(): PortalLogDao

    companion object {
        @Volatile
        private var instance: PortalDatabase? = null

        /**
         * 1 -> 2: portal_users.id gained autoGenerate.
         *
         * The migration is intentionally empty. autoGenerate changes no column
         * - `id` was already `INTEGER PRIMARY KEY` and every existing row is
         * kept - but it does change Room's schema identity hash, so without a
         * version bump Room refuses to open the file and every query throws
         * "Room cannot verify the data integrity", taking the app down on
         * launch. The empty migration is what advances the stored hash.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        fun get(context: Context): PortalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PortalDatabase::class.java,
                "hotspotportal.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
