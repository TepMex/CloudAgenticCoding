package com.tepmex.inbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
abstract class InboxDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        fun create(context: Context): InboxDatabase =
            Room.databaseBuilder(context, InboxDatabase::class.java, "inbox.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
