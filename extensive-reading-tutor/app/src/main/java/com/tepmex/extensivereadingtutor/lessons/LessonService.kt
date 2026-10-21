package com.tepmex.extensivereadingtutor.lessons

import com.tepmex.extensivereadingtutor.books.FragmentSelector
import com.tepmex.extensivereadingtutor.books.locateInFragment
import com.tepmex.extensivereadingtutor.books.locateQuote
import com.tepmex.extensivereadingtutor.books.mapSelection
import com.tepmex.extensivereadingtutor.books.nfc
import com.tepmex.extensivereadingtutor.books.precedingText
import com.tepmex.extensivereadingtutor.books.primaryLanguage
import com.tepmex.extensivereadingtutor.books.sha256
import com.tepmex.extensivereadingtutor.books.utf16ToCodePointRange
import com.tepmex.extensivereadingtutor.domain.AiCallId
import com.tepmex.extensivereadingtutor.domain.AiCallRecord
import com.tepmex.extensivereadingtutor.domain.AiOperation
import com.tepmex.extensivereadingtutor.domain.AnswerDraft
import com.tepmex.extensivereadingtutor.domain.AppClock
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.AttemptFreeze
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.CallStatus
import com.tepmex.extensivereadingtutor.domain.CompletedRange
import com.tepmex.extensivereadingtutor.domain.ContinuityNote
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.explain
import com.tepmex.extensivereadingtutor.domain.ExplainRecord
import com.tepmex.extensivereadingtutor.domain.FrozenAssistance
import com.tepmex.extensivereadingtutor.domain.FrozenExplanation
import com.tepmex.extensivereadingtutor.domain.FrozenSelection
import com.tepmex.extensivereadingtutor.domain.HelpTiming
import com.tepmex.extensivereadingtutor.domain.IdGenerator
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LearnerSelection
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
import com.tepmex.extensivereadingtutor.domain.RequestedPhase
import com.tepmex.extensivereadingtutor.domain.SelectionId
import com.tepmex.extensivereadingtutor.domain.SelectionRequestType
import com.tepmex.extensivereadingtutor.domain.SelectionReview
import com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus
import com.tepmex.extensivereadingtutor.domain.SessionEvent
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.domain.defaultPreferences
import com.tepmex.extensivereadingtutor.domain.require
import com.tepmex.extensivereadingtutor.learner.LearnerService
import com.tepmex.extensivereadingtutor.learner.ObservationPolicy
import com.tepmex.extensivereadingtutor.learner.ObservationPolicyV2
import com.tepmex.extensivereadingtutor.learner.ProposalInput
import com.tepmex.extensivereadingtutor.learner.V2Decision
import com.tepmex.extensivereadingtutor.learner.V2Proposal
import com.tepmex.extensivereadingtutor.llm.EvaluationDraft
import com.tepmex.extensivereadingtutor.llm.EvaluateLessonInput
import com.tepmex.extensivereadingtutor.llm.ExplainDraft
import com.tepmex.extensivereadingtutor.llm.ExplainSelectionInput
import com.tepmex.extensivereadingtutor.llm.LessonAi
import com.tepmex.extensivereadingtutor.llm.ObservationDraft
import com.tepmex.extensivereadingtutor.llm.PrepareLessonInput
import com.tepmex.extensivereadingtutor.llm.PreparedDraft
import com.tepmex.extensivereadingtutor.llm.SelectionReviewDraft
import com.tepmex.extensivereadingtutor.llm.fingerprint
import com.tepmex.extensivereadingtutor.llm.questionIdOrNull
import com.tepmex.extensivereadingtutor.pedagogy.ContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.ContextResult
import com.tepmex.extensivereadingtutor.pedagogy.EVALUATE_SCHEMA_JSON
import com.tepmex.extensivereadingtutor.pedagogy.EvaluationContext
import com.tepmex.extensivereadingtutor.pedagogy.EvaluateContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.ExplainInputV1
import com.tepmex.extensivereadingtutor.pedagogy.HintOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.LessonPolicyConfig
import com.tepmex.extensivereadingtutor.pedagogy.MemoryPacket
import com.tepmex.extensivereadingtutor.pedagogy.ObservationOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.OutputLimits
import com.tepmex.extensivereadingtutor.pedagogy.PREPARE_SCHEMA_JSON
import com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy
import com.tepmex.extensivereadingtutor.pedagogy.PrepareContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.PrepareOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.PrepareSchemaVersion
import com.tepmex.extensivereadingtutor.pedagogy.PromptPack
import com.tepmex.extensivereadingtutor.pedagogy.QuestionOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.QuestionPolicy
import com.tepmex.extensivereadingtutor.pedagogy.QuestionScopeWire
import com.tepmex.extensivereadingtutor.pedagogy.RubricWire
import com.tepmex.extensivereadingtutor.pedagogy.SelectionWire
import com.tepmex.extensivereadingtutor.pedagogy.estimateRequest
import com.tepmex.extensivereadingtutor.pedagogy.fits
import com.tepmex.extensivereadingtutor.pedagogy.requestFingerprint
import com.tepmex.extensivereadingtutor.storage.SessionHead
import com.tepmex.extensivereadingtutor.storage.StoreConstraintException
import com.tepmex.extensivereadingtutor.storage.StoreSession
import com.tepmex.extensivereadingtutor.storage.TutorStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val lessonWire = Json { encodeDefaults = true; explicitNulls = true }

class LessonService(
    private val store: TutorStore,
    private val ai: LessonAi,
    private val learner: LearnerService,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val models: ModelSource,
    private val strategy: PedagogicalStrategy = PedagogicalStrategy.V2,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val policy: ObservationPolicy = ObservationPolicy(),
    private val prepareContext: PrepareContextBuilder = PrepareContextBuilder(),
    private val evaluateContext: EvaluateContextBuilder = EvaluateContextBuilder(),
    private val questionPolicy: QuestionPolicy = QuestionPolicy(),
    private val policyV2: ObservationPolicyV2 = ObservationPolicyV2(),
    private val lessonPolicy: LessonPolicyConfig = LessonPolicyConfig(),
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
        val existing = store.read { it.session(sessionId) }
            ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        return if (existing.strategyVersion == PedagogicalStrategy.V1.version) prepareLessonV1(sessionId) else prepareLessonV2(sessionId)
    }

    suspend fun selections(sessionId: SessionId): List<LearnerSelection> = store.read { it.selections(sessionId) }

    suspend fun markSelection(
        sessionId: SessionId,
        startCodePoint: Int,
        endCodePoint: Int,
        requestType: SelectionRequestType = SelectionRequestType.GENERAL,
        comment: String? = null,
        helpTiming: HelpTiming = HelpTiming.WITH_REVIEW,
    ): AppResult<LearnerSelection> = store.transaction { db ->
        saveMark(db, sessionId, startCodePoint, endCodePoint, requestType, comment, helpTiming, null)
    }

    suspend fun markUtf16(
        sessionId: SessionId,
        startUtf16: Int,
        endUtf16: Int,
        requestType: SelectionRequestType = SelectionRequestType.GENERAL,
        comment: String? = null,
        helpTiming: HelpTiming = HelpTiming.WITH_REVIEW,
    ): AppResult<LearnerSelection> = store.transaction { db ->
        val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        val fragment = db.fragment(session.fragmentId) ?: return@transaction AppResult.Err(DomainError.LessonNotReady("Фрагмент не найден."))
        val range = utf16ToCodePointRange(fragment.originalText, startUtf16, endUtf16)
            ?: return@transaction AppResult.Err(DomainError.SelectionInvalid("Выделение пустое или разрывает символ."))
        saveMark(db, sessionId, range.first, range.second, requestType, comment, helpTiming, null)
    }

    suspend fun updateSelection(
        sessionId: SessionId,
        selectionId: SelectionId,
        requestType: SelectionRequestType,
        comment: String?,
    ): AppResult<LearnerSelection> = store.transaction { db ->
        val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        val freeze = db.attemptFreeze(sessionId)
        if (freeze != null && freeze.selections.any { it.id == selectionId }) {
            return@transaction AppResult.Err(DomainError.SelectionInvalid("Ответы уже отправлены. Новая пометка не меняет отправленный разбор."))
        }
        val current = db.selections(sessionId).firstOrNull { it.id == selectionId && it.deletedAt == null }
            ?: return@transaction AppResult.Err(DomainError.SelectionInvalid("Пометка не найдена."))
        val normalized = normalizeRequest(requestType, comment)
        val changed = current.requestType != normalized.first || current.comment != normalized.second
        val next = current.copy(
            revision = if (changed) current.revision + 1 else current.revision,
            requestType = normalized.first,
            comment = normalized.second,
        )
        db.saveSelection(next)
        AppResult.Ok(next)
    }

    suspend fun deleteSelection(sessionId: SessionId, selectionId: SelectionId): AppResult<LessonSession> = store.transaction { db ->
        val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        val freeze = db.attemptFreeze(sessionId)
        if (freeze != null && freeze.selections.any { it.id == selectionId }) {
            return@transaction AppResult.Err(DomainError.SelectionInvalid("Отправленный набор выделений уже зафиксирован."))
        }
        val current = db.selections(sessionId).firstOrNull { it.id == selectionId && it.deletedAt == null }
            ?: return@transaction AppResult.Ok(session)
        db.saveSelection(current.copy(deletedAt = clock.now()))
        AppResult.Ok(db.session(sessionId)!!)
    }

    suspend fun explainSelection(sessionId: SessionId, selectionId: SelectionId): AppResult<ExplainRecord> {
        val gate = store.transaction { db -> explainGate(db, sessionId, selectionId) }
        if (gate is ExplainGate.Finished) return gate.result
        val work = (gate as ExplainGate.Run).work
        val drafted = ai.explainSelection(work.input)
        return store.transaction { db ->
            val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
            val selection = db.selections(sessionId).firstOrNull { it.id == selectionId }
            if (drafted is AppResult.Err) {
                db.saveExplainRecord(failedExplain(work, session, drafted.error.explain()))
                return@transaction drafted
            }
            val draft = (drafted as AppResult.Ok).value
            if (selection == null || selection.revision != draft.selectionRevision || selection.deletedAt != null) {
                db.saveExplainRecord(storedExplain(work, session, draft, counts = false))
                return@transaction AppResult.Err(DomainError.SelectionInvalid("Выделение изменилось. Старый ответ не относится к текущему запросу."))
            }
            val fragment = db.fragment(session.fragmentId)!!
            if (draft.status == SelectionReviewStatus.EXPLAINED && draft.sourceQuotes.any { !quoteAllowed(fragment, it) }) {
                db.saveExplainRecord(failedExplain(work, session, "Цитата объяснения не из фрагмента."))
                return@transaction AppResult.Err(DomainError.InvalidModelOutput("Цитата объяснения не из фрагмента."))
            }
            val record = storedExplain(work, session, draft, counts = false)
            db.saveExplainRecord(record)
            db.saveInputSnapshot(sessionId, "explain-${selection.id.value}-${selection.revision}", work.input.userPayload)
            AppResult.Ok(record)
        }
    }

    suspend fun revealExplanation(sessionId: SessionId, selectionId: SelectionId, revision: Int): AppResult<ExplainRecord> =
        store.transaction { db ->
            val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
            val record = db.explainRecords(sessionId).firstOrNull {
                it.selectionId == selectionId && it.selectionRevision == revision && it.status == CallStatus.SUCCEEDED && it.explanation != null
            } ?: return@transaction AppResult.Err(DomainError.LessonNotReady("Объяснение ещё не получено."))
            if (record.revealedAt != null) return@transaction AppResult.Ok(record)
            val freeze = db.attemptFreeze(sessionId)
            val counts = freeze == null
            val now = clock.now()
            val revealed = record.copy(revealedAt = now, countsTowardAttempt = counts)
            db.saveExplainRecord(revealed)
            if (counts) {
                db.insertAssistance(
                    com.tepmex.extensivereadingtutor.domain.AssistanceEvent(
                        id = ids.newId("assist"),
                        sessionId = sessionId,
                        level = AssistanceLevel.MEANING_GIVEN,
                        scopeQuestionIds = emptyList(),
                        scopeAll = false,
                        scopeUnknown = true,
                        selectionId = selectionId,
                        selectionRevision = revision,
                        text = record.explanation.orEmpty(),
                        shownAt = now,
                        source = "explain-selection",
                        countsTowardAttempt = true,
                    ),
                )
            }
            AppResult.Ok(db.explainRecords(sessionId).first { it.id == record.id })
        }

    private fun saveMark(
        db: StoreSession,
        sessionId: SessionId,
        startCodePoint: Int,
        endCodePoint: Int,
        requestType: SelectionRequestType,
        comment: String?,
        helpTiming: HelpTiming,
        existingId: SelectionId?,
    ): AppResult<LearnerSelection> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.status == SessionStatus.COMPLETED) {
            return AppResult.Err(DomainError.SelectionInvalid("Занятие уже завершено."))
        }
        val frozenAttempt = db.attemptFreeze(sessionId) != null
        if (frozenAttempt && helpTiming != HelpTiming.NOW) {
            return AppResult.Err(
                DomainError.SelectionInvalid(
                    "Ответы уже отправлены. Новая пометка не входит в этот разбор. Её можно объяснить отдельно: «Объяснить сейчас».",
                ),
            )
        }
        val fragment = db.fragment(session.fragmentId) ?: return AppResult.Err(DomainError.LessonNotReady("Фрагмент не найден."))
        val mapped = mapSelection(fragment, startCodePoint, endCodePoint, lessonPolicy.contextCodePoints)
            ?: return AppResult.Err(DomainError.SelectionInvalid("Выделение не попадает в текущий фрагмент."))
        val length = mapped.endCodePoint - mapped.startCodePoint
        if (length > lessonPolicy.maxSelectionCodePoints) {
            return AppResult.Err(
                DomainError.SelectionInvalid(
                    "Выделение $length знаков длиннее лимита ${lessonPolicy.maxSelectionCodePoints}. Сократите диапазон. Текст не обрезан.",
                ),
            )
        }
        val normalized = normalizeRequest(requestType, comment)
        val timing = if (session.submission != null || frozenAttempt) HelpTiming.NOW else helpTiming
        val now = clock.now()
        val selection = LearnerSelection(
            id = existingId ?: SelectionId(ids.newId("sel")),
            revision = 1,
            learnerId = session.learnerId,
            bookId = session.bookId,
            fragmentId = fragment.id,
            sessionId = sessionId,
            sourceAnchors = mapped.anchors,
            quotedText = mapped.quotedText,
            requestType = normalized.first,
            comment = normalized.second,
            helpTiming = timing,
            createdAt = now,
            requestedAtPhase = phaseOf(session),
            contextBefore = mapped.contextBefore,
            contextAfter = mapped.contextAfter,
        )
        db.saveSelection(selection)
        return AppResult.Ok(selection)
    }

    private suspend fun prepareLessonV2(sessionId: SessionId): AppResult<LessonSession> {
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
            val book = db.book(session.bookId)
            val prefs = db.preferences(session.learnerId) ?: defaultPreferences(clock.now())
            val language = studyLanguage(book?.language, prefs.studyLanguage)
            val blocks = db.blocks(session.bookId)
            val window = db.provider(model.providerConfigId)?.capabilities?.contextWindowTokens ?: lessonPolicy.defaultContextWindowTokens
            val focus = db.selections(sessionId).filter { it.deletedAt == null }.map { it.frozen() }
            val completed = db.allSessions().filter { it.bookId == session.bookId && it.status == SessionStatus.COMPLETED }.map { it.id }.toSet()
            val notes = if (lessonPolicy.continuityNotesEnabled) {
                db.continuityNotes(session.bookId).filter { it.sessionId in completed }.takeLast(lessonPolicy.maxContinuityNotes)
            } else {
                emptyList()
            }
            when (
                val built = prepareContext.build(
                    fragment = fragment,
                    language = language,
                    preceding = precedingText(blocks, fragment, lessonPolicy.precedingCodePoints).takeIf { it.isNotEmpty() },
                    preferences = prefs,
                    knowledge = db.knowledge(session.learnerId),
                    concepts = db.concepts(),
                    observations = db.observations(session.learnerId),
                    recentTypes = db.recentQuestionTypes(session.learnerId, 6, sessionId),
                    learnerFocus = focus,
                    continuity = notes,
                    contextWindowTokens = window,
                    now = clock.now(),
                )
            ) {
                is ContextResult.Overflow -> {
                    db.saveSessionHead(session.head(SessionStatus.CREATED, updatedAt = clock.now()))
                    PrepareGate.Finished(AppResult.Err(DomainError.BudgetExceeded(built.message)))
                }
                is ContextResult.Ready -> PrepareGate.Run(
                    PrepareWork(
                        fragment = fragment,
                        input = PrepareLessonInput(
                            providerConfigId = model.providerConfigId,
                            providerKind = model.providerKind,
                            model = model.model,
                            strategy = PedagogicalStrategy.V2,
                            preferences = prefs,
                            chapterTitle = fragment.chapterTitle,
                            precedingText = built.value.input.precedingText,
                            fragmentText = fragment.originalText,
                            memory = MemoryPacket(emptyList(), emptyList(), emptyList(), 0),
                            systemPrompt = built.value.system,
                            userPayload = built.value.userJson,
                            maxOutputTokens = lessonPolicy.prepareMaxTokens,
                            jsonSchema = PREPARE_SCHEMA_JSON,
                            schemaName = "prepare-v2",
                        ),
                        reportJson = budgetJson(built.value.report),
                    ),
                )
            }
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
            store.transaction { db -> commitPrepareV2(db, sessionId, work, (drafted as AppResult.Ok).value, started) }
        } catch (error: StoreConstraintException) {
            store.read { it.session(sessionId) }?.let { AppResult.Ok(it) }
                ?: AppResult.Err(DomainError.LessonNotReady(error.message ?: "Подготовка уже сохранена."))
        }
    }

    private fun commitPrepareV2(
        db: StoreSession,
        sessionId: SessionId,
        work: PrepareWork,
        draft: PreparedDraft,
        started: Instant,
    ): AppResult<LessonSession> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.prepared != null) return AppResult.Ok(session)
        val now = clock.now()
        val callId = AiCallId(ids.newId("call"))
        val book = db.book(session.bookId)
        val prefs = db.preferences(session.learnerId) ?: defaultPreferences(now)
        val language = studyLanguage(book?.language, prefs.studyLanguage)
        val output = draft.toPrepareOutput()
        val allowed = db.concepts().map { it.id.value }.toSet()
        val decision = questionPolicy.decide(output, work.fragment, prefs.hintDepth, language, allowed)
        val fingerprint = draft.requestFingerprint.ifBlank {
            requestFingerprint(draft.model, 0.4, work.input.maxOutputTokens, work.input.systemPrompt.orEmpty(), work.input.userPayload.orEmpty())
        }
        if (decision is com.tepmex.extensivereadingtutor.pedagogy.PrepareDecision.Reject) {
            db.insertCall(callRecord(callId, sessionId, AiOperation.PREPARE_LESSON, work.input.providerConfigId, draft.providerKind, draft.model, PedagogicalStrategy.V2.prepareTemplateVersion, PedagogicalStrategy.V2.version, CallStatus.REJECTED_OUTPUT, draft, fingerprint, started, now))
            db.saveProvenance(callId, work.reportJson)
            val aborted = session.status.require(SessionEvent.ABORT_PREPARE)
            if (aborted is AppResult.Ok) db.saveSessionHead(session.head(aborted.value, updatedAt = now))
            return AppResult.Err(DomainError.InvalidModelOutput(decision.reason))
        }
        val accepted = (decision as com.tepmex.extensivereadingtutor.pedagogy.PrepareDecision.Accept)
        val questions = accepted.questions.mapIndexed { index, question ->
            LessonQuestion(
                id = QuestionId(ids.newId("q")),
                sessionId = sessionId,
                type = question.type,
                prompt = question.prompt,
                targetSkill = question.targetSkill,
                targetExpression = question.targetExpression,
                sourceAnchors = question.anchors,
                order = index,
                purpose = question.purpose,
                conceptRef = question.conceptRef,
                rubric = question.rubric,
                localId = question.localId,
            )
        }
        val ready = session.status.require(SessionEvent.COMMIT_PREPARE)
        if (ready is AppResult.Err) return ready
        db.insertCall(callRecord(callId, sessionId, AiOperation.PREPARE_LESSON, work.input.providerConfigId, draft.providerKind, draft.model, PedagogicalStrategy.V2.prepareTemplateVersion, PedagogicalStrategy.V2.version, CallStatus.SUCCEEDED, draft, fingerprint, started, now))
        db.saveProvenance(callId, work.reportJson)
        db.saveInputSnapshot(sessionId, "prepare", work.input.userPayload.orEmpty())
        db.insertQuestions(questions)
        for (hint in accepted.hints) {
            val scoped = questions.filter { it.localId in hint.targetLocalIds }.map { it.id }
            db.insertAssistance(
                com.tepmex.extensivereadingtutor.domain.AssistanceEvent(
                    id = ids.newId("assist"),
                    sessionId = sessionId,
                    level = hint.assistanceLevel,
                    scopeQuestionIds = if (hint.scopeAll) emptyList() else scoped,
                    scopeAll = hint.scopeAll,
                    scopeUnknown = false,
                    selectionId = null,
                    selectionRevision = null,
                    text = hint.text,
                    shownAt = now,
                    source = "shown-hint",
                    countsTowardAttempt = true,
                ),
            )
        }
        db.savePrepared(sessionId, draft.introduction, accepted.hints.map { it.text }, PedagogicalStrategy.V2.version)
        db.saveSessionHead(session.head((ready as AppResult.Ok).value, prepareCallId = callId, updatedAt = now))
        return AppResult.Ok(db.session(sessionId)!!)
    }

    private suspend fun prepareLessonV1(sessionId: SessionId): AppResult<LessonSession> {
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
                        strategy = PedagogicalStrategy.V1,
                        preferences = prefs,
                        chapterTitle = fragment.chapterTitle,
                        precedingText = precedingText(blocks, fragment).takeIf { it.isNotEmpty() },
                        fragmentText = fragment.originalText,
                        memory = memoryV1(db, fragment.originalText, session.id),
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
            store.transaction { db -> commitPrepareV1(db, sessionId, work, (drafted as AppResult.Ok).value, started) }
        } catch (error: StoreConstraintException) {
            store.read { it.session(sessionId) }?.let { AppResult.Ok(it) }
                ?: AppResult.Err(DomainError.LessonNotReady(error.message ?: "Подготовка уже сохранена."))
        }
    }

    private fun commitPrepareV1(
        db: StoreSession,
        sessionId: SessionId,
        work: PrepareWork,
        draft: PreparedDraft,
        started: Instant,
    ): AppResult<LessonSession> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.prepared != null) return AppResult.Ok(session)
        val now = clock.now()
        val kept = draft.questions.mapNotNull { question ->
            val hit = locateInFragment(work.fragment, question.sourceQuote) ?: return@mapNotNull null
            question to hit
        }
        val callId = AiCallId(ids.newId("call"))
        val fp = fingerprint("prepare", draft.model, work.fragment.originalText)
        if (kept.isEmpty()) {
            db.insertCall(callRecord(callId, sessionId, AiOperation.PREPARE_LESSON, work.input.providerConfigId, draft.providerKind, draft.model, PedagogicalStrategy.V1.prepareTemplateVersion, PedagogicalStrategy.V1.version, CallStatus.REJECTED_OUTPUT, draft, fp, started, now))
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
        db.insertCall(callRecord(callId, sessionId, AiOperation.PREPARE_LESSON, work.input.providerConfigId, draft.providerKind, draft.model, PedagogicalStrategy.V1.prepareTemplateVersion, PedagogicalStrategy.V1.version, CallStatus.SUCCEEDED, draft, fp, started, now))
        db.insertQuestions(questions)
        db.savePrepared(sessionId, draft.introduction, draft.hints, PedagogicalStrategy.V1.version)
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
            val stored = submission.copy(sessionId = sessionId)
            if (session.strategyVersion != PedagogicalStrategy.V1.version) {
                when (val built = buildEvaluation(db, session, stored)) {
                    is ContextResult.Overflow -> return@transaction AppResult.Err(DomainError.BudgetExceeded(built.message + " Черновик сохранён."))
                    is ContextResult.Ready -> {
                        val now = clock.now()
                        val freeze = freezeOf(db, session, built.value, now)
                        db.insertSubmission(stored)
                        db.saveAttemptFreeze(freeze)
                        db.saveInputSnapshot(sessionId, "evaluate-user", built.value.userJson)
                        db.saveInputSnapshot(sessionId, "evaluate-system", built.value.system)
                    }
                }
            } else {
                db.insertSubmission(stored)
            }
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
        val existing = store.read { it.session(sessionId) }
            ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        return if (existing.strategyVersion == PedagogicalStrategy.V1.version) evaluateV1(sessionId) else evaluateV2(sessionId)
    }

    private suspend fun evaluateV2(sessionId: SessionId): AppResult<LessonSession> {
        val model = models.current()
        val gate = store.transaction { db ->
            val session = db.session(sessionId)
                ?: return@transaction EvalGate.Finished(AppResult.Err(DomainError.SessionNotFound(sessionId.value)))
            if (session.review != null) return@transaction EvalGate.Finished(AppResult.Ok(session))
            if (session.submission == null) return@transaction EvalGate.Finished(AppResult.Err(DomainError.LessonNotReady("Сначала отправьте ответы.")))
            if (model.model.isBlank()) return@transaction EvalGate.Finished(AppResult.Err(DomainError.ModelNotConfigured))
            val freeze = db.attemptFreeze(sessionId)
                ?: return@transaction EvalGate.Finished(AppResult.Err(DomainError.LessonNotReady("Снимок ответов не найден.")))
            val user = db.inputSnapshot(sessionId, "evaluate-user") ?: freeze.evaluateInputJson
            val system = db.inputSnapshot(sessionId, "evaluate-system") ?: PromptPack.system(PromptPack.EVALUATE, EVALUATE_SCHEMA_JSON)
            val window = db.provider(model.providerConfigId)?.capabilities?.contextWindowTokens ?: lessonPolicy.defaultContextWindowTokens
            val estimate = estimateRequest(system, user)
            if (!fits(estimate, window, lessonPolicy.evaluateMaxTokens)) {
                return@transaction EvalGate.Finished(
                    AppResult.Err(DomainError.BudgetExceeded("Разбор не помещается в окно модели. Сократите выделения или увеличьте лимит. Черновик и ответы сохранены. Неполный разбор не записан.")),
                )
            }
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
                        strategy = PedagogicalStrategy.V2,
                        preferences = prefs,
                        fragmentText = fragment.originalText,
                        questions = session.prepared?.questions.orEmpty(),
                        submission = session.submission,
                        memory = MemoryPacket(emptyList(), emptyList(), emptyList(), 0),
                        systemPrompt = system,
                        userPayload = user,
                        maxOutputTokens = lessonPolicy.evaluateMaxTokens,
                        jsonSchema = EVALUATE_SCHEMA_JSON,
                        schemaName = "evaluate-v2",
                    ),
                    freeze = freeze,
                ),
            )
        }
        if (gate is EvalGate.Finished) return gate.result
        val work = (gate as EvalGate.Run).work
        val started = clock.now()
        val drafted = ai.evaluateAnswers(work.input)
        if (drafted is AppResult.Err) return drafted
        return try {
            store.transaction { db -> commitReviewV2(db, sessionId, work, (drafted as AppResult.Ok).value, started) }
        } catch (error: StoreConstraintException) {
            val saved = store.read { it.session(sessionId) }
            if (saved?.review != null) AppResult.Ok(saved)
            else AppResult.Err(DomainError.LessonNotReady(error.message ?: "Разбор уже сохранён."))
        }
    }

    private fun commitReviewV2(
        db: StoreSession,
        sessionId: SessionId,
        work: EvalWork,
        draft: EvaluationDraft,
        started: Instant,
    ): AppResult<LessonSession> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.review != null) return AppResult.Ok(session)
        val now = clock.now()
        val callId = AiCallId(ids.newId("call"))
        val book = db.book(session.bookId)
        val prefs = work.input.preferences
        val language = studyLanguage(book?.language, prefs.studyLanguage)
        val submission = session.submission ?: return AppResult.Err(DomainError.LessonNotReady("Нет ответов."))
        val questions = session.prepared?.questions.orEmpty()
        val freeze = work.freeze ?: return AppResult.Err(DomainError.LessonNotReady("Снимок ответов не найден."))
        val coverage = coverageError(questions, freeze, draft.reviews, draft.selectionReviews)
        val fingerprint = draft.requestFingerprint.ifBlank {
            requestFingerprint(draft.model, 0.2, work.input.maxOutputTokens, work.input.systemPrompt.orEmpty(), work.input.userPayload.orEmpty())
        }
        if (coverage != null) {
            db.insertCall(usageCall(callId, sessionId, AiOperation.EVALUATE_ANSWERS, work.input.providerConfigId, draft.providerKind, draft.model, PedagogicalStrategy.V2.evaluateTemplateVersion, PedagogicalStrategy.V2.version, CallStatus.REJECTED_OUTPUT, draft.usage, draft.rawJson, fingerprint, started, now))
            return AppResult.Err(DomainError.InvalidModelOutput(coverage))
        }
        val reviews = draft.reviews.map {
            QuestionReview(
                questionId = QuestionId(it.questionId),
                verdict = it.verdict,
                explanation = it.explanation.trim(),
                errorNotes = it.errorNotes,
                evidenceSufficient = it.evidenceSufficient,
                examples = it.examples,
                demonstratedMeaning = it.demonstratedMeaning,
                nuance = it.nuance?.takeIf { value -> value.isNotBlank() },
                productionFeedback = it.productionFeedback?.takeIf { value -> value.isNotBlank() },
                rubricIssue = it.rubricIssue?.takeIf { value -> value.isNotBlank() },
            )
        }
        val selectionReviews = draft.selectionReviews.map {
            SelectionReview(
                selectionId = SelectionId(it.selectionId),
                selectionRevision = it.selectionRevision,
                status = it.status,
                explanation = it.explanation.trim(),
                contextualMeaning = it.contextualMeaning,
                nuance = it.nuance,
                example = it.example,
                sourceQuotes = it.sourceQuotes,
            )
        }
        val proposalIds = mutableListOf<ProposalId>()
        val perQuestion = mutableMapOf<String, Int>()
        var selfReports = 0
        for (item in draft.observations) {
            val output = item.toOutput()
            val decision = policyV2.decide(
                V2Proposal(output, item.payloadJson),
                work.fragment,
                submission,
                questions,
                reviews,
                freeze.assistance,
                language,
                sessionId,
                freeze.selections,
            )
            when (decision) {
                is V2Decision.Reject -> rememberProposal(db, sessionId, decision.contentHash, ProposalStatus.REJECTED, decision.reason, null, item.payloadJson, now, proposalIds)
                is V2Decision.Accept -> {
                    val limited = if (output.evidenceSource == com.tepmex.extensivereadingtutor.domain.EvidenceSource.ANSWER) {
                        val count = perQuestion.getOrDefault(output.questionId.orEmpty(), 0)
                        if (count >= lessonPolicy.maxObservationsPerQuestion) true else {
                            perQuestion[output.questionId.orEmpty()] = count + 1
                            false
                        }
                    } else {
                        if (selfReports >= lessonPolicy.maxSelfReportObservations) true else {
                            selfReports += 1
                            false
                        }
                    }
                    if (limited) {
                        rememberProposal(db, sessionId, decision.contentHash + "|limit", ProposalStatus.REJECTED, "observation limit", null, item.payloadJson, now, proposalIds)
                        continue
                    }
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
                            evaluationVersion = PedagogicalStrategy.V2.evaluateTemplateVersion,
                            createdAt = now,
                            sequence = db.allocateSequence(),
                            bookId = session.bookId,
                            diagnosticKey = decision.diagnosticKey,
                            evidenceSource = item.evidenceSource,
                            evidenceMode = item.evidenceMode,
                            assistance = decision.assistance,
                            eligibleForIndependentSuccess = decision.eligibleForIndependentSuccess,
                            schemaVersion = "evaluate-v2",
                            policyVersion = "policy-v2",
                            evidenceUnitId = decision.evidenceUnitId,
                            contextId = decision.contextId,
                            selectionId = decision.selectionId,
                            selectionRevision = decision.selectionRevision,
                            timeZone = ZoneId.systemDefault().id,
                            senseScope = decision.concept?.senseScope ?: com.tepmex.extensivereadingtutor.domain.SenseScope.CONTEXTUAL_ONLY,
                        ),
                    )
                    rememberProposal(db, sessionId, decision.contentHash, ProposalStatus.ACCEPTED, null, observationId, item.payloadJson, now, proposalIds)
                }
            }
        }
        if (lessonPolicy.continuityNotesEnabled) {
            val kept = draft.continuityNotes.mapNotNull { note ->
                if (locateQuote(work.fragment, note.sourceQuote).kind != com.tepmex.extensivereadingtutor.books.QuoteLocate.UNIQUE) null
                else ContinuityNote(sessionId, note.text, note.sourceQuote, now)
            }.take(lessonPolicy.maxContinuityInReview)
            val prior = db.continuityNotes(session.bookId).filter { it.sessionId != sessionId }
            db.saveContinuityNotes(session.bookId, prior + kept)
        }
        val review = LessonReview(
            sessionId = sessionId,
            results = reviews,
            observationProposalIds = proposalIds,
            nextRecommendations = draft.nextRecommendations.take(lessonPolicy.maxRecommendations),
            aiCallId = callId,
            createdAt = now,
            selectionReviews = selectionReviews,
            feedbackResponse = draft.feedbackResponse,
        )
        val ready = session.status.require(SessionEvent.COMMIT_REVIEW)
        if (ready is AppResult.Err) return ready
        db.insertCall(usageCall(callId, sessionId, AiOperation.EVALUATE_ANSWERS, work.input.providerConfigId, draft.providerKind, draft.model, PedagogicalStrategy.V2.evaluateTemplateVersion, PedagogicalStrategy.V2.version, CallStatus.SUCCEEDED, draft.usage, draft.rawJson, fingerprint, started, now))
        db.insertReview(review)
        learner.replaceProjection(db, session.learnerId)
        db.saveSessionHead(session.head((ready as AppResult.Ok).value, evaluateCallId = callId, updatedAt = now, observationsAppliedAt = now))
        return AppResult.Ok(db.session(sessionId)!!)
    }

    private suspend fun evaluateV1(sessionId: SessionId): AppResult<LessonSession> {
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
                        strategy = PedagogicalStrategy.V1,
                        preferences = prefs,
                        fragmentText = fragment.originalText,
                        questions = session.prepared?.questions.orEmpty(),
                        submission = session.submission,
                        memory = memoryV1(db, fragment.originalText, session.id),
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
            store.transaction { db -> commitReviewV1(db, sessionId, work, (drafted as AppResult.Ok).value, started) }
        } catch (error: StoreConstraintException) {
            val saved = store.read { it.session(sessionId) }
            if (saved?.review != null) AppResult.Ok(saved)
            else AppResult.Err(DomainError.LessonNotReady(error.message ?: "Разбор уже сохранён."))
        }
    }

    private fun commitReviewV1(
        db: StoreSession,
        sessionId: SessionId,
        work: EvalWork,
        draft: EvaluationDraft,
        started: Instant,
    ): AppResult<LessonSession> {
        val session = db.session(sessionId) ?: return AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.review != null) return AppResult.Ok(session)
        val now = clock.now()
        val callId = AiCallId(ids.newId("call"))
        val book = db.book(session.bookId)
        val language = book?.language ?: work.input.preferences.studyLanguage
        val submission = session.submission ?: return AppResult.Err(DomainError.LessonNotReady("Нет ответов."))
        val proposalIds = mutableListOf<ProposalId>()
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
                            evaluationVersion = PedagogicalStrategy.V1.evaluateTemplateVersion,
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
            usageCall(
                callId, sessionId, AiOperation.EVALUATE_ANSWERS, work.input.providerConfigId, draft.providerKind, draft.model,
                PedagogicalStrategy.V1.evaluateTemplateVersion, PedagogicalStrategy.V1.version, CallStatus.SUCCEEDED, draft.usage, draft.rawJson,
                fingerprint("evaluate", draft.model, work.fragment.originalText), started, now,
            ),
        )
        db.insertReview(review)
        learner.replaceProjection(db, session.learnerId)
        db.saveSessionHead(session.head((ready as AppResult.Ok).value, evaluateCallId = callId, updatedAt = now, observationsAppliedAt = now))
        return AppResult.Ok(db.session(sessionId)!!)
    }

    suspend fun completeLesson(sessionId: SessionId): AppResult<LessonSession> = store.transaction { db ->
        val session = db.session(sessionId) ?: return@transaction AppResult.Err(DomainError.SessionNotFound(sessionId.value))
        if (session.status == SessionStatus.COMPLETED) return@transaction AppResult.Ok(session)
        if (session.review == null || session.observationsAppliedAt == null) {
            return@transaction AppResult.Err(DomainError.LessonNotReady("Разбор ещё не сохранён."))
        }
        if (session.strategyVersion != PedagogicalStrategy.V1.version) {
            val freeze = db.attemptFreeze(sessionId)
            val covered = session.review.results.map { it.questionId }.toSet()
            val expected = session.prepared?.questions.orEmpty().map { it.id }.toSet()
            val selectionCovered = session.review.selectionReviews.map { it.selectionId to it.selectionRevision }.toSet()
            val selectionExpected = freeze?.selections.orEmpty().map { it.id to it.revision }.toSet()
            if (covered != expected || selectionCovered != selectionExpected || session.review.results.any { it.explanation.isBlank() }) {
                return@transaction AppResult.Err(DomainError.LessonNotReady("Разбор не покрывает ответы и выделения."))
            }
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

    suspend fun explainRecords(sessionId: SessionId): List<ExplainRecord> = store.read { it.explainRecords(sessionId) }

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

    private fun explainGate(db: StoreSession, sessionId: SessionId, selectionId: SelectionId): ExplainGate {
        val session = db.session(sessionId) ?: return ExplainGate.Finished(AppResult.Err(DomainError.SessionNotFound(sessionId.value)))
        if (session.status == SessionStatus.COMPLETED) {
            return ExplainGate.Finished(AppResult.Err(DomainError.LessonNotReady("Занятие уже завершено.")))
        }
        val selection = db.selections(sessionId).firstOrNull { it.id == selectionId && it.deletedAt == null }
            ?: return ExplainGate.Finished(AppResult.Err(DomainError.SelectionInvalid("Пометка не найдена.")))
        val model = models.current()
        if (model.model.isBlank()) return ExplainGate.Finished(AppResult.Err(DomainError.ModelNotConfigured))
        val prefs = db.preferences(session.learnerId) ?: defaultPreferences(clock.now())
        val fragment = db.fragment(session.fragmentId)!!
        val input = ExplainInputV1(
            selection = selection.toWire(),
            sourceContext = selection.contextBefore + selection.quotedText + selection.contextAfter,
            explanationLanguage = prefs.explanationLanguage,
            studyLanguage = studyLanguage(db.book(session.bookId)?.language, prefs.studyLanguage),
            requestedDepth = prefs.hintDepth,
            questionScopes = session.prepared?.questions.orEmpty().map {
                QuestionScopeWire(it.id.value, it.prompt, it.targetExpression, it.sourceAnchors.map { span -> span.quote })
            },
        )
        val user = lessonWire.encodeToString(input)
        val system = PromptPack.system(PromptPack.EXPLAIN, com.tepmex.extensivereadingtutor.pedagogy.EXPLAIN_SCHEMA_JSON)
        val key = sha256("${selection.id.value}|${selection.revision}|$user")
        val cached = db.explainRecords(sessionId).firstOrNull { it.idempotencyKey == key && it.status == CallStatus.SUCCEEDED }
        if (cached != null) return ExplainGate.Finished(AppResult.Ok(cached))
        return ExplainGate.Run(
            ExplainWork(
                key = key,
                selection = selection,
                input = ExplainSelectionInput(
                    providerConfigId = model.providerConfigId,
                    providerKind = model.providerKind,
                    model = model.model,
                    strategy = PedagogicalStrategy.V2,
                    userPayload = user,
                    systemPrompt = system,
                    selectionId = selection.id.value,
                    selectionRevision = selection.revision,
                ),
            ),
        )
    }

    private fun buildEvaluation(db: StoreSession, session: LessonSession, submission: StudentSubmission): ContextResult<EvaluationContext> {
        val fragment = db.fragment(session.fragmentId)!!
        val prefs = db.preferences(session.learnerId) ?: defaultPreferences(clock.now())
        val language = studyLanguage(db.book(session.bookId)?.language, prefs.studyLanguage)
        val blocks = db.blocks(session.bookId)
        val model = models.current()
        val window = db.provider(model.providerConfigId)?.capabilities?.contextWindowTokens ?: lessonPolicy.defaultContextWindowTokens
        val freezeSelections = db.selections(session.id).filter { it.deletedAt == null }.map { it.frozen() }
        val assistance = db.assistanceEvents(session.id).filter { it.countsTowardAttempt }.map { it.frozen() }
        val revealed = db.explainRecords(session.id).filter { it.revealedAt != null && it.countsTowardAttempt && it.explanation != null }
            .filter { record -> freezeSelections.any { it.id == record.selectionId && it.revision == record.selectionRevision } }
            .map {
                FrozenExplanation(
                    selectionId = it.selectionId,
                    selectionRevision = it.selectionRevision,
                    explanation = it.explanation.orEmpty(),
                    contextualMeaning = it.contextualMeaning,
                    nuance = it.nuance,
                    example = it.example,
                    sourceQuotes = it.sourceQuotes,
                    revealedAt = it.revealedAt!!,
                )
            }
        return evaluateContext.build(
            fragment = fragment,
            language = language,
            preceding = precedingText(blocks, fragment, lessonPolicy.precedingCodePoints).takeIf { it.isNotEmpty() },
            preferences = prefs,
            introduction = session.prepared?.introduction.orEmpty(),
            questions = session.prepared?.questions.orEmpty(),
            submission = submission,
            hints = session.prepared?.hintPlans.orEmpty().ifEmpty {
                session.prepared?.hints.orEmpty().mapIndexed { index, text ->
                    com.tepmex.extensivereadingtutor.domain.HintPlan("h${index + 1}", text, emptyList(), AssistanceLevel.SCAFFOLD)
                }
            },
            assistance = assistance,
            selections = freezeSelections,
            revealed = revealed,
            contextWindowTokens = window,
        )
    }

    private fun freezeOf(db: StoreSession, session: LessonSession, built: EvaluationContext, now: Instant): AttemptFreeze {
        val selections = db.selections(session.id).filter { it.deletedAt == null }.map { it.frozen() }
        val assistance = db.assistanceEvents(session.id).filter { it.countsTowardAttempt }.map { it.frozen() }
        val explanations = db.explainRecords(session.id).filter { it.revealedAt != null && it.countsTowardAttempt && it.explanation != null }
            .filter { record -> selections.any { it.id == record.selectionId && it.revision == record.selectionRevision } }
            .map {
                FrozenExplanation(it.selectionId, it.selectionRevision, it.explanation.orEmpty(), it.contextualMeaning, it.nuance, it.example, it.sourceQuotes, it.revealedAt!!)
            }
        return AttemptFreeze(session.id, now, selections, explanations, assistance, built.userJson)
    }

    private suspend fun abortPrepare(sessionId: SessionId) {
        store.transaction { db ->
            val session = db.session(sessionId) ?: return@transaction
            if (session.status == SessionStatus.PREPARING && session.prepared == null) {
                db.saveSessionHead(session.head(SessionStatus.CREATED))
            }
        }
    }

    private fun memoryV1(db: StoreSession, fragmentText: String, sessionId: SessionId): MemoryPacket =
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
        now: Instant,
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

    private fun failedExplain(work: ExplainWork, session: LessonSession, error: String) = ExplainRecord(
        id = ids.newId("explain"),
        sessionId = session.id,
        selectionId = work.selection.id,
        selectionRevision = work.selection.revision,
        idempotencyKey = work.key + "|failed|" + ids.newId("try"),
        status = CallStatus.FAILED,
        responseJson = null,
        explanation = null,
        contextualMeaning = null,
        nuance = null,
        example = null,
        sourceQuotes = emptyList(),
        reviewStatus = null,
        revealedAt = null,
        createdAt = clock.now(),
        providerKind = work.input.providerKind,
        model = work.input.model,
        promptVersion = PromptPack.EXPLAIN_VERSION,
        error = error,
        countsTowardAttempt = false,
    )

    private fun storedExplain(work: ExplainWork, session: LessonSession, draft: ExplainDraft, counts: Boolean) = ExplainRecord(
        id = work.key,
        sessionId = session.id,
        selectionId = SelectionId(draft.selectionId),
        selectionRevision = draft.selectionRevision,
        idempotencyKey = work.key,
        status = CallStatus.SUCCEEDED,
        responseJson = draft.rawJson,
        explanation = draft.explanation,
        contextualMeaning = draft.contextualMeaning,
        nuance = draft.nuance,
        example = draft.example,
        sourceQuotes = draft.sourceQuotes,
        reviewStatus = draft.status,
        revealedAt = null,
        createdAt = clock.now(),
        providerKind = draft.providerKind,
        model = draft.model,
        promptVersion = PromptPack.EXPLAIN_VERSION,
        error = null,
        countsTowardAttempt = counts,
    )
}

private data class PrepareWork(
    val fragment: BookFragment,
    val input: PrepareLessonInput,
    val reportJson: String = "",
)

private data class EvalWork(
    val fragment: BookFragment,
    val input: EvaluateLessonInput,
    val freeze: AttemptFreeze? = null,
)

private data class ExplainWork(
    val key: String,
    val selection: LearnerSelection,
    val input: ExplainSelectionInput,
)

private sealed interface PrepareGate {
    data class Finished(val result: AppResult<LessonSession>) : PrepareGate
    data class Run(val work: PrepareWork) : PrepareGate
}

private sealed interface EvalGate {
    data class Finished(val result: AppResult<LessonSession>) : EvalGate
    data class Run(val work: EvalWork) : EvalGate
}

private sealed interface ExplainGate {
    data class Finished(val result: AppResult<ExplainRecord>) : ExplainGate
    data class Run(val work: ExplainWork) : ExplainGate
}

private fun LessonSession.head(
    status: SessionStatus,
    prepareCallId: AiCallId? = this.prepareCallId,
    evaluateCallId: AiCallId? = this.evaluateCallId,
    updatedAt: Instant = this.updatedAt,
    completedAt: Instant? = this.completedAt,
    observationsAppliedAt: Instant? = this.observationsAppliedAt,
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

private fun studyLanguage(bookLanguage: String?, preference: String): String =
    primaryLanguage(bookLanguage) ?: primaryLanguage(preference) ?: preference

private fun phaseOf(session: LessonSession): RequestedPhase = when {
    session.submission != null -> RequestedPhase.AFTER_SUBMISSION
    session.prepared == null -> RequestedPhase.BEFORE_PREPARE
    else -> RequestedPhase.AFTER_PREPARE
}

private fun normalizeRequest(type: SelectionRequestType, comment: String?): Pair<SelectionRequestType, String?> {
    val text = comment?.trim()?.takeIf { it.isNotEmpty() }
    val resolved = if (type == SelectionRequestType.CUSTOM && text == null) SelectionRequestType.GENERAL else type
    return resolved to text
}

private fun LearnerSelection.frozen() = FrozenSelection(
    id, revision, sourceAnchors, quotedText, requestType, comment, contextBefore, contextAfter, helpTiming,
)

private fun com.tepmex.extensivereadingtutor.domain.AssistanceEvent.frozen() = FrozenAssistance(
    id, level, scopeQuestionIds, scopeAll, scopeUnknown, selectionId, text, shownAt,
)

private fun LearnerSelection.toWire() = SelectionWire(
    id.value,
    revision,
    quotedText,
    sourceAnchors.map {
        com.tepmex.extensivereadingtutor.pedagogy.AnchorWire(it.blockId?.value.orEmpty(), it.startCodePoint ?: 0, it.endCodePoint ?: 0, it.quote)
    },
    requestType,
    comment,
    contextBefore,
    contextAfter,
    helpTiming.name,
)

private fun PreparedDraft.toPrepareOutput(): PrepareOutputV2 {
    val questions = questions.mapIndexed { index, question ->
        val quotes = question.sourceQuotes.ifEmpty { listOfNotNull(question.sourceQuote.takeIf { it.isNotBlank() }) }
        val rubric = question.rubric
        QuestionOutputV2(
            localId = question.localId?.takeIf { it.isNotBlank() } ?: "q${index + 1}",
            type = question.type,
            prompt = question.prompt,
            targetSkill = question.targetSkill,
            targetExpression = question.targetExpression,
            conceptRef = question.conceptRef,
            sourceQuotes = quotes,
            purpose = question.purpose,
            rubric = RubricWire(
                expectedMeaning = rubric?.expectedMeaning.orEmpty(),
                requiredPoints = rubric?.requiredPoints.orEmpty(),
                acceptableAlternatives = rubric?.acceptableAlternatives.orEmpty(),
                unsupportedInferences = rubric?.unsupportedInferences.orEmpty(),
                evidenceQuotes = rubric?.evidenceQuotes ?: quotes,
            ),
        )
    }
    val hints = if (hintPlans.isNotEmpty()) {
        hintPlans.map { HintOutputV2(it.localId, it.text, it.targetQuestionLocalIds, it.assistanceLevel) }
    } else {
        hints.mapIndexed { index, text -> HintOutputV2("h${index + 1}", text, emptyList(), AssistanceLevel.SCAFFOLD) }
    }
    return PrepareOutputV2(PrepareSchemaVersion.PREPARE_V2, introduction, hints, questions)
}

private fun ObservationDraft.toOutput() = ObservationOutputV2(
    questionId, selectionId, selectionRevision, conceptRef, conceptLabel, diagnosticKey, skillType, observationType, outcome,
    confidence, evidenceSource, studentEvidence, sourceQuote, evidenceMode, assistanceAssessment, interpretation,
)

private fun coverageError(
    questions: List<LessonQuestion>,
    freeze: AttemptFreeze,
    reviews: List<com.tepmex.extensivereadingtutor.llm.ReviewDraft>,
    selectionReviews: List<SelectionReviewDraft>,
): String? {
    val expected = questions.map { it.id.value }
    val got = reviews.map { it.questionId }
    if (got.toSet() != expected.toSet() || got.size != expected.size) return "Разбор должен содержать ровно один отзыв на каждый вопрос."
    if (reviews.any { it.explanation.isBlank() }) return "У каждого ответа нужно непустое объяснение."
    val expectedSelections = freeze.selections.map { it.id.value to it.revision }
    val gotSelections = selectionReviews.map { it.selectionId to it.selectionRevision }
    if (gotSelections.toSet() != expectedSelections.toSet() || gotSelections.size != expectedSelections.size) {
        return "Разбор должен покрыть каждое зафиксированное выделение."
    }
    if (selectionReviews.any { it.explanation.isBlank() }) return "У каждого выделения нужно непустое объяснение."
    return null
}

private fun quoteAllowed(fragment: BookFragment, quote: String): Boolean {
    val needle = nfc(quote).trim()
    return needle.isNotEmpty() && nfc(fragment.originalText).contains(needle)
}

private fun budgetJson(report: com.tepmex.extensivereadingtutor.domain.BudgetReport): String =
    "{\"builderVersion\":\"${report.builderVersion}\",\"memoryBytes\":${report.memoryBytes},\"memoryBudgetBytes\":${report.memoryBudgetBytes},\"estimatedInputTokens\":${report.estimatedInputTokens},\"estimateUncertain\":${report.estimateUncertain},\"cutoff\":${if (report.cutoffReason == null) "null" else "\"" + report.cutoffReason.replace("\"", "") + "\""},\"selected\":${report.selectedIds.size},\"omitted\":${report.omittedIds.size}}"

private fun callRecord(
    id: AiCallId,
    sessionId: SessionId,
    operation: AiOperation,
    providerConfigId: String,
    providerKind: String,
    model: String,
    template: String,
    strategyVersion: String,
    status: CallStatus,
    draft: PreparedDraft,
    fingerprint: String,
    started: Instant,
    finished: Instant,
) = AiCallRecord(
    id = id,
    sessionId = sessionId,
    operation = operation,
    providerConfigId = providerConfigId,
    providerKind = providerKind,
    model = model,
    promptTemplateVersion = template,
    strategyVersion = strategyVersion,
    requestFingerprint = fingerprint,
    status = status,
    promptTokens = draft.usage?.promptTokens,
    completionTokens = draft.usage?.completionTokens,
    totalTokens = draft.usage?.totalTokens,
    rawJson = draft.rawJson,
    startedAt = started,
    finishedAt = finished,
)

private fun usageCall(
    id: AiCallId,
    sessionId: SessionId,
    operation: AiOperation,
    providerConfigId: String,
    providerKind: String,
    model: String,
    template: String,
    strategyVersion: String,
    status: CallStatus,
    usage: com.tepmex.extensivereadingtutor.domain.TokenUsage?,
    rawJson: String,
    fingerprint: String,
    started: Instant,
    finished: Instant,
) = AiCallRecord(
    id = id,
    sessionId = sessionId,
    operation = operation,
    providerConfigId = providerConfigId,
    providerKind = providerKind,
    model = model,
    promptTemplateVersion = template,
    strategyVersion = strategyVersion,
    requestFingerprint = fingerprint,
    status = status,
    promptTokens = usage?.promptTokens,
    completionTokens = usage?.completionTokens,
    totalTokens = usage?.totalTokens,
    rawJson = rawJson,
    startedAt = started,
    finishedAt = finished,
)
