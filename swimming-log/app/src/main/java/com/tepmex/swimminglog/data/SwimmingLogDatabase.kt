package com.tepmex.swimminglog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SwimmingActivityEntity::class], version = 1, exportSchema = false)
abstract class SwimmingLogDatabase : RoomDatabase() {
    abstract fun swimmingActivityDao(): SwimmingActivityDao

    companion object {
        fun create(context: Context): SwimmingLogDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SwimmingLogDatabase::class.java,
                "swimming_log.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
