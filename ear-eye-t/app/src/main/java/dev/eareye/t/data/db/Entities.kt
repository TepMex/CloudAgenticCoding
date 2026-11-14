package dev.eareye.t.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screen_events",
    indices = [
        Index("timestampMillis"),
        Index(value = ["timestampMillis", "eventType", "ordinal"], unique = true),
    ],
)
data class ScreenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val eventType: Int,
    val ordinal: Int,
)

@Entity(
    tableName = "audio_transitions",
    indices = [
        Index("sessionId"),
        Index("wallMillis"),
    ],
)
data class AudioTransitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val wallMillis: Long,
    val elapsedMillis: Long,
    val bootCount: Int,
    val active: Boolean,
)

@Entity(tableName = "monitoring_sessions")
data class MonitoringSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bootCount: Int,
    val connectedWallMillis: Long,
    val connectedElapsedMillis: Long,
    val lastConfirmedWallMillis: Long,
    val lastConfirmedElapsedMillis: Long,
    val lastCallbackWallMillis: Long?,
    val lastSnapshotWallMillis: Long?,
    val lastHeartbeatWallMillis: Long?,
    val endReason: String?,
    val unexpected: Boolean,
)

@Entity(
    tableName = "diagnostic_events",
    indices = [Index("wallMillis")],
)
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wallMillis: Long,
    val kind: String,
    val message: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastSuccessWallMillis: Long?,
    val trustedUntilMillis: Long?,
    val lastError: String?,
)

@Entity(tableName = "data_generation")
data class DataGenerationEntity(
    @PrimaryKey val id: Int = 1,
    val version: Long,
)

@Entity(tableName = "audio_settings")
data class AudioSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val media: Boolean = true,
    val game: Boolean = true,
    val assistant: Boolean = false,
    val navigation: Boolean = false,
    val accessibility: Boolean = false,
)
