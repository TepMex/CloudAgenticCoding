package com.tepmex.myweek.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tepmex.myweek.domain.TaskText

@Database(
    entities = [
        SprintEntity::class,
        BlockEntity::class,
        TaskEntity::class,
        StepRatingEntity::class,
        StepComparisonEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class WeekDatabase : RoomDatabase() {
    abstract fun weekDao(): WeekDao

    abstract fun stepDao(): StepDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE blocks ADD COLUMN emoji TEXT NOT NULL DEFAULT '${TaskText.DEFAULT_EMOJI}'",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `step_ratings` (`optionKey` TEXT NOT NULL, `emoji` TEXT NOT NULL, `label` TEXT NOT NULL, `rating` REAL NOT NULL, `comparisonCount` INTEGER NOT NULL, `winCount` INTEGER NOT NULL, `lossCount` INTEGER NOT NULL, `skipCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`optionKey`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `step_comparisons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `leftKey` TEXT NOT NULL, `rightKey` TEXT NOT NULL, `pairKey` TEXT NOT NULL, `outcome` TEXT NOT NULL, `leftRatingBefore` REAL NOT NULL, `rightRatingBefore` REAL NOT NULL, `leftRatingAfter` REAL NOT NULL, `rightRatingAfter` REAL NOT NULL, `comparedAt` INTEGER NOT NULL)",
                )
            }
        }

        fun create(context: Context): WeekDatabase =
            Room.databaseBuilder(context, WeekDatabase::class.java, "my-week.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
