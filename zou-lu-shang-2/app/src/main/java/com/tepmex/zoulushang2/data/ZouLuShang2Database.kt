package com.tepmex.zoulushang2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PaintCell::class],
    version = 1,
    exportSchema = false,
)
abstract class ZouLuShang2Database : RoomDatabase() {
    abstract fun paintCellDao(): PaintCellDao

    companion object {
        @Volatile
        private var instance: ZouLuShang2Database? = null

        fun get(context: Context): ZouLuShang2Database =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZouLuShang2Database::class.java,
                    "zou_lu_shang_2.db",
                ).build().also { instance = it }
            }
    }
}
