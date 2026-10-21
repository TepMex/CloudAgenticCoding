package com.tepmex.extensivereadingtutor.storage

import com.tepmex.extensivereadingtutor.domain.AiCallRecord
import com.tepmex.extensivereadingtutor.domain.AnswerDraft
import com.tepmex.extensivereadingtutor.domain.Book
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.CompletedRange
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.LearnerId
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.LessonReview
import com.tepmex.extensivereadingtutor.domain.LessonSession
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationProposal
import com.tepmex.extensivereadingtutor.domain.ObservationRetraction
import com.tepmex.extensivereadingtutor.domain.ProviderConfig
import com.tepmex.extensivereadingtutor.domain.ReadingProgress
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import java.time.Instant

class StoreConstraintException(message: String) : RuntimeException(message)

data class SessionHead(
    val id: SessionId,
    val status: com.tepmex.extensivereadingtutor.domain.SessionStatus,
    val prepareCallId: com.tepmex.extensivereadingtutor.domain.AiCallId?,
    val evaluateCallId: com.tepmex.extensivereadingtutor.domain.AiCallId?,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val observationsAppliedAt: Instant?,
)

interface TutorStore {
    suspend fun <T> transaction(block: (StoreSession) -> T): T
    suspend fun <T> read(block: (StoreSession) -> T): T
}

interface StoreSession {
    fun ensureLearner(learnerId: LearnerId, createdAt: Instant)
    fun preferences(learnerId: LearnerId): LearnerPreferences?
    fun savePreferences(preferences: LearnerPreferences)

    fun insertBook(book: Book)
    fun book(id: BookId): Book?
    fun bookByHash(hash: String): Book?
    fun books(): List<Book>
    fun saveBook(book: Book)

    fun insertBlocks(blocks: List<BookBlock>)
    fun blocks(bookId: BookId): List<BookBlock>
    fun block(id: com.tepmex.extensivereadingtutor.domain.BlockId): BookBlock?

    fun insertFragment(fragment: BookFragment)
    fun fragment(id: FragmentId): BookFragment?

    fun progress(bookId: BookId): ReadingProgress?
    fun saveProgress(progress: ReadingProgress)
    fun addCompletedRange(range: CompletedRange)
    fun completedRanges(bookId: BookId): List<CompletedRange>

    fun insertSession(session: LessonSession)
    fun session(id: SessionId): LessonSession?
    fun saveSessionHead(head: SessionHead)
    fun savePrepared(sessionId: SessionId, introduction: String, hints: List<String>, strategyVersion: String)
    fun activeSession(bookId: BookId): LessonSession?
    fun allSessions(): List<LessonSession>

    fun insertQuestions(questions: List<LessonQuestion>)
    fun questions(sessionId: SessionId): List<LessonQuestion>

    fun saveDraft(draft: AnswerDraft)
    fun drafts(sessionId: SessionId): List<AnswerDraft>

    fun insertSubmission(submission: StudentSubmission)
    fun submission(sessionId: SessionId): StudentSubmission?

    fun insertReview(review: LessonReview)
    fun review(sessionId: SessionId): LessonReview?

    fun proposalByHash(sessionId: SessionId, contentHash: String): ObservationProposal?
    fun insertProposal(proposal: ObservationProposal)
    fun proposals(sessionId: SessionId): List<ObservationProposal>

    fun insertObservation(observation: LearnerObservation)
    fun observation(id: ObservationId): LearnerObservation?
    fun observations(learnerId: LearnerId): List<LearnerObservation>

    fun retraction(observationId: ObservationId): ObservationRetraction?
    fun insertRetraction(retraction: ObservationRetraction)

    fun upsertConcept(concept: Concept)
    fun concepts(): List<Concept>

    fun replaceKnowledge(learnerId: LearnerId, states: List<LearnerKnowledgeState>)
    fun knowledge(learnerId: LearnerId): List<LearnerKnowledgeState>

    fun insertCall(call: AiCallRecord)
    fun callsForSession(sessionId: SessionId): List<AiCallRecord>

    fun upsertProvider(config: ProviderConfig)
    fun provider(id: String): ProviderConfig?

    fun recentQuestionPrompts(learnerId: LearnerId, limit: Int, excludeSession: SessionId?): List<String>
    fun allocateSequence(): Long
    fun eraseLearningHistory(bookId: BookId, now: Instant)
}
