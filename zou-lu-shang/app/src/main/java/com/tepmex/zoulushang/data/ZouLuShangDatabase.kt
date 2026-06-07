package com.tepmex.zoulushang.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CityBoundary::class, VisitedTile::class],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE visited_tiles ADD COLUMN pointCount INTEGER NOT NULL DEFAULT 26",
                )
            }
        }
    }
}
