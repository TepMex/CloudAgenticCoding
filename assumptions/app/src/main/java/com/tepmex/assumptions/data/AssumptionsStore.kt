package com.tepmex.assumptions.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tepmex.assumptions.domain.EmotionEntry
import com.tepmex.assumptions.domain.Emotions
import com.tepmex.assumptions.domain.Expectation
import com.tepmex.assumptions.domain.PleasureScale
import com.tepmex.assumptions.domain.expectationErrors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "emotion_entries")
data class EmotionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emotionId: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "expectations")
data class ExpectationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventText: String,
    val expectedOutcome: String,
    val beforeScore: Int,
    val afterScore: Int?,
    val afterNote: String,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long?,
)

@Dao
interface AssumptionsDao {
    @Query("SELECT * FROM emotion_entries ORDER BY createdAtEpochMs DESC, id DESC")
    fun observeEmotions(): Flow<List<EmotionEntryEntity>>

    @Insert
    suspend fun insertEmotion(entry: EmotionEntryEntity)

    @Query("DELETE FROM emotion_entries WHERE id = :id")
    suspend fun deleteEmotion(id: Long)

    @Query("SELECT * FROM expectations ORDER BY createdAtEpochMs DESC, id DESC")
    fun observeExpectations(): Flow<List<ExpectationEntity>>

    @Insert
    suspend fun insertExpectation(entry: ExpectationEntity)

    @Query(
        """
        UPDATE expectations
        SET afterScore = :afterScore,
            afterNote = :afterNote,
            resolvedAtEpochMs = :resolvedAt
        WHERE id = :id
        """,
    )
    suspend fun resolve(id: Long, afterScore: Int, afterNote: String, resolvedAt: Long)

    @Query("DELETE FROM expectations WHERE id = :id")
    suspend fun deleteExpectation(id: Long)
}

@Database(
    entities = [EmotionEntryEntity::class, ExpectationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AssumptionsDatabase : RoomDatabase() {
    abstract fun dao(): AssumptionsDao

    companion object {
        fun create(context: Context): AssumptionsDatabase =
            Room.databaseBuilder(context, AssumptionsDatabase::class.java, "assumptions.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

class AssumptionsRepository(
    private val dao: AssumptionsDao,
) {
    fun emotions(): Flow<List<EmotionEntry>> =
        dao.observeEmotions().map { rows ->
            rows.map { EmotionEntry(it.id, it.emotionId, it.createdAtEpochMs) }
        }

    fun expectations(): Flow<List<Expectation>> =
        dao.observeExpectations().map { rows -> rows.map { it.toModel() } }

    suspend fun recordEmotion(emotionId: String, nowEpochMs: Long = System.currentTimeMillis()) {
        check(Emotions.find(emotionId) != null) { "Unknown emotion $emotionId" }
        dao.insertEmotion(EmotionEntryEntity(emotionId = emotionId, createdAtEpochMs = nowEpochMs))
    }

    suspend fun deleteEmotion(id: Long) {
        dao.deleteEmotion(id)
    }

    suspend fun addExpectation(
        event: String,
        outcome: String,
        beforeScore: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        check(expectationErrors(event, outcome, beforeScore).isEmpty())
        dao.insertExpectation(
            ExpectationEntity(
                eventText = event.trim(),
                expectedOutcome = outcome.trim(),
                beforeScore = beforeScore,
                afterScore = null,
                afterNote = "",
                createdAtEpochMs = nowEpochMs,
                resolvedAtEpochMs = null,
            ),
        )
    }

    suspend fun resolve(
        id: Long,
        afterScore: Int,
        note: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        check(PleasureScale.contains(afterScore))
        dao.resolve(id, afterScore, note.trim(), nowEpochMs)
    }

    suspend fun deleteExpectation(id: Long) {
        dao.deleteExpectation(id)
    }
}

private fun ExpectationEntity.toModel(): Expectation = Expectation(
    id = id,
    eventText = eventText,
    expectedOutcome = expectedOutcome,
    beforeScore = beforeScore,
    afterScore = afterScore,
    afterNote = afterNote,
    createdAtEpochMs = createdAtEpochMs,
    resolvedAtEpochMs = resolvedAtEpochMs,
)
