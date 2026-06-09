package com.tepmex.zoulushang.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CityBoundary::class, VisitedTile::class, LiveTile::class, LiveLocationSample::class],
    version = 4,
    exportSchema = false,
)
abstract class ZouLuShangDatabase : RoomDatabase() {
    abstract fun cityBoundaryDao(): CityBoundaryDao
    abstract fun visitedTileDao(): VisitedTileDao
    abstract fun liveTileDao(): LiveTileDao
    abstract fun liveLocationSampleDao(): LiveLocationSampleDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE visited_tiles ADD COLUMN pointCount INTEGER NOT NULL DEFAULT 26",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS live_tiles (
                        cityId INTEGER NOT NULL,
                        tileKey INTEGER NOT NULL,
                        pointCount INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(cityId, tileKey)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS live_location_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        cityId INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        recordedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
