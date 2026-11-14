package dev.eareye.t.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScreenEventEntity::class,
        AudioTransitionEntity::class,
        MonitoringSessionEntity::class,
        DiagnosticEventEntity::class,
        SyncStateEntity::class,
        DataGenerationEntity::class,
        AudioSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EarEyeDatabase : RoomDatabase() {
    abstract fun dao(): EarEyeDao

    companion object {
        fun create(context: Context): EarEyeDatabase {
            return Room.databaseBuilder(context, EarEyeDatabase::class.java, "ear-eye.db")
                .build()
        }
    }
}
