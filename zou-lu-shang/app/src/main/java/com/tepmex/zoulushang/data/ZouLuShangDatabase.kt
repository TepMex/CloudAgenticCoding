package com.tepmex.zoulushang.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CityBoundary::class, VisitedTile::class],
    version = 1,
    exportSchema = false,
)
abstract class ZouLuShangDatabase : RoomDatabase() {
    abstract fun cityBoundaryDao(): CityBoundaryDao
    abstract fun visitedTileDao(): VisitedTileDao

    companion object {
        @Volatile
        private var instance: ZouLuShangDatabase? = null

        fun get(context: Context): ZouLuShangDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZouLuShangDatabase::class.java,
                    "zou_lu_shang.db",
                ).build().also { instance = it }
            }
    }
}
