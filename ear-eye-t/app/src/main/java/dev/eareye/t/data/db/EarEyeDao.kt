package dev.eareye.t.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EarEyeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScreenEvents(events: List<ScreenEventEntity>)

    @Query("DELETE FROM screen_events WHERE timestampMillis >= :fromMillis AND timestampMillis < :toMillis")
    suspend fun deleteScreenEventsInRange(fromMillis: Long, toMillis: Long)

    @Query("SELECT * FROM screen_events ORDER BY timestampMillis, ordinal, eventType")
    suspend fun allScreenEvents(): List<ScreenEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: MonitoringSessionEntity): Long

    @Update
    suspend fun updateSession(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_sessions WHERE endReason IS NULL")
    suspend fun openSessions(): List<MonitoringSessionEntity>

    @Query("SELECT * FROM monitoring_sessions ORDER BY id")
    suspend fun allSessions(): List<MonitoringSessionEntity>

    @Query("SELECT * FROM monitoring_sessions ORDER BY id DESC LIMIT 1")
    suspend fun latestSession(): MonitoringSessionEntity?

    @Insert
    suspend fun insertTransition(transition: AudioTransitionEntity)

    @Query("SELECT * FROM audio_transitions ORDER BY sessionId, elapsedMillis, id")
    suspend fun allTransitions(): List<AudioTransitionEntity>

    @Insert
    suspend fun insertDiagnostic(event: DiagnosticEventEntity)

    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY id DESC LIMIT :limit)")
    suspend fun pruneDiagnostics(limit: Int)

    @Query("SELECT * FROM diagnostic_events ORDER BY id DESC LIMIT :limit")
    suspend fun latestDiagnostics(limit: Int): List<DiagnosticEventEntity>

    @Query("SELECT * FROM diagnostic_events ORDER BY id DESC LIMIT :limit")
    fun observeDiagnostics(limit: Int): Flow<List<DiagnosticEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncState(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun syncState(): SyncStateEntity?

    @Query(
        "UPDATE sync_state SET lastSuccessWallMillis = :successMillis, trustedUntilMillis = :trustedUntilMillis, lastError = NULL WHERE id = 1",
    )
    suspend fun markSyncSuccess(successMillis: Long, trustedUntilMillis: Long)

    @Query("UPDATE sync_state SET lastError = :error WHERE id = 1")
    suspend fun markSyncFailure(error: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGeneration(generation: DataGenerationEntity)

    @Query("SELECT version FROM data_generation WHERE id = 1")
    fun observeGeneration(): Flow<Long?>

    @Query("UPDATE data_generation SET version = version + 1 WHERE id = 1")
    suspend fun bumpGeneration()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettings(settings: AudioSettingsEntity)

    @Query("SELECT * FROM audio_settings WHERE id = 1")
    suspend fun settings(): AudioSettingsEntity?

    @Query(
        """
        UPDATE audio_settings
        SET media = :media, game = :game, assistant = :assistant, navigation = :navigation, accessibility = :accessibility
        WHERE id = 1
        """,
    )
    suspend fun updateSettings(
        media: Boolean,
        game: Boolean,
        assistant: Boolean,
        navigation: Boolean,
        accessibility: Boolean,
    )
}
