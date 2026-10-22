package com.tepmex.myweek.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SprintEntity::class, BlockEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WeekDatabase : RoomDatabase() {
    abstract fun weekDao(): WeekDao

    companion object {
        fun create(context: Context): WeekDatabase =
            Room.databaseBuilder(context, WeekDatabase::class.java, "my-week.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
