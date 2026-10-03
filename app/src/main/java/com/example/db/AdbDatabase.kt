package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScriptEntity::class, HistoryEntity::class, AntiOverlayEventEntity::class], version = 2, exportSchema = false)
abstract class AdbDatabase : RoomDatabase() {
    abstract fun adbDao(): AdbDao

    companion object {
        @Volatile
        private var INSTANCE: AdbDatabase? = null

        fun getDatabase(context: Context): AdbDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AdbDatabase::class.java,
                    "super_adb_manager_db"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
