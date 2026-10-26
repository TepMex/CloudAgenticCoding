package com.tepmex.myweek.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tepmex.myweek.domain.TaskText

@Database(
    entities = [SprintEntity::class, BlockEntity::class, TaskEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WeekDatabase : RoomDatabase() {
    abstract fun weekDao(): WeekDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE blocks ADD COLUMN emoji TEXT NOT NULL DEFAULT '${TaskText.DEFAULT_EMOJI}'",
                )
            }
        }

        fun create(context: Context): WeekDatabase =
            Room.databaseBuilder(context, WeekDatabase::class.java, "my-week.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
