package com.tepmex.wozainaar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocationPoint::class],
    version = 1,
    exportSchema = false,
)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationPointDao(): LocationPointDao

    companion object {
        fun create(context: Context): LocationDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LocationDatabase::class.java,
                "wo_zai_naar.db",
            ).build()
    }
}
