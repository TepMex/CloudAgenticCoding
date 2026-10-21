package com.tepmex.extensivereadingtutor.storage.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tepmex.extensivereadingtutor.domain.AiCallId
import com.tepmex.extensivereadingtutor.domain.AiCallRecord
import com.tepmex.extensivereadingtutor.domain.AiOperation
import com.tepmex.extensivereadingtutor.domain.AnswerDraft
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.Book
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.CallStatus
import com.tepmex.extensivereadingtutor.domain.CompletedRange
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.EpubMetadata
import com.tepmex.extensivereadingtutor.domain.EvidenceStrength
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.HintDepth
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerId
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.LessonReview
import com.tepmex.extensivereadingtutor.domain.LessonSession
import com.tepmex.extensivereadingtutor.domain.LlmCapabilities
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationProposal
import com.tepmex.extensivereadingtutor.domain.ObservationRetraction
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.PreparedLesson
import com.tepmex.extensivereadingtutor.domain.ProposalId
import com.tepmex.extensivereadingtutor.domain.ProposalStatus
import com.tepmex.extensivereadingtutor.domain.ProviderConfig
import com.tepmex.extensivereadingtutor.domain.ProviderKind
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.QuestionReview
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.ReadingProgress
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.SourceSpan
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.domain.SubmittedAnswer
import com.tepmex.extensivereadingtutor.domain.TextAnchor
import com.tepmex.extensivereadingtutor.domain.TextCursor
import com.tepmex.extensivereadingtutor.domain.TokenUsage
import com.tepmex.extensivereadingtutor.storage.SessionHead
import com.tepmex.extensivereadingtutor.storage.StoreConstraintException
import com.tepmex.extensivereadingtutor.storage.StoreSession
import com.tepmex.extensivereadingtutor.storage.TutorStore
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import android.database.sqlite.SQLiteConstraintException

private val json = Json { ignoreUnknownKeys = true }

private fun strings(values: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), values)

private fun strings(raw: String): List<String> = json.decodeFromString(ListSerializer(String.serializer()), raw)

@Serializable
private data class SpanWire(val quote: String, val blockId: String? = null, val startCodePoint: Int? = null, val endCodePoint: Int? = null)

@Serializable
private data class MetaWire(val identifier: String? = null, val publisher: String? = null, val description: String? = null, val rawLanguage: String? = null)

@Serializable
private data class CapWire(val jsonObjectMode: Boolean = true, val reportsUsage: Boolean = true, val systemRole: Boolean = true)

private fun spans(values: List<SourceSpan>): String = json.encodeToString(
    ListSerializer(SpanWire.serializer()),
    values.map { SpanWire(it.quote, it.blockId?.value, it.startCodePoint, it.endCodePoint) },
)

private fun spans(raw: String): List<SourceSpan> = json.decodeFromString(ListSerializer(SpanWire.serializer()), raw).map {
    SourceSpan(it.quote, it.blockId?.let(::BlockId), it.startCodePoint, it.endCodePoint)
}

private fun Instant.millis(): Long = toEpochMilli()
private fun Long.instant(): Instant = Instant.ofEpochMilli(this)
private fun Long?.instantOrNull(): Instant? = this?.let { Instant.ofEpochMilli(it) }

@Entity(tableName = "learners")
data class LearnerEntity(@PrimaryKey val id: String, val createdAt: Long)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val learnerId: String,
    val studyLanguage: String,
    val explanationLanguage: String,
    val selfAssessedLevel: String,
    val preferredDifficulty: String,
    val hintDepth: String,
    val targetFragmentCodePoints: Int,
    val updatedAt: Long,
)

@Entity(tableName = "books", indices = [Index(value = ["contentHash"], unique = true)])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val language: String,
    val sourceUri: String,
    val localRef: String,
    val contentHash: String,
    val importedAt: Long,
    val metadataJson: String,
    val removedAt: Long?,
)

@Entity(tableName = "book_blocks", indices = [Index("bookId")])
data class BlockEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val spineIndex: Int,
    val spineHref: String,
    val orderInBook: Int,
    val text: String,
    val textHash: String,
    val chapterTitle: String?,
)

@Entity(tableName = "fragments")
data class FragmentEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val originalText: String,
    val textHash: String,
    val chapterTitle: String?,
    val chapterHref: String?,
    val codePointLength: Int,
    val segmentationVersion: String,
)

@Entity(tableName = "fragment_anchors", indices = [Index("fragmentId")])
data class AnchorEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val fragmentId: String,
    val ordinal: Int,
    val bookId: String,
    val spineIndex: Int,
    val spineHref: String,
    val blockId: String,
    val startCodePoint: Int,
    val endCodePoint: Int,
    val quote: String,
    val quoteSha256: String,
)

@Entity(tableName = "reading_progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val blockOrder: Int,
    val codePoint: Int,
    val activeSessionId: String?,
    val completedFragmentIds: String,
    val updatedAt: Long,
)

@Entity(tableName = "completed_ranges", indices = [Index("bookId")])
data class CompletedEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val sessionId: String,
    val fragmentId: String,
    val completedAt: Long,
)

@Entity(tableName = "lesson_sessions", indices = [Index("bookId"), Index("fragmentId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val fragmentId: String,
    val learnerId: String,
    val strategyVersion: String,
    val createdAt: Long,
    val status: String,
    val prepareCallId: String?,
    val evaluateCallId: String?,
    val updatedAt: Long,
    val completedAt: Long?,
    val observationsAppliedAt: Long?,
    val introduction: String?,
    val hintsJson: String,
    val preparedStrategy: String?,
)

@Entity(tableName = "lesson_questions", indices = [Index("sessionId")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val prompt: String,
    val targetSkill: String,
    val targetExpression: String?,
    val sourceAnchorsJson: String,
    val orderIndex: Int,
)

@Entity(tableName = "answer_drafts", primaryKeys = ["sessionId", "questionId"])
data class DraftEntity(val sessionId: String, val questionId: String, val text: String, val updatedAt: Long)

@Entity(tableName = "submissions")
data class SubmissionEntity(
    @PrimaryKey val sessionId: String,
    val difficulty: String?,
    val wantsMoreExplanation: Boolean,
    val unclearNotes: String?,
    val freeComment: String?,
    val hintRequestCount: Int,
    val submittedAt: Long,
)

@Entity(tableName = "submission_answers", primaryKeys = ["sessionId", "questionId"])
data class AnswerEntity(val sessionId: String, val questionId: String, val text: String)

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey val sessionId: String,
    val proposalIdsJson: String,
    val recommendationsJson: String,
    val aiCallId: String,
    val createdAt: Long,
)

@Entity(tableName = "question_reviews", primaryKeys = ["sessionId", "questionId"])
data class QuestionReviewEntity(
    val sessionId: String,
    val questionId: String,
    val verdict: String,
    val explanation: String,
    val errorNotes: String,
    val evidenceSufficient: Boolean,
    val examplesJson: String,
)

@Entity(
    tableName = "observation_proposals",
    indices = [Index(value = ["sessionId", "contentHash"], unique = true)],
)
data class ProposalEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val contentHash: String,
    val status: String,
    val rejectionReason: String?,
    val observationId: String?,
    val payloadJson: String,
    val createdAt: Long,
)

@Entity(tableName = "observations", indices = [Index("learnerId"), Index("sessionId")])
data class ObservationEntity(
    @PrimaryKey val id: String,
    val learnerId: String,
    val language: String,
    val sessionId: String,
    val questionId: String?,
    val conceptId: String?,
    val skillType: String,
    val observationType: String,
    val outcome: String,
    val studentEvidence: String,
    val sourceEvidence: String,
    val sourceAnchorsJson: String,
    val interpretation: String,
    val confidence: String,
    val evaluatorProvider: String,
    val evaluatorModel: String,
    val evaluationVersion: String,
    val createdAt: Long,
    val sequence: Long,
)

@Entity(tableName = "retractions")
data class RetractionEntity(
    @PrimaryKey val observationId: String,
    val id: String,
    val reason: String,
    val createdAt: Long,
)

@Entity(tableName = "concepts")
data class ConceptEntity(@PrimaryKey val id: String, val language: String, val label: String)

@Entity(tableName = "knowledge_states", indices = [Index("learnerId")])
data class KnowledgeEntity(
    @PrimaryKey val id: String,
    val learnerId: String,
    val language: String,
    val conceptId: String?,
    val diagnosticKey: String?,
    val skillType: String,
    val status: String,
    val successCount: Int,
    val weakCount: Int,
    val errorCount: Int,
    val lastOutcome: String?,
    val lastObservedAt: Long?,
    val evidenceStrength: String,
    val observationIdsJson: String,
)

@Entity(tableName = "ai_calls", indices = [Index("sessionId")])
data class CallEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val operation: String,
    val providerConfigId: String,
    val providerKind: String,
    val model: String,
    val promptTemplateVersion: String,
    val strategyVersion: String,
    val requestFingerprint: String,
    val status: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val rawJson: String?,
    val startedAt: Long,
    val finishedAt: Long,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val kind: String,
    val baseUrl: String,
    val capabilitiesJson: String,
)

@Entity(tableName = "counters")
data class CounterEntity(@PrimaryKey val name: String, val value: Long)

@Dao
interface TutorDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertLearner(entity: LearnerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPreference(entity: PreferenceEntity)

    @Query("SELECT * FROM preferences WHERE learnerId = :id")
    fun preference(id: String): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBook(entity: BookEntity)

    @Update
    fun updateBook(entity: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    fun book(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentHash = :hash")
    fun bookByHash(hash: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBlocks(entities: List<BlockEntity>)

    @Query("SELECT * FROM book_blocks WHERE bookId = :bookId ORDER BY orderInBook")
    fun blocks(bookId: String): List<BlockEntity>

    @Query("SELECT * FROM book_blocks WHERE id = :id")
    fun block(id: String): BlockEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFragment(entity: FragmentEntity)

    @Query("SELECT * FROM fragments WHERE id = :id")
    fun fragment(id: String): FragmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAnchors(entities: List<AnchorEntity>)

    @Query("SELECT * FROM fragment_anchors WHERE fragmentId = :id ORDER BY ordinal")
    fun anchors(id: String): List<AnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProgress(entity: ProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun progress(bookId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCompleted(entity: CompletedEntity)

    @Query("SELECT * FROM completed_ranges WHERE bookId = :bookId")
    fun completed(bookId: String): List<CompletedEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(entity: SessionEntity)

    @Query("SELECT * FROM lesson_sessions WHERE id = :id")
    fun session(id: String): SessionEntity?

    @Query("SELECT * FROM lesson_sessions WHERE bookId = :bookId AND status != 'COMPLETED' LIMIT 1")
    fun activeSession(bookId: String): SessionEntity?

    @Query("SELECT * FROM lesson_sessions")
    fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM lesson_sessions WHERE learnerId = :learnerId ORDER BY createdAt DESC")
    fun sessionsForLearner(learnerId: String): List<SessionEntity>

    @Query(
        "UPDATE lesson_sessions SET status = :status, prepareCallId = :prepareCallId, evaluateCallId = :evaluateCallId, " +
            "updatedAt = :updatedAt, completedAt = :completedAt, observationsAppliedAt = :observationsAppliedAt WHERE id = :id",
    )
    fun updateHead(id: String, status: String, prepareCallId: String?, evaluateCallId: String?, updatedAt: Long, completedAt: Long?, observationsAppliedAt: Long?)

    @Query("UPDATE lesson_sessions SET introduction = :introduction, hintsJson = :hints, preparedStrategy = :strategy WHERE id = :id")
    fun updatePrepared(id: String, introduction: String, hints: String, strategy: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertQuestions(entities: List<QuestionEntity>)

    @Query("SELECT * FROM lesson_questions WHERE sessionId = :sessionId ORDER BY orderIndex")
    fun questions(sessionId: String): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDraft(entity: DraftEntity)

    @Query("SELECT * FROM answer_drafts WHERE sessionId = :sessionId")
    fun drafts(sessionId: String): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSubmission(entity: SubmissionEntity)

    @Query("SELECT * FROM submissions WHERE sessionId = :sessionId")
    fun submission(sessionId: String): SubmissionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAnswers(entities: List<AnswerEntity>)

    @Query("SELECT * FROM submission_answers WHERE sessionId = :sessionId")
    fun answers(sessionId: String): List<AnswerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertReview(entity: ReviewEntity)

    @Query("SELECT * FROM reviews WHERE sessionId = :sessionId")
    fun review(sessionId: String): ReviewEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertQuestionReviews(entities: List<QuestionReviewEntity>)

    @Query("SELECT * FROM question_reviews WHERE sessionId = :sessionId")
    fun questionReviews(sessionId: String): List<QuestionReviewEntity>

    @Query("SELECT * FROM observation_proposals WHERE sessionId = :sessionId AND contentHash = :hash LIMIT 1")
    fun proposalByHash(sessionId: String, hash: String): ProposalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProposal(entity: ProposalEntity)

    @Query("SELECT * FROM observation_proposals WHERE sessionId = :sessionId")
    fun proposals(sessionId: String): List<ProposalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertObservation(entity: ObservationEntity)

    @Query("SELECT * FROM observations WHERE id = :id")
    fun observation(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE learnerId = :learnerId")
    fun observations(learnerId: String): List<ObservationEntity>

    @Query("SELECT * FROM retractions WHERE observationId = :id")
    fun retraction(id: String): RetractionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRetraction(entity: RetractionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertConcept(entity: ConceptEntity)

    @Query("SELECT * FROM concepts")
    fun concepts(): List<ConceptEntity>

    @Query("DELETE FROM knowledge_states WHERE learnerId = :learnerId")
    fun deleteKnowledge(learnerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertKnowledge(entities: List<KnowledgeEntity>)

    @Query("SELECT * FROM knowledge_states WHERE learnerId = :learnerId")
    fun knowledge(learnerId: String): List<KnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCall(entity: CallEntity)

    @Query("SELECT * FROM ai_calls WHERE sessionId = :sessionId")
    fun calls(sessionId: String): List<CallEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProvider(entity: ProviderEntity)

    @Query("SELECT * FROM providers WHERE id = :id")
    fun provider(id: String): ProviderEntity?

    @Query("SELECT value FROM counters WHERE name = 'seq'")
    fun sequence(): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSequence(entity: CounterEntity)

    @Query("UPDATE counters SET value = :value WHERE name = 'seq'")
    fun updateSequence(value: Long)

    @Query("DELETE FROM retractions WHERE observationId IN (SELECT id FROM observations WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId))")
    fun deleteRetractionsForBook(bookId: String)

    @Query("DELETE FROM observations WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteObservationsForBook(bookId: String)

    @Query("DELETE FROM observation_proposals WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteProposalsForBook(bookId: String)

    @Query("DELETE FROM question_reviews WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteQuestionReviewsForBook(bookId: String)

    @Query("DELETE FROM reviews WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteReviewsForBook(bookId: String)

    @Query("DELETE FROM submission_answers WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteAnswersForBook(bookId: String)

    @Query("DELETE FROM submissions WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteSubmissionsForBook(bookId: String)

    @Query("DELETE FROM answer_drafts WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteDraftsForBook(bookId: String)

    @Query("DELETE FROM lesson_questions WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteQuestionsForBook(bookId: String)

    @Query("DELETE FROM ai_calls WHERE sessionId IN (SELECT id FROM lesson_sessions WHERE bookId = :bookId)")
    fun deleteCallsForBook(bookId: String)

    @Query("DELETE FROM lesson_sessions WHERE bookId = :bookId")
    fun deleteSessionsForBook(bookId: String)

    @Query("DELETE FROM fragment_anchors WHERE fragmentId IN (SELECT id FROM fragments WHERE bookId = :bookId)")
    fun deleteAnchorsForBook(bookId: String)

    @Query("DELETE FROM fragments WHERE bookId = :bookId")
    fun deleteFragmentsForBook(bookId: String)

    @Query("DELETE FROM completed_ranges WHERE bookId = :bookId")
    fun deleteCompletedForBook(bookId: String)
}

@Database(
    entities = [
        LearnerEntity::class, PreferenceEntity::class, BookEntity::class, BlockEntity::class,
        FragmentEntity::class, AnchorEntity::class, ProgressEntity::class, CompletedEntity::class,
        SessionEntity::class, QuestionEntity::class, DraftEntity::class, SubmissionEntity::class,
        AnswerEntity::class, ReviewEntity::class, QuestionReviewEntity::class, ProposalEntity::class,
        ObservationEntity::class, RetractionEntity::class, ConceptEntity::class, KnowledgeEntity::class,
        CallEntity::class, ProviderEntity::class, CounterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TutorDatabase : RoomDatabase() {
    abstract fun tutorDao(): TutorDao

    companion object {
        fun open(context: Context): TutorDatabase = Room.databaseBuilder(
            context,
            TutorDatabase::class.java,
            "tutor.db",
        ).addCallback(object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_active_book ON lesson_sessions(bookId) WHERE status != 'COMPLETED'")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_active_fragment ON lesson_sessions(fragmentId) WHERE status != 'COMPLETED'")
            }
        }).build()
    }
}

class RoomTutorStore(private val database: TutorDatabase) : TutorStore {
    private val dao = database.tutorDao()

    override suspend fun <T> transaction(block: (StoreSession) -> T): T =
        database.withTransaction { guard { block(RoomSession(dao)) } }

    override suspend fun <T> read(block: (StoreSession) -> T): T =
        database.withTransaction { block(RoomSession(dao)) }
}

private fun <T> guard(block: () -> T): T = try {
    block()
} catch (error: SQLiteConstraintException) {
    throw StoreConstraintException(error.message ?: "constraint")
}

private class RoomSession(private val dao: TutorDao) : StoreSession {
    override fun ensureLearner(learnerId: LearnerId, createdAt: Instant) {
        dao.insertLearner(LearnerEntity(learnerId.value, createdAt.millis()))
    }

    override fun preferences(learnerId: LearnerId): LearnerPreferences? = dao.preference(learnerId.value)?.let {
        LearnerPreferences(
            LearnerId(it.learnerId), it.studyLanguage, it.explanationLanguage, it.selfAssessedLevel,
            DifficultyPreference.valueOf(it.preferredDifficulty), HintDepth.valueOf(it.hintDepth),
            it.targetFragmentCodePoints, it.updatedAt.instant(),
        )
    }

    override fun savePreferences(preferences: LearnerPreferences) {
        dao.upsertPreference(
            PreferenceEntity(
                preferences.learnerId.value, preferences.studyLanguage, preferences.explanationLanguage,
                preferences.selfAssessedLevel, preferences.preferredDifficulty.name, preferences.hintDepth.name,
                preferences.targetFragmentCodePoints, preferences.updatedAt.millis(),
            ),
        )
    }

    override fun insertBook(book: Book) = guard { dao.insertBook(book.toEntity()) }
    override fun book(id: BookId): Book? = dao.book(id.value)?.toDomain()
    override fun bookByHash(hash: String): Book? = dao.bookByHash(hash)?.toDomain()
    override fun saveBook(book: Book) { dao.updateBook(book.toEntity()) }

    override fun insertBlocks(blocks: List<BookBlock>) = guard {
        dao.insertBlocks(blocks.map {
            BlockEntity(it.id.value, it.bookId.value, it.spineIndex, it.spineHref, it.orderInBook, it.text, it.textHash, it.chapterTitle)
        })
    }

    override fun blocks(bookId: BookId): List<BookBlock> = dao.blocks(bookId.value).map {
        BookBlock(BlockId(it.id), BookId(it.bookId), it.spineIndex, it.spineHref, it.orderInBook, it.text, it.textHash, it.chapterTitle)
    }

    override fun block(id: BlockId): BookBlock? = dao.block(id.value)?.let {
        BookBlock(BlockId(it.id), BookId(it.bookId), it.spineIndex, it.spineHref, it.orderInBook, it.text, it.textHash, it.chapterTitle)
    }

    override fun insertFragment(fragment: BookFragment) = guard {
        dao.insertFragment(
            FragmentEntity(
                fragment.id.value, fragment.bookId.value, fragment.originalText, fragment.textHash,
                fragment.chapterTitle, fragment.chapterHref, fragment.codePointLength, fragment.segmentationVersion,
            ),
        )
        dao.insertAnchors(fragment.anchors.mapIndexed { index, anchor ->
            AnchorEntity(
                fragmentId = fragment.id.value, ordinal = index, bookId = anchor.bookId.value,
                spineIndex = anchor.spineIndex, spineHref = anchor.spineHref, blockId = anchor.blockId.value,
                startCodePoint = anchor.startCodePoint, endCodePoint = anchor.endCodePoint,
                quote = anchor.quote, quoteSha256 = anchor.quoteSha256,
            )
        })
    }

    override fun fragment(id: FragmentId): BookFragment? {
        val row = dao.fragment(id.value) ?: return null
        val anchors = dao.anchors(id.value).map {
            TextAnchor(BookId(it.bookId), it.spineIndex, it.spineHref, BlockId(it.blockId), it.startCodePoint, it.endCodePoint, it.quote, it.quoteSha256)
        }
        return BookFragment(FragmentId(row.id), BookId(row.bookId), anchors, row.originalText, row.textHash, row.chapterTitle, row.chapterHref, row.codePointLength, row.segmentationVersion)
    }

    override fun progress(bookId: BookId): ReadingProgress? = dao.progress(bookId.value)?.let {
        ReadingProgress(
            BookId(it.bookId), TextCursor(it.blockOrder, it.codePoint), it.activeSessionId?.let(::SessionId),
            strings(it.completedFragmentIds).map(::FragmentId), it.updatedAt.instant(),
        )
    }

    override fun saveProgress(progress: ReadingProgress) {
        dao.upsertProgress(
            ProgressEntity(
                progress.bookId.value, progress.cursor.blockOrder, progress.cursor.codePoint,
                progress.activeSessionId?.value, strings(progress.completedFragmentIds.map { it.value }),
                progress.updatedAt.millis(),
            ),
        )
    }

    override fun addCompletedRange(range: CompletedRange) {
        dao.insertCompleted(CompletedEntity(range.id, range.bookId.value, range.sessionId.value, range.fragmentId.value, range.completedAt.millis()))
    }

    override fun completedRanges(bookId: BookId): List<CompletedRange> = dao.completed(bookId.value).map {
        CompletedRange(it.id, BookId(it.bookId), SessionId(it.sessionId), FragmentId(it.fragmentId), it.completedAt.instant())
    }

    override fun insertSession(session: LessonSession) = guard {
        val activeBook = dao.activeSession(session.bookId.value)
        if (activeBook != null) throw StoreConstraintException("active book session")
        dao.insertSession(
            SessionEntity(
                session.id.value, session.bookId.value, session.fragmentId.value, session.learnerId.value,
                session.strategyVersion, session.createdAt.millis(), session.status.name, session.prepareCallId?.value,
                session.evaluateCallId?.value, session.updatedAt.millis(), session.completedAt?.millis(),
                session.observationsAppliedAt?.millis(), null, strings(emptyList<String>()), null,
            ),
        )
    }

    override fun session(id: SessionId): LessonSession? = dao.session(id.value)?.let(::assemble)

    override fun saveSessionHead(head: SessionHead) {
        dao.updateHead(
            head.id.value, head.status.name, head.prepareCallId?.value, head.evaluateCallId?.value,
            head.updatedAt.millis(), head.completedAt?.millis(), head.observationsAppliedAt?.millis(),
        )
    }

    override fun savePrepared(sessionId: SessionId, introduction: String, hints: List<String>, strategyVersion: String) {
        val row = dao.session(sessionId.value) ?: throw StoreConstraintException("missing session")
        if (row.introduction != null) throw StoreConstraintException("prepared lesson")
        dao.updatePrepared(sessionId.value, introduction, strings(hints), strategyVersion)
    }

    override fun activeSession(bookId: BookId): LessonSession? = dao.activeSession(bookId.value)?.let(::assemble)
    override fun allSessions(): List<LessonSession> = dao.allSessions().map(::assemble)

    override fun insertQuestions(questions: List<LessonQuestion>) = guard {
        dao.insertQuestions(questions.map {
            QuestionEntity(it.id.value, it.sessionId.value, it.type.name, it.prompt, it.targetSkill.name, it.targetExpression, spans(it.sourceAnchors), it.order)
        })
    }

    override fun questions(sessionId: SessionId): List<LessonQuestion> = dao.questions(sessionId.value).map {
        LessonQuestion(QuestionId(it.id), SessionId(it.sessionId), QuestionType.valueOf(it.type), it.prompt, SkillType.valueOf(it.targetSkill), it.targetExpression, spans(it.sourceAnchorsJson), it.orderIndex)
    }

    override fun saveDraft(draft: AnswerDraft) {
        dao.upsertDraft(DraftEntity(draft.sessionId.value, draft.questionId.value, draft.text, draft.updatedAt.millis()))
    }

    override fun drafts(sessionId: SessionId): List<AnswerDraft> = dao.drafts(sessionId.value).map {
        AnswerDraft(SessionId(it.sessionId), QuestionId(it.questionId), it.text, it.updatedAt.instant())
    }

    override fun insertSubmission(submission: StudentSubmission) = guard {
        dao.insertSubmission(
            SubmissionEntity(
                submission.sessionId.value, submission.difficulty?.name, submission.wantsMoreExplanation,
                submission.unclearNotes, submission.freeComment, submission.hintRequestCount, submission.submittedAt.millis(),
            ),
        )
        dao.insertAnswers(submission.answers.map { AnswerEntity(submission.sessionId.value, it.questionId.value, it.text) })
    }

    override fun submission(sessionId: SessionId): StudentSubmission? {
        val row = dao.submission(sessionId.value) ?: return null
        return StudentSubmission(
            SessionId(row.sessionId),
            dao.answers(sessionId.value).map { SubmittedAnswer(QuestionId(it.questionId), it.text) },
            row.difficulty?.let { SubjectiveDifficulty.valueOf(it) },
            row.wantsMoreExplanation, row.unclearNotes, row.freeComment, row.hintRequestCount, row.submittedAt.instant(),
        )
    }

    override fun insertReview(review: LessonReview) = guard {
        dao.insertReview(ReviewEntity(review.sessionId.value, strings(review.observationProposalIds.map { it.value }), strings(review.nextRecommendations), review.aiCallId.value, review.createdAt.millis()))
        dao.insertQuestionReviews(review.results.map {
            QuestionReviewEntity(review.sessionId.value, it.questionId.value, it.verdict.name, it.explanation, it.errorNotes, it.evidenceSufficient, strings(it.examples))
        })
    }

    override fun review(sessionId: SessionId): LessonReview? {
        val row = dao.review(sessionId.value) ?: return null
        return LessonReview(
            SessionId(row.sessionId),
            dao.questionReviews(sessionId.value).map {
                QuestionReview(QuestionId(it.questionId), AnswerVerdict.valueOf(it.verdict), it.explanation, it.errorNotes, it.evidenceSufficient, strings(it.examplesJson))
            },
            strings(row.proposalIdsJson).map(::ProposalId),
            strings(row.recommendationsJson),
            AiCallId(row.aiCallId),
            row.createdAt.instant(),
        )
    }

    override fun proposalByHash(sessionId: SessionId, contentHash: String): ObservationProposal? =
        dao.proposalByHash(sessionId.value, contentHash)?.toDomain()

    override fun insertProposal(proposal: ObservationProposal) = guard {
        dao.insertProposal(
            ProposalEntity(
                proposal.id.value, proposal.sessionId.value, proposal.contentHash, proposal.status.name,
                proposal.rejectionReason, proposal.observationId?.value, proposal.payloadJson, proposal.createdAt.millis(),
            ),
        )
    }

    override fun proposals(sessionId: SessionId): List<ObservationProposal> = dao.proposals(sessionId.value).map { it.toDomain() }

    override fun insertObservation(observation: LearnerObservation) = guard {
        dao.insertObservation(
            ObservationEntity(
                observation.id.value, observation.learnerId.value, observation.language, observation.sessionId.value,
                observation.questionId?.value, observation.conceptId?.value, observation.skillType.name,
                observation.observationType.name, observation.outcome.name, observation.studentEvidence,
                observation.sourceEvidence, spans(observation.sourceAnchors), observation.interpretation,
                observation.confidence.name, observation.evaluatorProvider, observation.evaluatorModel,
                observation.evaluationVersion, observation.createdAt.millis(), observation.sequence,
            ),
        )
    }

    override fun observation(id: ObservationId): LearnerObservation? = dao.observation(id.value)?.toDomain()

    override fun observations(learnerId: LearnerId): List<LearnerObservation> = dao.observations(learnerId.value).map { it.toDomain() }

    override fun retraction(observationId: ObservationId): ObservationRetraction? = dao.retraction(observationId.value)?.let {
        ObservationRetraction(it.id, ObservationId(it.observationId), it.reason, it.createdAt.instant())
    }

    override fun insertRetraction(retraction: ObservationRetraction) = guard {
        dao.insertRetraction(RetractionEntity(retraction.observationId.value, retraction.id, retraction.reason, retraction.createdAt.millis()))
    }

    override fun upsertConcept(concept: Concept) {
        dao.insertConcept(ConceptEntity(concept.id.value, concept.language, concept.label))
    }

    override fun concepts(): List<Concept> = dao.concepts().map { Concept(ConceptId(it.id), it.language, it.label) }

    override fun replaceKnowledge(learnerId: LearnerId, states: List<LearnerKnowledgeState>) {
        dao.deleteKnowledge(learnerId.value)
        dao.insertKnowledge(states.map {
            KnowledgeEntity(
                it.id, it.learnerId.value, it.language, it.conceptId?.value, it.diagnosticKey, it.skillType.name,
                it.status.name, it.successCount, it.weakCount, it.errorCount, it.lastOutcome?.name,
                it.lastObservedAt?.millis(), it.evidenceStrength.name, strings(it.observationIds.map { id -> id.value }),
            )
        })
    }

    override fun knowledge(learnerId: LearnerId): List<LearnerKnowledgeState> = dao.knowledge(learnerId.value).map {
        LearnerKnowledgeState(
            it.id, LearnerId(it.learnerId), it.language, it.conceptId?.let(::ConceptId), it.diagnosticKey,
            SkillType.valueOf(it.skillType), KnowledgeStatus.valueOf(it.status), it.successCount, it.weakCount,
            it.errorCount, it.lastOutcome?.let { name -> Outcome.valueOf(name) }, it.lastObservedAt.instantOrNull(),
            EvidenceStrength.valueOf(it.evidenceStrength), strings(it.observationIdsJson).map(::ObservationId),
        )
    }

    override fun insertCall(call: AiCallRecord) {
        dao.insertCall(
            CallEntity(
                call.id.value, call.sessionId.value, call.operation.name, call.providerConfigId, call.providerKind,
                call.model, call.promptTemplateVersion, call.strategyVersion, call.requestFingerprint, call.status.name,
                call.promptTokens, call.completionTokens, call.totalTokens, call.rawJson, call.startedAt.millis(), call.finishedAt.millis(),
            ),
        )
    }

    override fun callsForSession(sessionId: SessionId): List<AiCallRecord> = dao.calls(sessionId.value).map {
        AiCallRecord(
            AiCallId(it.id), SessionId(it.sessionId), AiOperation.valueOf(it.operation), it.providerConfigId,
            it.providerKind, it.model, it.promptTemplateVersion, it.strategyVersion, it.requestFingerprint,
            CallStatus.valueOf(it.status), it.promptTokens, it.completionTokens, it.totalTokens, it.rawJson,
            it.startedAt.instant(), it.finishedAt.instant(),
        )
    }

    override fun upsertProvider(config: ProviderConfig) {
        dao.upsertProvider(
            ProviderEntity(
                config.id, config.displayName, config.kind.name, config.baseUrl,
                json.encodeToString(CapWire.serializer(), CapWire(config.capabilities.jsonObjectMode, config.capabilities.reportsUsage, config.capabilities.systemRole)),
            ),
        )
    }

    override fun provider(id: String): ProviderConfig? = dao.provider(id)?.let {
        val caps = json.decodeFromString(CapWire.serializer(), it.capabilitiesJson)
        ProviderConfig(it.id, it.displayName, ProviderKind.valueOf(it.kind), it.baseUrl, LlmCapabilities(caps.jsonObjectMode, caps.reportsUsage, caps.systemRole))
    }

    override fun recentQuestionPrompts(learnerId: LearnerId, limit: Int, excludeSession: SessionId?): List<String> {
        val prompts = mutableListOf<String>()
        for (row in dao.sessionsForLearner(learnerId.value)) {
            if (row.id == excludeSession?.value) continue
            for (question in dao.questions(row.id)) {
                prompts += question.prompt
                if (prompts.size >= limit) return prompts
            }
        }
        return prompts
    }

    override fun allocateSequence(): Long {
        val current = dao.sequence()
        val next = (current ?: 0L) + 1
        if (current == null) dao.insertSequence(CounterEntity("seq", next)) else dao.updateSequence(next)
        return next
    }

    override fun eraseLearningHistory(bookId: BookId, now: Instant) {
        val id = bookId.value
        dao.deleteRetractionsForBook(id)
        dao.deleteObservationsForBook(id)
        dao.deleteProposalsForBook(id)
        dao.deleteQuestionReviewsForBook(id)
        dao.deleteReviewsForBook(id)
        dao.deleteAnswersForBook(id)
        dao.deleteSubmissionsForBook(id)
        dao.deleteDraftsForBook(id)
        dao.deleteQuestionsForBook(id)
        dao.deleteCallsForBook(id)
        dao.deleteSessionsForBook(id)
        dao.deleteAnchorsForBook(id)
        dao.deleteFragmentsForBook(id)
        dao.deleteCompletedForBook(id)
        val first = dao.blocks(id).minOfOrNull { it.orderInBook } ?: 0
        dao.upsertProgress(ProgressEntity(id, first, 0, null, strings(emptyList()), now.millis()))
    }

    private fun assemble(row: SessionEntity): LessonSession {
        val questions = questions(SessionId(row.id))
        val prepared = row.introduction?.let {
            PreparedLesson(it, strings(row.hintsJson), questions, row.preparedStrategy ?: row.strategyVersion)
        }
        return LessonSession(
            SessionId(row.id), BookId(row.bookId), FragmentId(row.fragmentId), LearnerId(row.learnerId),
            SessionStatus.valueOf(row.status), row.strategyVersion, row.prepareCallId?.let(::AiCallId),
            row.evaluateCallId?.let(::AiCallId), row.createdAt.instant(), row.updatedAt.instant(),
            row.completedAt.instantOrNull(), row.observationsAppliedAt.instantOrNull(),
            prepared, submission(SessionId(row.id)), review(SessionId(row.id)),
        )
    }
}

private fun Book.toEntity() = BookEntity(
    id.value, title, author, language, sourceUri, localRef, contentHash, importedAt.millis(),
    json.encodeToString(MetaWire.serializer(), MetaWire(metadata.identifier, metadata.publisher, metadata.description, metadata.rawLanguage)),
    removedAt?.millis(),
)

private fun BookEntity.toDomain(): Book {
    val meta = json.decodeFromString(MetaWire.serializer(), metadataJson)
    return Book(
        BookId(id), title, author, language, sourceUri, localRef, contentHash, importedAt.instant(),
        EpubMetadata(meta.identifier, meta.publisher, meta.description, meta.rawLanguage), removedAt.instantOrNull(),
    )
}

private fun ProposalEntity.toDomain() = ObservationProposal(
    ProposalId(id), SessionId(sessionId), contentHash, ProposalStatus.valueOf(status), rejectionReason,
    observationId?.let(::ObservationId), payloadJson, createdAt.instant(),
)

private fun ObservationEntity.toDomain() = LearnerObservation(
    ObservationId(id), LearnerId(learnerId), language, SessionId(sessionId), questionId?.let(::QuestionId),
    conceptId?.let(::ConceptId), SkillType.valueOf(skillType), ObservationType.valueOf(observationType),
    Outcome.valueOf(outcome), studentEvidence, sourceEvidence, spans(sourceAnchorsJson), interpretation,
    Confidence.valueOf(confidence), evaluatorProvider, evaluatorModel, evaluationVersion, createdAt.instant(), sequence,
)
