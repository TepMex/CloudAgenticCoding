package com.tepmex.extensivereadingtutor.storage

import com.tepmex.extensivereadingtutor.domain.AiCallId
import com.tepmex.extensivereadingtutor.domain.AssistanceEvent
import com.tepmex.extensivereadingtutor.domain.AttemptFreeze
import com.tepmex.extensivereadingtutor.domain.ContinuityNote
import com.tepmex.extensivereadingtutor.domain.ExplainRecord
import com.tepmex.extensivereadingtutor.domain.LearnerSelection
import com.tepmex.extensivereadingtutor.domain.SelectionId
import com.tepmex.extensivereadingtutor.domain.AiCallRecord
import com.tepmex.extensivereadingtutor.domain.AnswerDraft
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.Book
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.CompletedRange
import com.tepmex.extensivereadingtutor.domain.Concept
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
import com.tepmex.extensivereadingtutor.domain.PreparedLesson
import com.tepmex.extensivereadingtutor.domain.ProviderConfig
import com.tepmex.extensivereadingtutor.domain.ReadingProgress
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.TextCursor
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTutorStore : TutorStore {
    private val mutex = Mutex()
    private var mem = Mem()

    override suspend fun <T> transaction(block: (StoreSession) -> T): T = mutex.withLock {
        val before = mem
        val working = before.deepCopy()
        try {
            val result = block(Session(working))
            mem = working
            result
        } catch (error: Throwable) {
            mem = before
            throw error
        }
    }

    override suspend fun <T> read(block: (StoreSession) -> T): T = mutex.withLock {
        block(Session(mem.deepCopy()))
    }

    private class Mem {
        var sequence = 0L
        val learners = mutableSetOf<LearnerId>()
        val preferences = mutableMapOf<LearnerId, LearnerPreferences>()
        val books = mutableMapOf<BookId, Book>()
        val blocks = mutableListOf<BookBlock>()
        val fragments = mutableMapOf<FragmentId, BookFragment>()
        val progress = mutableMapOf<BookId, ReadingProgress>()
        val completed = mutableListOf<CompletedRange>()
        val sessions = mutableMapOf<SessionId, SessionRow>()
        val questions = mutableListOf<LessonQuestion>()
        val drafts = mutableMapOf<Pair<SessionId, com.tepmex.extensivereadingtutor.domain.QuestionId>, AnswerDraft>()
        val submissions = mutableMapOf<SessionId, StudentSubmission>()
        val reviews = mutableMapOf<SessionId, LessonReview>()
        val proposals = mutableListOf<ObservationProposal>()
        val observations = mutableMapOf<ObservationId, LearnerObservation>()
        val retractions = mutableMapOf<ObservationId, ObservationRetraction>()
        val concepts = mutableMapOf<com.tepmex.extensivereadingtutor.domain.ConceptId, Concept>()
        val knowledge = mutableListOf<LearnerKnowledgeState>()
        val calls = mutableListOf<AiCallRecord>()
        val providers = mutableMapOf<String, ProviderConfig>()
        val selections = mutableListOf<LearnerSelection>()
        val freezes = mutableMapOf<SessionId, AttemptFreeze>()
        val assistance = mutableListOf<AssistanceEvent>()
        val explains = mutableListOf<ExplainRecord>()
        val continuity = mutableMapOf<BookId, List<ContinuityNote>>()
        val snapshots = mutableMapOf<Pair<SessionId, String>, String>()
        val provenance = mutableMapOf<AiCallId, String>()

        fun deepCopy(): Mem = Mem().also { copy ->
            copy.sequence = sequence
            copy.learners += learners
            copy.preferences += preferences
            copy.books += books
            copy.blocks += blocks
            copy.fragments += fragments
            copy.progress += progress
            copy.completed += completed
            sessions.forEach { (id, row) -> copy.sessions[id] = row.copy() }
            copy.questions += questions
            copy.drafts += drafts
            copy.submissions += submissions
            copy.reviews += reviews
            copy.proposals += proposals
            copy.observations += observations
            copy.retractions += retractions
            copy.concepts += concepts
            copy.knowledge += knowledge
            copy.calls += calls
            copy.providers += providers
            copy.selections += selections
            copy.freezes += freezes
            copy.assistance += assistance
            copy.explains += explains
            copy.continuity += continuity
            copy.snapshots += snapshots
            copy.provenance += provenance
        }
    }

    private data class SessionRow(
        val id: SessionId,
        val bookId: BookId,
        val fragmentId: FragmentId,
        val learnerId: LearnerId,
        val strategyVersion: String,
        val createdAt: Instant,
        val status: SessionStatus,
        val prepareCallId: AiCallId?,
        val evaluateCallId: AiCallId?,
        val updatedAt: Instant,
        val completedAt: Instant?,
        val observationsAppliedAt: Instant?,
        val introduction: String?,
        val hints: List<String>,
        val preparedStrategy: String?,
    )

    private class Session(private val mem: Mem) : StoreSession {
        override fun ensureLearner(learnerId: LearnerId, createdAt: Instant) {
            mem.learners += learnerId
        }

        override fun preferences(learnerId: LearnerId): LearnerPreferences? = mem.preferences[learnerId]

        override fun savePreferences(preferences: LearnerPreferences) {
            mem.preferences[preferences.learnerId] = preferences
        }

        override fun insertBook(book: Book) {
            if (mem.books.values.any { it.contentHash == book.contentHash }) {
                throw StoreConstraintException("book hash")
            }
            mem.books[book.id] = book
        }

        override fun book(id: BookId): Book? = mem.books[id]

        override fun bookByHash(hash: String): Book? = mem.books.values.firstOrNull { it.contentHash == hash }

        override fun books(): List<Book> = mem.books.values.sortedByDescending { it.importedAt }

        override fun saveBook(book: Book) {
            if (mem.books[book.id] == null) throw StoreConstraintException("missing book")
            mem.books[book.id] = book
        }

        override fun insertBlocks(blocks: List<BookBlock>) {
            mem.blocks += blocks
        }

        override fun blocks(bookId: BookId): List<BookBlock> =
            mem.blocks.filter { it.bookId == bookId }.sortedBy { it.orderInBook }

        override fun block(id: BlockId): BookBlock? = mem.blocks.firstOrNull { it.id == id }

        override fun insertFragment(fragment: BookFragment) {
            if (mem.fragments.containsKey(fragment.id)) throw StoreConstraintException("fragment")
            mem.fragments[fragment.id] = fragment
        }

        override fun fragment(id: FragmentId): BookFragment? = mem.fragments[id]

        override fun progress(bookId: BookId): ReadingProgress? = mem.progress[bookId]

        override fun saveProgress(progress: ReadingProgress) {
            mem.progress[progress.bookId] = progress
        }

        override fun addCompletedRange(range: CompletedRange) {
            mem.completed += range
        }

        override fun completedRanges(bookId: BookId): List<CompletedRange> =
            mem.completed.filter { it.bookId == bookId }

        override fun insertSession(session: LessonSession) {
            if (mem.sessions.values.any { it.bookId == session.bookId && it.status != SessionStatus.COMPLETED }) {
                throw StoreConstraintException("active book session")
            }
            if (mem.sessions.values.any { it.fragmentId == session.fragmentId && it.status != SessionStatus.COMPLETED }) {
                throw StoreConstraintException("active fragment session")
            }
            mem.sessions[session.id] = SessionRow(
                id = session.id,
                bookId = session.bookId,
                fragmentId = session.fragmentId,
                learnerId = session.learnerId,
                strategyVersion = session.strategyVersion,
                createdAt = session.createdAt,
                status = session.status,
                prepareCallId = session.prepareCallId,
                evaluateCallId = session.evaluateCallId,
                updatedAt = session.updatedAt,
                completedAt = session.completedAt,
                observationsAppliedAt = session.observationsAppliedAt,
                introduction = null,
                hints = emptyList(),
                preparedStrategy = null,
            )
        }

        override fun session(id: SessionId): LessonSession? = mem.sessions[id]?.let(::assemble)

        override fun saveSessionHead(head: SessionHead) {
            val row = mem.sessions[head.id] ?: throw StoreConstraintException("missing session")
            mem.sessions[head.id] = row.copy(
                status = head.status,
                prepareCallId = head.prepareCallId,
                evaluateCallId = head.evaluateCallId,
                updatedAt = head.updatedAt,
                completedAt = head.completedAt,
                observationsAppliedAt = head.observationsAppliedAt,
            )
        }

        override fun savePrepared(sessionId: SessionId, introduction: String, hints: List<String>, strategyVersion: String) {
            val row = mem.sessions[sessionId] ?: throw StoreConstraintException("missing session")
            if (row.introduction != null) throw StoreConstraintException("prepared lesson")
            mem.sessions[sessionId] = row.copy(
                introduction = introduction,
                hints = hints,
                preparedStrategy = strategyVersion,
            )
        }

        override fun activeSession(bookId: BookId): LessonSession? =
            mem.sessions.values
                .filter { it.bookId == bookId && it.status != SessionStatus.COMPLETED }
                .map(::assemble)
                .firstOrNull()

        override fun allSessions(): List<LessonSession> = mem.sessions.values.map(::assemble)

        override fun insertQuestions(questions: List<LessonQuestion>) {
            mem.questions += questions
        }

        override fun questions(sessionId: SessionId): List<LessonQuestion> =
            mem.questions.filter { it.sessionId == sessionId }.sortedBy { it.order }

        override fun saveDraft(draft: AnswerDraft) {
            mem.drafts[draft.sessionId to draft.questionId] = draft
        }

        override fun drafts(sessionId: SessionId): List<AnswerDraft> =
            mem.drafts.values.filter { it.sessionId == sessionId }

        override fun insertSubmission(submission: StudentSubmission) {
            if (mem.submissions.containsKey(submission.sessionId)) throw StoreConstraintException("submission")
            mem.submissions[submission.sessionId] = submission
        }

        override fun submission(sessionId: SessionId): StudentSubmission? = mem.submissions[sessionId]

        override fun insertReview(review: LessonReview) {
            if (mem.reviews.containsKey(review.sessionId)) throw StoreConstraintException("review")
            mem.reviews[review.sessionId] = review
        }

        override fun review(sessionId: SessionId): LessonReview? = mem.reviews[sessionId]

        override fun proposalByHash(sessionId: SessionId, contentHash: String): ObservationProposal? =
            mem.proposals.firstOrNull { it.sessionId == sessionId && it.contentHash == contentHash }

        override fun insertProposal(proposal: ObservationProposal) {
            if (proposalByHash(proposal.sessionId, proposal.contentHash) != null) {
                throw StoreConstraintException("proposal")
            }
            mem.proposals += proposal
        }

        override fun proposals(sessionId: SessionId): List<ObservationProposal> =
            mem.proposals.filter { it.sessionId == sessionId }

        override fun insertObservation(observation: LearnerObservation) {
            if (mem.observations.containsKey(observation.id)) throw StoreConstraintException("observation")
            mem.observations[observation.id] = observation
        }

        override fun observation(id: ObservationId): LearnerObservation? = mem.observations[id]

        override fun observations(learnerId: LearnerId): List<LearnerObservation> =
            mem.observations.values.filter { it.learnerId == learnerId }

        override fun retraction(observationId: ObservationId): ObservationRetraction? = mem.retractions[observationId]

        override fun insertRetraction(retraction: ObservationRetraction) {
            if (mem.retractions.containsKey(retraction.observationId)) throw StoreConstraintException("retraction")
            mem.retractions[retraction.observationId] = retraction
        }

        override fun upsertConcept(concept: Concept) {
            mem.concepts.putIfAbsent(concept.id, concept)
        }

        override fun concepts(): List<Concept> = mem.concepts.values.toList()

        override fun replaceKnowledge(learnerId: LearnerId, states: List<LearnerKnowledgeState>) {
            mem.knowledge.removeAll { it.learnerId == learnerId }
            mem.knowledge += states
        }

        override fun knowledge(learnerId: LearnerId): List<LearnerKnowledgeState> =
            mem.knowledge.filter { it.learnerId == learnerId }

        override fun insertCall(call: AiCallRecord) {
            mem.calls += call
        }

        override fun callsForSession(sessionId: SessionId): List<AiCallRecord> =
            mem.calls.filter { it.sessionId == sessionId }

        override fun upsertProvider(config: ProviderConfig) {
            mem.providers[config.id] = config
        }

        override fun provider(id: String): ProviderConfig? = mem.providers[id]

        override fun selections(sessionId: SessionId): List<LearnerSelection> =
            mem.selections.filter { it.sessionId == sessionId }

        override fun saveSelection(selection: LearnerSelection) {
            mem.selections.removeAll { it.id == selection.id }
            mem.selections += selection
        }

        override fun attemptFreeze(sessionId: SessionId): AttemptFreeze? = mem.freezes[sessionId]

        override fun saveAttemptFreeze(freeze: AttemptFreeze) {
            if (mem.freezes.containsKey(freeze.sessionId)) throw StoreConstraintException("freeze")
            mem.freezes[freeze.sessionId] = freeze
        }

        override fun assistanceEvents(sessionId: SessionId): List<AssistanceEvent> =
            mem.assistance.filter { it.sessionId == sessionId }

        override fun insertAssistance(event: AssistanceEvent) {
            mem.assistance += event
        }

        override fun explainRecords(sessionId: SessionId): List<ExplainRecord> =
            mem.explains.filter { it.sessionId == sessionId }

        override fun saveExplainRecord(record: ExplainRecord) {
            mem.explains.removeAll { it.id == record.id }
            mem.explains += record
        }

        override fun continuityNotes(bookId: BookId): List<ContinuityNote> = mem.continuity[bookId].orEmpty()

        override fun saveContinuityNotes(bookId: BookId, notes: List<ContinuityNote>) {
            mem.continuity[bookId] = notes
        }

        override fun inputSnapshot(sessionId: SessionId, operation: String): String? = mem.snapshots[sessionId to operation]

        override fun saveInputSnapshot(sessionId: SessionId, operation: String, json: String) {
            mem.snapshots.putIfAbsent(sessionId to operation, json)
        }

        override fun provenance(callId: AiCallId): String? = mem.provenance[callId]

        override fun saveProvenance(callId: AiCallId, json: String) {
            mem.provenance[callId] = json
        }

        override fun recentQuestionTypes(learnerId: LearnerId, limit: Int, excludeSession: SessionId?): List<String> {
            val ordered = mem.sessions.values
                .filter { it.learnerId == learnerId && it.id != excludeSession }
                .sortedByDescending { it.createdAt }
            val types = mutableListOf<String>()
            for (row in ordered) {
                for (question in questions(row.id)) {
                    types += question.type.name
                    if (types.size >= limit) return types
                }
            }
            return types
        }

        override fun recentQuestionPrompts(learnerId: LearnerId, limit: Int, excludeSession: SessionId?): List<String> {
            val ordered = mem.sessions.values
                .filter { it.learnerId == learnerId && it.id != excludeSession }
                .sortedByDescending { it.createdAt }
            val prompts = mutableListOf<String>()
            for (row in ordered) {
                for (question in questions(row.id)) {
                    prompts += question.prompt
                    if (prompts.size >= limit) return prompts
                }
            }
            return prompts
        }

        override fun allocateSequence(): Long {
            mem.sequence += 1
            return mem.sequence
        }

        override fun eraseLearningHistory(bookId: BookId, now: Instant) {
            val sessionIds = mem.sessions.values.filter { it.bookId == bookId }.map { it.id }.toSet()
            val observationIds = mem.observations.values.filter { it.sessionId in sessionIds }.map { it.id }.toSet()
            mem.observations.keys.removeAll(observationIds)
            mem.retractions.keys.removeAll(observationIds)
            mem.proposals.removeAll { it.sessionId in sessionIds }
            sessionIds.forEach {
                mem.reviews.remove(it)
                mem.submissions.remove(it)
            }
            mem.drafts.keys.removeAll { it.first in sessionIds }
            mem.questions.removeAll { it.sessionId in sessionIds }
            mem.calls.removeAll { it.sessionId in sessionIds }
            mem.selections.removeAll { it.sessionId in sessionIds }
            sessionIds.forEach { mem.freezes.remove(it) }
            mem.assistance.removeAll { it.sessionId in sessionIds }
            mem.explains.removeAll { it.sessionId in sessionIds }
            mem.continuity.remove(bookId)
            mem.snapshots.keys.removeAll { it.first in sessionIds }
            sessionIds.forEach { mem.sessions.remove(it) }
            mem.fragments.entries.removeAll { it.value.bookId == bookId }
            mem.completed.removeAll { it.bookId == bookId }
            val first = mem.blocks.filter { it.bookId == bookId }.minOfOrNull { it.orderInBook } ?: 0
            mem.progress[bookId] = ReadingProgress(
                bookId = bookId,
                cursor = TextCursor(first, 0),
                activeSessionId = null,
                completedFragmentIds = emptyList(),
                updatedAt = now,
            )
        }

        private fun assemble(row: SessionRow): LessonSession {
            val prepared = row.introduction?.let { introduction ->
                PreparedLesson(
                    introduction = introduction,
                    hints = row.hints,
                    questions = questions(row.id),
                    strategyVersion = row.preparedStrategy ?: row.strategyVersion,
                )
            }
            return LessonSession(
                id = row.id,
                bookId = row.bookId,
                fragmentId = row.fragmentId,
                learnerId = row.learnerId,
                status = row.status,
                strategyVersion = row.strategyVersion,
                prepareCallId = row.prepareCallId,
                evaluateCallId = row.evaluateCallId,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                completedAt = row.completedAt,
                observationsAppliedAt = row.observationsAppliedAt,
                prepared = prepared,
                submission = mem.submissions[row.id],
                review = mem.reviews[row.id],
            )
        }
    }
}
