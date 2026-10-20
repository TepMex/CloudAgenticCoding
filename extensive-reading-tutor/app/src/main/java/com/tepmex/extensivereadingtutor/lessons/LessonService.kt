package com.tepmex.extensivereadingtutor.lessons

import com.tepmex.extensivereadingtutor.books.FragmentSelector
import com.tepmex.extensivereadingtutor.books.locateInFragment
import com.tepmex.extensivereadingtutor.books.precedingText
import com.tepmex.extensivereadingtutor.domain.AiCallId
import com.tepmex.extensivereadingtutor.domain.AiCallRecord
import com.tepmex.extensivereadingtutor.domain.AiOperation
import com.tepmex.extensivereadingtutor.domain.AnswerDraft
import com.tepmex.extensivereadingtutor.domain.AppClock
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.CallStatus
import com.tepmex.extensivereadingtutor.domain.CompletedRange
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.IdGenerator
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.LessonReview
import com.tepmex.extensivereadingtutor.domain.LessonSession
import com.tepmex.extensivereadingtutor.domain.ModelSource
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationProposal
import com.tepmex.extensivereadingtutor.domain.PreparedLesson
import com.tepmex.extensivereadingtutor.domain.ProposalId
import com.tepmex.extensivereadingtutor.domain.ProposalStatus
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.QuestionReview
import com.tepmex.extensivereadingtutor.domain.ReadingProgress
import com.tepmex.extensivereadingtutor.domain.SessionEvent
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.domain.defaultPreferences
import com.tepmex.extensivereadingtutor.domain.require
import com.tepmex.extensivereadingtutor.learner.LearnerService
import com.tepmex.extensivereadingtutor.learner.ObservationPolicy
import com.tepmex.extensivereadingtutor.learner.ProposalInput
import com.tepmex.extensivereadingtutor.llm.EvaluationDraft
import com.tepmex.extensivereadingtutor.llm.EvaluateLessonInput
import com.tepmex.extensivereadingtutor.llm.LessonAi
import com.tepmex.extensivereadingtutor.llm.PrepareLessonInput
import com.tepmex.extensivereadingtutor.llm.PreparedDraft
import com.tepmex.extensivereadingtutor.llm.fingerprint
import com.tepmex.extensivereadingtutor.llm.questionIdOrNull
import com.tepmex.extensivereadingtutor.pedagogy.ContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.MemoryPacket
import com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy
import com.tepmex.extensivereadingtutor.storage.SessionHead
import com.tepmex.extensivereadingtutor.storage.StoreConstraintException
import com.tepmex.extensivereadingtutor.storage.StoreSession
import com.tepmex.extensivereadingtutor.storage.TutorStore

class LessonService(
    private val store: TutorStore,
    private val ai: LessonAi,
    private val learner: LearnerService,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val models: ModelSource,
    private val strategy: PedagogicalStrategy = PedagogicalStrategy.V1,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val policy: ObservationPolicy = ObservationPolicy(),
) {
    suspend fun startOrResumeLesson(bookId: BookId): AppResult<LessonSession> {
        val created = try {
            store.transaction { db -> startIn(db, bookId) }
        } catch (error: StoreConstraintException) {
            val active = store.read { it.activeSession(bookId) }
            if (active != null) return AppResult.Ok(active)
            return AppResult.Err(DomainError.LessonNotReady(error.message ?: "Занятие уже есть."))
        }
        return created
    }

    private fun startIn(db: StoreSession, bookId: BookId): AppResult<LessonSession> {
        val now = clock.now()
        db.ensureLearner(LOCAL_LEARNER, now)
        if (db.preferences(LOCAL_LEARNER) == null) db.savePreferences(defaultPreferences(now))
        val book = db.book(bookId) ?: return AppResult.Err(DomainError.BookNotFound(bookId.value))
        if (book.removedAt != null) return AppResult.Err(DomainError.BookRemoved(bookId.value))
        db.activeSession(bookId)?.let { return AppResult.Ok(it) }
        val prefs = db.preferences(LOCAL_LEARNER)!!
        val blocks = db.blocks(bookId)
        val progress = db.progress(bookId) ?: ReadingProgress(
            bookId, com.tepmex.extensivereadingtutor.domain.TextCursor(0, 0), null, emptyList(), now,
        )
        val target = FragmentSelector.targetFor(prefs.targetFragmentCodePoints, multiplier(prefs.preferredDifficulty))
        val slices = FragmentSelector.select(blocks, progress.cursor, target, FragmentSelector.hardMax(target))
        if (slices.isEmpty()) return AppResult.Err(DomainError.NothingLeftToRead)
        val fragment = FragmentSelector.materialize(bookId, com.tepmex.extensivereadingtutor.domain.FragmentId(ids.newId("fragment")), slices)
        val session = LessonSession(
            id = SessionId(ids.newId("lesson")),
            bookId = bookId,
            fragmentId = fragment.id,
            learnerId = LOCAL_LEARNER,
            status = SessionStatus.CREATED,
            strategyVersion = strategy.version,
            prepareCallId = null,
            evaluateCallId = null,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            observationsAppliedAt = null,
            prepared = null,
            submission = null,
            review = null,
        )
        db.insertFragment(fragment)
        db.insertSession(session)
        db.saveProgress(progress.copy(activeSessionId = session.id, updatedAt = now))
        return AppResult.Ok(db.session(session.id)!!)
    }

    suspend fun prepareLesson(sessionId: SessionId): AppResult<LessonSession> {
        val model = models.current()
        val gate = store.transaction { db ->
            val session = db.session(sessionId)
                ?: return@transaction PrepareGate.Finished(AppResult.Err(DomainError.SessionNotFound(sessionId.value)))
            if (session.prepared != null) return@transaction PrepareGate.Finished(AppResult.Ok(session))
            if (model.model.isBlank()) return@transaction PrepareGate.Finished(AppResult.Err(DomainError.ModelNotConfigured))
            val preparing = session.status.require(SessionEvent.BEGIN_PREPARE)
            if (preparing is AppResult.Err) return@transaction PrepareGate.Finished(preparing)
            db.saveSessionHead(session.head((preparing as AppResult.Ok).value))
            val fragment = db.fragment(session.fragmentId)!!
            val prefs = db.preferences(session.learnerId) ?: defaultPreferences(clock.now())
            val blocks = db.blocks(session.bookId)
            PrepareGate.Run(
                PrepareWork(
                    fragment = fragment,
                    input = PrepareLessonInput(
                        providerConfigId = model.providerConfigId,
                        providerKind = model.providerKind,
                        model = model.model,
                        strategy = strategy,
                        preferences = prefs,
                        chapterTitle = fragment.chapterTitle,
                        precedingText = precedingText(blocks, fragment).takeIf { it.isNotEmpty() },
                        fragmentText = fragment.originalText,
                        memory = memory(db, fragment.originalText, session.id),
                    ),
                ),
            )
        }
        if (gate is PrepareGate.Finished) return gate.result
        val work = (gate as PrepareGate.Run).work
        val started = clock.now()
        val drafted = ai.prepareLesson(work.input)
        if (drafted is AppResult.Err) {
            abortPrepare(sessionId)
            return drafted
        }
        return try {
            store.transaction { db -> commitPrepare(db, sessionId, work, (drafted as AppResult.Ok).value, started) }
        } catch (error: StoreConstraintException) {
            store.read { it.session(sessionId) }?.let { AppResult.Ok(it) }
                ?: AppResult.Err(DomainError.LessonNotReady(error.message ?: "Подготовка уже сохранена."))
        }
    }

    private fun commitPrepare(
        db: StoreSession,
        sessionId: SessionId,
        work: PrepareWork,
        draft: PreparedDraft,
        started: java.time.Instant,
    ): AppResult<LessonSession> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.prepared != null) return AppResult.Ok(session)
        val now = clock.now()
        val kept = draft.questions.mapNotNull { question ->
            val hit = locateInFragment(work.fragment, question.sourceQuote) ?: return@mapNotNull null
            question to hit
        }
        val callId = AiCallId(ids.newId("call"))
        if (kept.isEmpty()) {
            db.insertCall(callRecord(callId, sessionId, AiOperation.PREPARE_LESSON, work.input.providerConfigId, draft.providerKind, draft.model, strategy.prepareTemplateVersion, CallStatus.REJECTED_OUTPUT, draft, fingerprint("prepare", draft.model, work.fragment.originalText), started, now))
            val aborted = session.status.require(SessionEvent.ABORT_PREPARE)
            if (aborted is AppResult.Ok) db.saveSessionHead(session.head(aborted.value, updatedAt = now))
            return AppResult.Err(DomainError.InvalidModelOutput("Цитаты вопросов не найдены в оригинале."))
        }
        val questions = kept.mapIndexed { index, (question, hit) ->
            LessonQuestion(
                id = QuestionId(ids.newId("q")),
                sessionId = sessionId,
                type = question.type,
                prompt = question.prompt,
                targetSkill = question.targetSkill,
                targetExpression = question.targetExpression,
                sourceAnchors = listOf(hit.span),
                order = index,
            )
        }
        val ready = session.status.require(SessionEvent.COMMIT_PREPARE)
        if (ready is AppResult.Err) return ready
        db.insertCall(callRecord(callId, sessionId, AiOperation.PREPARE_LESSON, work.input.providerConfigId, draft.providerKind, draft.model, strategy.prepareTemplateVersion, CallStatus.SUCCEEDED, draft, fingerprint("prepare", draft.model, work.fragment.originalText), started, now))
        db.insertQuestions(questions)
        db.savePrepared(sessionId, draft.introduction, draft.hints, strategy.version)
        db.saveSessionHead(session.head((ready as AppResult.Ok).value, prepareCallId = callId, updatedAt = now))
        return AppResult.Ok(db.session(sessionId)!!)
    }

    suspend fun revealQuestions(sessionId: SessionId): AppResult<LessonSession> = store.transaction { db ->
        val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.status != SessionStatus.INTRO_READY) {
            if (session.status.ordinal >= SessionStatus.QUESTIONS_READY.ordinal && session.prepared != null) {
                return@transaction AppResult.Ok(session)
            }
            return@transaction AppResult.Err(DomainError.IllegalTransition(session.status.name, SessionEvent.REVEAL_QUESTIONS.name))
        }
        val next = session.status.require(SessionEvent.REVEAL_QUESTIONS)
        if (next is AppResult.Err) return@transaction next
        db.saveSessionHead(session.head((next as AppResult.Ok).value))
        AppResult.Ok(db.session(sessionId)!!)
    }

    suspend fun saveAnswerDraft(sessionId: SessionId, questionId: QuestionId, text: String): AppResult<LessonSession> =
        store.transaction { db ->
            val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
            if (session.submission != null) return@transaction AppResult.Err(DomainError.AnswersLocked(sessionId.value))
            val questions = session.prepared?.questions.orEmpty()
            if (questions.none { it.id == questionId }) {
                return@transaction AppResult.Err(DomainError.QuestionNotFound(questionId.value))
            }
            val status = when (session.status) {
                SessionStatus.QUESTIONS_READY -> {
                    val next = session.status.require(SessionEvent.BEGIN_ANSWERING)
                    if (next is AppResult.Err) return@transaction next
                    (next as AppResult.Ok).value
                }
                SessionStatus.AWAITING_ANSWERS -> session.status
                else -> return@transaction AppResult.Err(DomainError.IllegalTransition(session.status.name, "saveAnswerDraft"))
            }
            db.saveDraft(AnswerDraft(sessionId, questionId, text, clock.now()))
            if (status != session.status) db.saveSessionHead(session.head(status))
            AppResult.Ok(db.session(sessionId)!!)
        }

    suspend fun submitAnswers(sessionId: SessionId, submission: StudentSubmission): AppResult<LessonSession> =
        store.transaction { db ->
            val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
            val questions = session.prepared?.questions.orEmpty()
            if (questions.isEmpty()) return@transaction AppResult.Err(DomainError.LessonNotReady("Вопросы ещё не открыты."))
            val existing = session.submission
            if (existing != null) {
                return@transaction if (sameSubmission(existing, submission)) {
                    AppResult.Ok(session)
                } else {
                    AppResult.Err(DomainError.SubmissionMismatch("Ответы уже зафиксированы."))
                }
            }
            if (session.status != SessionStatus.QUESTIONS_READY && session.status != SessionStatus.AWAITING_ANSWERS) {
                return@transaction AppResult.Err(DomainError.IllegalTransition(session.status.name, "submitAnswers"))
            }
            val expected = questions.map { it.id }.toSet()
            val given = submission.answers.map { it.questionId }.toSet()
            if (given != expected) {
                return@transaction AppResult.Err(DomainError.SubmissionMismatch("Нужен ответ на каждый вопрос."))
            }
            db.insertSubmission(submission.copy(sessionId = sessionId, submittedAt = submission.submittedAt))
            val status = if (session.status == SessionStatus.QUESTIONS_READY) {
                val next = session.status.require(SessionEvent.BEGIN_ANSWERING)
                if (next is AppResult.Err) return@transaction next
                (next as AppResult.Ok).value
            } else {
                session.status
            }
            if (status != session.status) db.saveSessionHead(session.head(status))
            AppResult.Ok(db.session(sessionId)!!)
        }

    suspend fun evaluateAnswers(sessionId: SessionId): AppResult<LessonSession> {
        val model = models.current()
        val gate = store.transaction { db ->
            val session = db.session(sessionId)
                ?: return@transaction EvalGate.Finished(AppResult.Err(DomainError.SessionNotFound(sessionId.value)))
            if (session.review != null) return@transaction EvalGate.Finished(AppResult.Ok(session))
            if (session.submission == null) return@transaction EvalGate.Finished(AppResult.Err(DomainError.LessonNotReady("Сначала отправьте ответы.")))
            if (model.model.isBlank()) return@transaction EvalGate.Finished(AppResult.Err(DomainError.ModelNotConfigured))
            val evaluating = session.status.require(SessionEvent.BEGIN_EVALUATE)
            if (evaluating is AppResult.Err) return@transaction EvalGate.Finished(evaluating)
            db.saveSessionHead(session.head((evaluating as AppResult.Ok).value))
            val fragment = db.fragment(session.fragmentId)!!
            val prefs = db.preferences(session.learnerId) ?: defaultPreferences(clock.now())
            EvalGate.Run(
                EvalWork(
                    fragment = fragment,
                    input = EvaluateLessonInput(
                        providerConfigId = model.providerConfigId,
                        providerKind = model.providerKind,
                        model = model.model,
                        strategy = strategy,
                        preferences = prefs,
                        fragmentText = fragment.originalText,
                        questions = session.prepared?.questions.orEmpty(),
                        submission = session.submission,
                        memory = memory(db, fragment.originalText, session.id),
                    ),
                ),
            )
        }
        if (gate is EvalGate.Finished) return gate.result
        val work = (gate as EvalGate.Run).work
        val started = clock.now()
        val drafted = ai.evaluateAnswers(work.input)
        if (drafted is AppResult.Err) return drafted
        return try {
            store.transaction { db -> commitReview(db, sessionId, work, (drafted as AppResult.Ok).value, started) }
        } catch (error: StoreConstraintException) {
            val saved = store.read { it.session(sessionId) }
            if (saved?.review != null) AppResult.Ok(saved)
            else AppResult.Err(DomainError.LessonNotReady(error.message ?: "Разбор уже сохранён."))
        }
    }

    private fun commitReview(
        db: StoreSession,
        sessionId: SessionId,
        work: EvalWork,
        draft: EvaluationDraft,
        started: java.time.Instant,
    ): AppResult<LessonSession> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.review != null) return AppResult.Ok(session)
        val now = clock.now()
        val callId = AiCallId(ids.newId("call"))
        val book = db.book(session.bookId)
        val language = book?.language ?: work.input.preferences.studyLanguage
        val submission = session.submission ?: return AppResult.Err(DomainError.LessonNotReady("Нет ответов."))
        val proposalIds = mutableListOf<ProposalId>()
        for (rejected in draft.rejectedObservations) {
            rememberProposal(db, sessionId, shaOf(rejected.payloadJson), ProposalStatus.REJECTED, rejected.reason, null, rejected.payloadJson, now, proposalIds)
        }
        for (item in draft.observations) {
            val decision = policy.decide(
                ProposalInput(
                    questionId = questionIdOrNull(item.questionId),
                    conceptLabel = item.conceptLabel,
                    skillType = item.skillType,
                    observationType = item.observationType,
                    outcome = item.outcome,
                    studentEvidence = item.studentEvidence,
                    sourceQuote = item.sourceQuote,
                    interpretation = item.interpretation,
                    confidence = item.confidence,
                    payloadJson = item.payloadJson,
                ),
                work.fragment,
                submission,
                language,
                sessionId,
            )
            when (decision) {
                is com.tepmex.extensivereadingtutor.learner.PolicyDecision.Reject ->
                    rememberProposal(db, sessionId, decision.contentHash, ProposalStatus.REJECTED, decision.reason, null, item.payloadJson, now, proposalIds)
                is com.tepmex.extensivereadingtutor.learner.PolicyDecision.Accept -> {
                    if (db.proposalByHash(sessionId, decision.contentHash) != null) continue
                    decision.concept?.let { db.upsertConcept(it) }
                    val observationId = ObservationId(ids.newId("obs"))
                    db.insertObservation(
                        LearnerObservation(
                            id = observationId,
                            learnerId = session.learnerId,
                            language = language,
                            sessionId = sessionId,
                            questionId = questionIdOrNull(item.questionId),
                            conceptId = decision.concept?.id,
                            skillType = item.skillType,
                            observationType = item.observationType,
                            outcome = item.outcome,
                            studentEvidence = decision.studentEvidence,
                            sourceEvidence = decision.sourceEvidence,
                            sourceAnchors = listOf(decision.span),
                            interpretation = item.interpretation.trim(),
                            confidence = item.confidence,
                            evaluatorProvider = draft.providerKind,
                            evaluatorModel = draft.model,
                            evaluationVersion = strategy.evaluateTemplateVersion,
                            createdAt = now,
                            sequence = db.allocateSequence(),
                        ),
                    )
                    rememberProposal(db, sessionId, decision.contentHash, ProposalStatus.ACCEPTED, null, observationId, item.payloadJson, now, proposalIds)
                }
            }
        }
        val review = LessonReview(
            sessionId = sessionId,
            results = draft.reviews.map {
                QuestionReview(
                    questionId = QuestionId(it.questionId),
                    verdict = it.verdict,
                    explanation = it.explanation,
                    errorNotes = it.errorNotes,
                    evidenceSufficient = it.evidenceSufficient,
                    examples = it.examples,
                )
            },
            observationProposalIds = proposalIds,
            nextRecommendations = draft.nextRecommendations,
            aiCallId = callId,
            createdAt = now,
        )
        val ready = session.status.require(SessionEvent.COMMIT_REVIEW)
        if (ready is AppResult.Err) return ready
        db.insertCall(
            callRecord(
                callId,
                sessionId,
                AiOperation.EVALUATE_ANSWERS,
                work.input.providerConfigId,
                draft.providerKind,
                draft.model,
                strategy.evaluateTemplateVersion,
                CallStatus.SUCCEEDED,
                draft.usage,
                draft.rawJson,
                fingerprint("evaluate", draft.model, work.fragment.originalText),
                started,
                now,
            ),
        )
        db.insertReview(review)
        learner.replaceProjection(db, session.learnerId)
        db.saveSessionHead(
            session.head(
                (ready as AppResult.Ok).value,
                evaluateCallId = callId,
                updatedAt = now,
                observationsAppliedAt = now,
            ),
        )
        return AppResult.Ok(db.session(sessionId)!!)
    }

    suspend fun completeLesson(sessionId: SessionId): AppResult<LessonSession> = store.transaction { db ->
        val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.status == SessionStatus.COMPLETED) return@transaction AppResult.Ok(session)
        if (session.review == null || session.observationsAppliedAt == null) {
            return@transaction AppResult.Err(DomainError.LessonNotReady("Разбор ещё не сохранён."))
        }
        val next = session.status.require(SessionEvent.COMPLETE)
        if (next is AppResult.Err) return@transaction next
        val now = clock.now()
        val fragment = db.fragment(session.fragmentId)!!
        val blocks = db.blocks(session.bookId)
        val progress = db.progress(session.bookId)!!
        val cursor = FragmentSelector.advance(blocks, fragment)
        val completedIds = progress.completedFragmentIds + fragment.id
        db.saveSessionHead(session.head((next as AppResult.Ok).value, updatedAt = now, completedAt = now))
        db.addCompletedRange(
            CompletedRange(
                id = ids.newId("range"),
                bookId = session.bookId,
                sessionId = sessionId,
                fragmentId = fragment.id,
                completedAt = now,
            ),
        )
        db.saveProgress(
            progress.copy(
                cursor = cursor,
                activeSessionId = null,
                completedFragmentIds = completedIds,
                updatedAt = now,
            ),
        )
        val prefs = db.preferences(session.learnerId)
        if (prefs != null) {
            db.savePreferences(
                prefs.copy(
                    targetFragmentCodePoints = nextTarget(prefs.targetFragmentCodePoints, session.submission?.difficulty),
                    updatedAt = now,
                ),
            )
        }
        AppResult.Ok(db.session(sessionId)!!)
    }

    suspend fun getActiveLesson(bookId: BookId): LessonSession? = store.read { it.activeSession(bookId) }

    suspend fun getReadingProgress(bookId: BookId): ReadingProgress? = store.read { it.progress(bookId) }

    suspend fun drafts(sessionId: SessionId): List<AnswerDraft> = store.read { it.drafts(sessionId) }

    suspend fun fragment(id: com.tepmex.extensivereadingtutor.domain.FragmentId): BookFragment? = store.read { it.fragment(id) }

    suspend fun recoverInterruptedSessions(): List<LessonSession> = store.transaction { db ->
        val changed = mutableListOf<LessonSession>()
        val now = clock.now()
        for (session in db.allSessions()) {
            if (session.status == SessionStatus.PREPARING && session.prepared == null) {
                db.saveSessionHead(session.head(SessionStatus.CREATED, updatedAt = now))
                changed += db.session(session.id)!!
            } else if (session.status == SessionStatus.EVALUATING && session.review != null) {
                db.saveSessionHead(
                    session.head(
                        SessionStatus.REVIEW_READY,
                        updatedAt = now,
                        observationsAppliedAt = session.observationsAppliedAt ?: now,
                    ),
                )
                changed += db.session(session.id)!!
            }
        }
        changed
    }

    private suspend fun abortPrepare(sessionId: SessionId) {
        store.transaction { db ->
            val session = db.session(sessionId) ?: return@transaction
            if (session.status == SessionStatus.PREPARING && session.prepared == null) {
                db.saveSessionHead(session.head(SessionStatus.CREATED))
            }
        }
    }

    private fun memory(db: StoreSession, fragmentText: String, sessionId: SessionId): MemoryPacket =
        contextBuilder.build(
            fragmentText = fragmentText,
            knowledge = db.knowledge(LOCAL_LEARNER),
            concepts = db.concepts(),
            recentPrompts = db.recentQuestionPrompts(LOCAL_LEARNER, 12, sessionId),
        )

    private fun rememberProposal(
        db: StoreSession,
        sessionId: SessionId,
        hash: String,
        status: ProposalStatus,
        reason: String?,
        observationId: ObservationId?,
        payload: String,
        now: java.time.Instant,
        idsOut: MutableList<ProposalId>,
    ) {
        val existing = db.proposalByHash(sessionId, hash)
        if (existing != null) {
            idsOut += existing.id
            return
        }
        val id = ProposalId(ids.newId("proposal"))
        db.insertProposal(
            ObservationProposal(
                id = id,
                sessionId = sessionId,
                contentHash = hash,
                status = status,
                rejectionReason = reason,
                observationId = observationId,
                payloadJson = payload,
                createdAt = now,
            ),
        )
        idsOut += id
    }
}

private data class PrepareWork(val fragment: BookFragment, val input: PrepareLessonInput)
private data class EvalWork(val fragment: BookFragment, val input: EvaluateLessonInput)

private sealed interface PrepareGate {
    data class Finished(val result: AppResult<LessonSession>) : PrepareGate
    data class Run(val work: PrepareWork) : PrepareGate
}

private sealed interface EvalGate {
    data class Finished(val result: AppResult<LessonSession>) : EvalGate
    data class Run(val work: EvalWork) : EvalGate
}

private fun LessonSession.head(
    status: SessionStatus,
    prepareCallId: AiCallId? = this.prepareCallId,
    evaluateCallId: AiCallId? = this.evaluateCallId,
    updatedAt: java.time.Instant = this.updatedAt,
    completedAt: java.time.Instant? = this.completedAt,
    observationsAppliedAt: java.time.Instant? = this.observationsAppliedAt,
) = SessionHead(id, status, prepareCallId, evaluateCallId, updatedAt, completedAt, observationsAppliedAt)

private fun multiplier(preference: DifficultyPreference): Double = when (preference) {
    DifficultyPreference.EASY -> 0.85
    DifficultyPreference.MATCHED -> 1.0
    DifficultyPreference.STRETCH -> 1.15
}

private fun nextTarget(current: Int, difficulty: SubjectiveDifficulty?): Int = when (difficulty) {
    SubjectiveDifficulty.TOO_HARD -> (current * 0.8).toInt().coerceAtLeast(40)
    SubjectiveDifficulty.TOO_EASY -> (current * 1.25).toInt().coerceAtMost(1200)
    else -> current
}

private fun sameSubmission(saved: StudentSubmission, incoming: StudentSubmission): Boolean {
    val savedAnswers = saved.answers.associate { it.questionId to it.text }
    val incomingAnswers = incoming.answers.associate { it.questionId to it.text }
    return savedAnswers == incomingAnswers &&
        saved.difficulty == incoming.difficulty &&
        saved.wantsMoreExplanation == incoming.wantsMoreExplanation &&
        saved.unclearNotes == incoming.unclearNotes &&
        saved.freeComment == incoming.freeComment &&
        saved.hintRequestCount == incoming.hintRequestCount
}

private fun callRecord(
    id: AiCallId,
    sessionId: SessionId,
    operation: AiOperation,
    providerConfigId: String,
    providerKind: String,
    model: String,
    template: String,
    status: CallStatus,
    draft: PreparedDraft,
    fingerprint: String,
    started: java.time.Instant,
    finished: java.time.Instant,
) = AiCallRecord(
    id = id,
    sessionId = sessionId,
    operation = operation,
    providerConfigId = providerConfigId,
    providerKind = providerKind,
    model = model,
    promptTemplateVersion = template,
    strategyVersion = PedagogicalStrategy.V1.version,
    requestFingerprint = fingerprint,
    status = status,
    promptTokens = draft.usage?.promptTokens,
    completionTokens = draft.usage?.completionTokens,
    totalTokens = draft.usage?.totalTokens,
    rawJson = draft.rawJson,
    startedAt = started,
    finishedAt = finished,
)

private fun callRecord(
    id: AiCallId,
    sessionId: SessionId,
    operation: AiOperation,
    providerConfigId: String,
    providerKind: String,
    model: String,
    template: String,
    status: CallStatus,
    usage: com.tepmex.extensivereadingtutor.domain.TokenUsage?,
    rawJson: String,
    fingerprint: String,
    started: java.time.Instant,
    finished: java.time.Instant,
) = AiCallRecord(
    id = id,
    sessionId = sessionId,
    operation = operation,
    providerConfigId = providerConfigId,
    providerKind = providerKind,
    model = model,
    promptTemplateVersion = template,
    strategyVersion = PedagogicalStrategy.V1.version,
    requestFingerprint = fingerprint,
    status = status,
    promptTokens = usage?.promptTokens,
    completionTokens = usage?.completionTokens,
    totalTokens = usage?.totalTokens,
    rawJson = rawJson,
    startedAt = started,
    finishedAt = finished,
)

private fun shaOf(payload: String): String = com.tepmex.extensivereadingtutor.books.sha256(payload)
