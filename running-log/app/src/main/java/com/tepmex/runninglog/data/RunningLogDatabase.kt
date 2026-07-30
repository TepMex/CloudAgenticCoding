package com.tepmex.runninglog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RunningActivityEntity::class], version = 1, exportSchema = false)
abstract class RunningLogDatabase : RoomDatabase() {
    abstract fun runningActivityDao(): RunningActivityDao

    companion object {
        fun create(context: Context): RunningLogDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RunningLogDatabase::class.java,
                "running_log.db",
            ).build()
    }
}
