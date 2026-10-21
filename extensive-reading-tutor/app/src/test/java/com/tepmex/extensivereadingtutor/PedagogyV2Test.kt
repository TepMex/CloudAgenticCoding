package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.books.codePointLength
import com.tepmex.extensivereadingtutor.books.mapSelection
import com.tepmex.extensivereadingtutor.books.sliceCodePoints
import com.tepmex.extensivereadingtutor.books.selectionBounds
import com.tepmex.extensivereadingtutor.books.utf16ToCodePointRange
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.ConceptKind
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.ContinuityNote
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.EvidenceMode
import com.tepmex.extensivereadingtutor.domain.EvidenceSource
import com.tepmex.extensivereadingtutor.domain.EvidenceStrength
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.FrozenAssistance
import com.tepmex.extensivereadingtutor.domain.HelpTiming
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.LlmCapabilities
import com.tepmex.extensivereadingtutor.domain.explain
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.ProviderConfig
import com.tepmex.extensivereadingtutor.domain.ProviderKind
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.QuestionPurpose
import com.tepmex.extensivereadingtutor.domain.QuestionReview
import com.tepmex.extensivereadingtutor.domain.QuestionRubric
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SelectionId
import com.tepmex.extensivereadingtutor.domain.SelectionRequestType
import com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus
import com.tepmex.extensivereadingtutor.domain.SenseScope
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.SourceSpan
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubmittedAnswer
import com.tepmex.extensivereadingtutor.domain.TextAnchor
import com.tepmex.extensivereadingtutor.domain.TokenUsage
import com.tepmex.extensivereadingtutor.learner.EvidenceUnitReducer
import com.tepmex.extensivereadingtutor.learner.KnowledgeProjectorV2
import com.tepmex.extensivereadingtutor.learner.ObservationPolicyV2
import com.tepmex.extensivereadingtutor.learner.UnitKind
import com.tepmex.extensivereadingtutor.learner.V2Proposal
import com.tepmex.extensivereadingtutor.learner.eligibleIndependent
import com.tepmex.extensivereadingtutor.learner.recordedAssistance
import com.tepmex.extensivereadingtutor.llm.EvaluateLessonInput
import com.tepmex.extensivereadingtutor.llm.EvaluationDraft
import com.tepmex.extensivereadingtutor.llm.ExplainDraft
import com.tepmex.extensivereadingtutor.llm.ExplainSelectionInput
import com.tepmex.extensivereadingtutor.llm.GatewayLessonAi
import com.tepmex.extensivereadingtutor.llm.LessonAi
import com.tepmex.extensivereadingtutor.llm.LlmProvider
import com.tepmex.extensivereadingtutor.llm.LlmRequest
import com.tepmex.extensivereadingtutor.llm.LlmResponse
import com.tepmex.extensivereadingtutor.llm.PrepareLessonInput
import com.tepmex.extensivereadingtutor.llm.PreparedDraft
import com.tepmex.extensivereadingtutor.llm.ProviderLookup
import com.tepmex.extensivereadingtutor.llm.RetryingCalls
import com.tepmex.extensivereadingtutor.llm.ReviewDraft
import com.tepmex.extensivereadingtutor.llm.SelectionReviewDraft
import com.tepmex.extensivereadingtutor.llm.parseEvaluationV2
import com.tepmex.extensivereadingtutor.pedagogy.Catalog
import com.tepmex.extensivereadingtutor.pedagogy.ContextResult
import com.tepmex.extensivereadingtutor.pedagogy.EVALUATE_SCHEMA_JSON
import com.tepmex.extensivereadingtutor.pedagogy.EvaluateContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.EvaluateInputV2
import com.tepmex.extensivereadingtutor.pedagogy.ExplainInputV1
import com.tepmex.extensivereadingtutor.pedagogy.ObservationOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.PREPARE_SCHEMA_JSON
import com.tepmex.extensivereadingtutor.pedagogy.PrepareContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.PromptPack
import com.tepmex.extensivereadingtutor.pedagogy.MemoryPacket
import com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PedagogyV2Test {
    private val policy = ObservationPolicyV2()
    private val projector = KnowledgeProjectorV2()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

    @Test
    fun threeObservationsOfOneAnswerAreOneUnit() {
        val rows = (1..3).map { index ->
            seen("s", "zh:idiom:手不释卷", "2026-01-02T00:00:0${index}Z", "ctx", index.toLong(), "o$index")
        }
        val units = EvidenceUnitReducer().reduce(rows, emptySet())
        assertEquals(1, units.size)
        assertEquals(UnitKind.INDEPENDENT_POSITIVE, units.single().kind)
        assertEquals(3, units.single().observationIds.size)
    }

    @Test
    fun stableNeedsSpreadContextsDaysAndSpan() {
        val sameDay = (1..3).map { index ->
            seen("s$index", "zh:idiom:甲", "2026-01-02T00:0$index:00Z", "same", index.toLong(), "d$index")
        }
        assertNotEquals(KnowledgeStatus.STABLE, projector.project(sameDay, emptySet()).single().status)
        val missingZone = (1..3).map { index ->
            seen("z$index", "zh:idiom:甲", "2026-01-0${index}T00:00:00Z", "c$index", index.toLong(), "z$index").copy(timeZone = null)
        }
        assertNotEquals(KnowledgeStatus.STABLE, projector.project(missingZone, emptySet()).single().status)
        val spread = listOf(
            seen("a", "zh:idiom:甲", "2026-01-01T00:00:00Z", "c1", 1, "a"),
            seen("b", "zh:idiom:甲", "2026-01-04T00:00:00Z", "c2", 2, "b"),
            seen("c", "zh:idiom:甲", "2026-01-09T00:00:00Z", "c3", 3, "c"),
        )
        val stable = projector.project(spread, emptySet()).single()
        assertEquals(KnowledgeStatus.STABLE, stable.status)
        assertEquals(3, stable.independentSuccessUnits)
        assertEquals(3, stable.distinctContexts)
        assertEquals(3, stable.distinctStudyDays)
        val retracted = projector.project(spread, setOf(ObservationId("c")))
        assertNotEquals(KnowledgeStatus.STABLE, retracted.single().status)
    }

    @Test
    fun contradictoryUnitDoesNotPickAConvenientOutcome() {
        val rows = listOf(
            seen("s", "zh:idiom:甲", "2026-01-02T00:00:00Z", "ctx", 1, "p"),
            seen("s", "zh:idiom:甲", "2026-01-02T00:00:01Z", "ctx", 2, "n").copy(
                outcome = Outcome.NEGATIVE,
                observationType = ObservationType.SEMANTIC_MISINTERPRETATION,
                eligibleForIndependentSuccess = false,
            ),
        )
        val unit = EvidenceUnitReducer().reduce(rows, emptySet()).single()
        assertEquals(UnitKind.AMBIGUOUS, unit.kind)
        assertTrue(projector.project(rows, emptySet()).isEmpty())
    }

    @Test
    fun meaningHintOnOneQuestionDoesNotCaptureTheOther() {
        val q1 = QuestionId("q1")
        val q2 = QuestionId("q2")
        val events = listOf(
            FrozenAssistance("h", AssistanceLevel.MEANING_GIVEN, listOf(q1), false, false, null, "значение", Instant.EPOCH),
        )
        val input = positiveOutput("q1")
        val review = QuestionReview(q1, AnswerVerdict.CORRECT, "Ответ передаёт смысл фрагмента.", "", true, emptyList(), "смысл")
        assertEquals(AssistanceLevel.MEANING_GIVEN, recordedAssistance(q1, events))
        assertEquals(AssistanceLevel.NONE_RECORDED, recordedAssistance(q2, events))
        assertFalse(eligibleIndependent(input, review, recordedAssistance(q1, events)))
        assertTrue(eligibleIndependent(input.copy(questionId = "q2"), review.copy(questionId = q2), recordedAssistance(q2, events)))
        val unknown = listOf(
            FrozenAssistance("n", AssistanceLevel.MEANING_GIVEN, emptyList(), false, true, SelectionId("sel"), "объяснение", Instant.EPOCH),
        )
        assertEquals(AssistanceLevel.UNKNOWN, recordedAssistance(q2, unknown))
        assertFalse(eligibleIndependent(input, review, recordedAssistance(q2, unknown)))
    }

    @Test
    fun emptyAndUnassessableReviewsCreateNoNegativeMastery() {
        val fragment = fragmentOf("他天天读书，手不释卷。")
        val question = question(fragment, "q1", "手不释卷")
        val blank = submission("q1", "")
        val empty = policy.decide(
            proposal(question.id.value, ObservationType.UNKNOWN_VOCABULARY, Outcome.NEGATIVE, "", "手不释卷"),
            fragment, blank, listOf(question),
            listOf(review(question.id, AnswerVerdict.UNANSWERED, false)),
            emptyList(), "zh", SessionId("s"), emptyList(),
        )
        assertTrue(rejectReason(empty).isNotBlank())
        val badQuestion = policy.decide(
            proposal(question.id.value, ObservationType.UNKNOWN_VOCABULARY, Outcome.NEGATIVE, "не знаю", "手不释卷"),
            fragment, submission("q1", "не знаю"), listOf(question),
            listOf(review(question.id, AnswerVerdict.NOT_ASSESSABLE, false)),
            emptyList(), "zh", SessionId("s"), emptyList(),
        )
        assertTrue(badQuestion.toString().contains("not assessable") || badQuestion.toString().contains("mastery"))
    }

    @Test
    fun foreignIdsAndCrossAnswerQuotesAreRejected() {
        val fragment = fragmentOf("同事接话说。")
        val first = question(fragment, "q1", "接话")
        val second = question(fragment, "q2", "接话").copy(id = QuestionId("q2"), order = 1)
        val answers = StudentSubmission(
            SessionId("s"),
            listOf(SubmittedAnswer(first.id, "подхватил"), SubmittedAnswer(second.id, "чужая цитата ответа")),
            null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"),
        )
        val reviews = listOf(review(first.id, AnswerVerdict.CORRECT, true), review(second.id, AnswerVerdict.CORRECT, true))
        assertTrue(rejectReason(policy.decide(proposal("missing", ObservationType.CORRECT_UNDERSTANDING, Outcome.POSITIVE, "подхватил", "接话"), fragment, answers, listOf(first), reviews, emptyList(), "zh", SessionId("s"), emptyList())).contains("question"))
        assertTrue(rejectReason(policy.decide(proposal("q1", ObservationType.CORRECT_UNDERSTANDING, Outcome.POSITIVE, "чужая цитата ответа", "接话"), fragment, answers, listOf(first, second), reviews, emptyList(), "zh", SessionId("s"), emptyList())).contains("stored field"))
        assertTrue(rejectReason(policy.decide(proposal("q1", ObservationType.CORRECT_UNDERSTANDING, Outcome.POSITIVE, "подхватил", "нет такой цитаты"), fragment, answers, listOf(first), reviews, emptyList(), "zh", SessionId("s"), emptyList())).contains("quote"))
        val foreign = proposal("q1", ObservationType.CORRECT_UNDERSTANDING, Outcome.POSITIVE, "подхватил", "接话").let {
            it.copy(input = it.input.copy(conceptRef = "en:lexical:book", conceptLabel = "book"))
        }
        assertTrue(rejectReason(policy.decide(foreign, fragment, answers, listOf(first), reviews, emptyList(), "zh", SessionId("s"), emptyList())).contains("foreign"))
    }

    @Test
    fun russianRetellingDoesNotUpdateActiveProduction() {
        val fragment = fragmentOf("他天天读书。")
        val question = question(fragment, "q1", "读书", SkillType.READING_COMPREHENSION).copy(type = QuestionType.MAIN_IDEA)
        val decision = policy.decide(
            proposal("q1", ObservationType.SUCCESSFUL_ACTIVE_USAGE, Outcome.POSITIVE, "он читает", "读书").let {
                it.copy(input = it.input.copy(skillType = SkillType.ACTIVE_PRODUCTION))
            },
            fragment, submission("q1", "он читает"), listOf(question),
            listOf(review(question.id, AnswerVerdict.CORRECT, true)),
            emptyList(), "zh", SessionId("s"), emptyList(),
        )
        assertTrue(rejectReason(decision).contains("skill"))
    }

    @Test
    fun exposureAndSelfReportDoNotRaiseOrStripStable() {
        val stable = listOf(
            seen("a", "zh:idiom:甲", "2026-01-01T00:00:00Z", "c1", 1, "a"),
            seen("b", "zh:idiom:甲", "2026-01-04T00:00:00Z", "c2", 2, "b"),
            seen("c", "zh:idiom:甲", "2026-01-09T00:00:00Z", "c3", 3, "c"),
        )
        val report = seen("d", "zh:idiom:甲", "2026-01-10T00:00:00Z", "c4", 4, "d").copy(
            evidenceMode = EvidenceMode.SELF_REPORTED,
            outcome = Outcome.NEGATIVE,
            observationType = ObservationType.UNKNOWN_VOCABULARY,
            eligibleForIndependentSuccess = false,
        )
        assertEquals(KnowledgeStatus.STABLE, projector.project(stable + report, emptySet()).single().status)
        val exposure = seen("e", "zh:lexical:乙", "2026-01-02T00:00:00Z", "x", 1, "e").copy(
            outcome = Outcome.WEAK,
            observationType = ObservationType.HINT_DEPENDENT_SUCCESS,
            eligibleForIndependentSuccess = false,
            evidenceMode = EvidenceMode.DEMONSTRATED,
        )
        val projected = projector.project(listOf(exposure), emptySet())
        assertTrue(projected.isEmpty() || projected.single().independentSuccessUnits == 0)
    }

    @Test
    fun memoryStaysInsideBytesAndSeparatesLanguages() {
        val label = "手不释卷"
        val fragment = fragmentOf("他天天读书，$label。")
        val zh = knowledge("zh", "zh:idiom:$label", label, KnowledgeStatus.NEEDS_REVIEW)
        val ru = knowledge("ru", "ru:lexical:книга", "книга", KnowledgeStatus.NEEDS_REVIEW)
        val otherBook = seen("book-a", "zh:idiom:$label", "2026-01-02T00:00:00Z", "ctx", 1, "cross").copy(bookId = BookId("other"))
        val builder = PrepareContextBuilder()
        val prefs = com.tepmex.extensivereadingtutor.domain.defaultPreferences(Instant.parse("2026-01-01T00:00:00Z"))
        val first = builder.build(fragment, "zh", null, prefs, listOf(zh, ru), listOf(concept("zh:idiom:$label", "zh", label), concept("ru:lexical:книга", "ru", "книга")), listOf(otherBook), emptyList(), emptyList(), emptyList(), 16384, Instant.parse("2026-01-03T00:00:00Z"))
        val ready = first as ContextResult.Ready
        assertTrue(ready.value.userJson.contains("zh:idiom:$label"))
        assertFalse(ready.value.userJson.contains("ru:lexical:книга"))
        assertTrue(ready.value.report.memoryBytes <= 8 * 1024)
        val again = builder.build(fragment, "zh", null, prefs, listOf(zh, ru), listOf(concept("zh:idiom:$label", "zh", label), concept("ru:lexical:книга", "ru", "книга")), listOf(otherBook), emptyList(), emptyList(), emptyList(), 16384, Instant.parse("2026-01-03T00:00:00Z")) as ContextResult.Ready
        assertEquals(ready.value.userJson, again.value.userJson)
        val crowded = (1..40).map { index ->
            val word = "词" + "长".repeat(40) + index
            knowledge("zh", "zh:lexical:$word", word, KnowledgeStatus.LEARNING)
        }
        val longText = crowded.joinToString("") { it.conceptId!!.value.substringAfterLast(':') }
        val packed = builder.build(fragmentOf(longText), "zh", "x".repeat(800), prefs, crowded, crowded.map { concept(it.conceptId!!.value, "zh", it.conceptId!!.value.substringAfterLast(':')) }, emptyList(), emptyList(), emptyList(), emptyList(), 32768, Instant.EPOCH) as ContextResult.Ready
        assertTrue(packed.value.report.memoryBytes <= 8 * 1024)
        assertTrue(packed.value.userJson.toByteArray().isNotEmpty())
    }

    @Test
    fun evaluatePayloadCarriesTheTaskAndNotMasteryLabels() = runBlocking {
        val ai = RecordingAi()
        val harness = Harness(ai = ai)
        val session = open(harness, listOf("他天天读书，手不释卷。"))
        val marked = harness.lessons.markSelection(session, 4, 8, SelectionRequestType.GENERAL, null, HelpTiming.WITH_REVIEW).unwrap()
        assertEquals(0, ai.explainCalls)
        harness.lessons.prepareLesson(session).unwrap()
        assertEquals(0, ai.explainCalls)
        assertTrue(ai.prepareInputs.single().userPayload!!.contains(marked.quotedText))
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(session).unwrap()
        harness.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "он постоянно читает")), null, true, "Не знаю 释", null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        harness.lessons.evaluateAnswers(session).unwrap()
        val payload = ai.evaluateInputs.single().userPayload.orEmpty()
        assertTrue(payload.contains("Прочитайте фрагмент"))
        assertTrue(payload.contains("IDIOM_COMPREHENSION"))
        assertTrue(payload.contains("wantsMoreExplanation"))
        assertTrue(payload.contains("true"))
        assertTrue(payload.contains("Не знаю 释"))
        assertTrue(payload.contains(marked.id.value))
        assertFalse(payload.contains("NEEDS_REVIEW"))
        assertFalse(payload.contains("STABLE"))
        assertFalse(payload.contains("weak learner"))
        val freeze = harness.store.read { it.attemptFreeze(session) }!!
        assertEquals(marked.id, freeze.selections.single().id)
        assertTrue(harness.store.read { it.assistanceEvents(session) }.any { it.countsTowardAttempt })
    }

    @Test
    fun withReviewAddsNoCallAndNowIsIdempotent() = runBlocking {
        val ai = RecordingAi()
        val harness = Harness(ai = ai)
        val session = open(harness, listOf("同事接话说：“那些都是他读过的书。”"))
        val cursorBefore = harness.lessons.getReadingProgress(harness.store.read { it.session(session) }!!.bookId)!!.cursor
        val mark = harness.lessons.markSelection(session, 0, 2, SelectionRequestType.MEANING, "он перебил?", HelpTiming.WITH_REVIEW).unwrap()
        assertEquals(0, ai.explainCalls)
        harness.lessons.prepareLesson(session).unwrap()
        assertEquals(SessionStatus.INTRO_READY, harness.lessons.getActiveLesson(harness.store.read { it.books().single().id })!!.status)
        val explained = harness.lessons.explainSelection(session, mark.id).unwrap()
        assertEquals(1, ai.explainCalls)
        assertNull(explained.revealedAt)
        assertTrue(harness.store.read { it.assistanceEvents(session) }.none { it.source == "explain-selection" })
        val again = harness.lessons.explainSelection(session, mark.id).unwrap()
        assertEquals(1, ai.explainCalls)
        assertEquals(explained.id, again.id)
        ai.failExplain = true
        harness.lessons.updateSelection(session, mark.id, SelectionRequestType.MEANING, "он перебил собеседника?").unwrap()
        val failed = harness.lessons.explainSelection(session, mark.id)
        assertTrue(failed is AppResult.Err)
        assertEquals(2, ai.explainCalls)
        assertTrue(harness.store.read { it.assistanceEvents(session) }.none { it.source == "explain-selection" })
        assertEquals(cursorBefore, harness.lessons.getReadingProgress(harness.store.read { it.books().single().id })!!.cursor)
        ai.failExplain = false
        val revised = harness.lessons.explainSelection(session, mark.id).unwrap()
        assertEquals(3, ai.explainCalls)
        assertNotEquals(explained.selectionRevision, revised.selectionRevision)
        val revealed = harness.lessons.revealExplanation(session, mark.id, revised.selectionRevision).unwrap()
        assertTrue(revealed.countsTowardAttempt)
        harness.lessons.deleteSelection(session, mark.id)
        assertTrue(harness.store.read { it.assistanceEvents(session) }.any { it.source == "explain-selection" && it.countsTowardAttempt })
    }

    @Test
    fun selectionSnapshotSurvivesRestartAndDoesNotInventAnswerEvidence() = runBlocking {
        val ai = RecordingAi()
        val harness = Harness(ai = ai)
        val book = import(harness, listOf("甲。乙。丙。"))
        val session = harness.lessons.startOrResumeLesson(book).unwrap().id
        val kept = harness.lessons.markSelection(session, 0, 1, SelectionRequestType.GENERAL, null, HelpTiming.WITH_REVIEW).unwrap()
        val dropped = harness.lessons.markSelection(session, 3, 4, SelectionRequestType.GENERAL, null, HelpTiming.WITH_REVIEW).unwrap()
        harness.lessons.deleteSelection(session, dropped.id).unwrap()
        val reopened = Harness(harness.store, ai, harness.clock, harness.models)
        assertEquals(kept.quotedText, reopened.lessons.selections(session).single { it.deletedAt == null }.quotedText)
        reopened.lessons.prepareLesson(session).unwrap()
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        reopened.lessons.revealQuestions(session).unwrap()
        reopened.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "вижу перечисление")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        val freeze = harness.store.read { it.attemptFreeze(session) }!!
        assertEquals(listOf(kept.id), freeze.selections.map { it.id })
        assertNull(freeze.selections.single().comment)
        val later = reopened.lessons.markSelection(session, 4, 5, SelectionRequestType.GENERAL, null, HelpTiming.WITH_REVIEW)
        assertTrue(later is AppResult.Err)
        val now = reopened.lessons.markSelection(session, 2, 3, SelectionRequestType.GENERAL, null, HelpTiming.NOW).unwrap()
        assertFalse(harness.store.read { it.attemptFreeze(session) }!!.selections.any { it.id == now.id })
        reopened.lessons.evaluateAnswers(session).unwrap()
        val observations = harness.store.read { it.observations(LOCAL_LEARNER) }
        assertTrue(observations.none { it.studentEvidence == kept.quotedText && it.evidenceSource == EvidenceSource.SELECTION_COMMENT })
        assertEquals(1, harness.store.read { it.session(session) }!!.review!!.selectionReviews.size)
    }

    @Test
    fun missingSelectionReviewBlocksCommitAndEmptySetAllowsNone() = runBlocking {
        val ai = RecordingAi()
        val harness = Harness(ai = ai)
        val session = open(harness, listOf("他天天读书。"))
        harness.lessons.prepareLesson(session).unwrap()
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(session).unwrap()
        harness.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "он читает")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        ai.omitSelectionReviews = false
        harness.lessons.evaluateAnswers(session).unwrap()
        assertEquals(SessionStatus.REVIEW_READY, harness.store.read { it.session(session) }!!.status)
        val secondBook = import(harness, listOf("办公室里有很多书。"))
        val other = harness.lessons.startOrResumeLesson(secondBook).unwrap().id
        harness.lessons.markSelection(other, 0, 2, SelectionRequestType.GENERAL, null, HelpTiming.WITH_REVIEW).unwrap()
        harness.lessons.prepareLesson(other).unwrap()
        val q2 = harness.store.read { it.session(other) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(other).unwrap()
        harness.lessons.submitAnswers(other, StudentSubmission(other, listOf(SubmittedAnswer(q2.id, "много книг")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        ai.omitSelectionReviews = true
        val blocked = harness.lessons.evaluateAnswers(other)
        assertTrue(blocked is AppResult.Err)
        assertNotEquals(SessionStatus.REVIEW_READY, harness.store.read { it.session(other) }!!.status)
        assertTrue(harness.store.read { it.observations(LOCAL_LEARNER) }.none { it.sessionId == other })
    }

    @Test
    fun shownHelpBeforeACorrectAnswerIsNotIndependent() = runBlocking {
        val ai = RecordingAi(hints = false)
        val harness = Harness(ai = ai)
        val session = open(harness, listOf("他天天读书，手不释卷。"))
        val mark = harness.lessons.markSelection(session, 6, 10, SelectionRequestType.MEANING, null, HelpTiming.NOW).unwrap()
        harness.lessons.prepareLesson(session).unwrap()
        val explained = harness.lessons.explainSelection(session, mark.id).unwrap()
        harness.lessons.revealExplanation(session, mark.id, explained.selectionRevision).unwrap()
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(session).unwrap()
        harness.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "он постоянно читает")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        val reviewed = harness.lessons.evaluateAnswers(session).unwrap()
        assertEquals(AnswerVerdict.CORRECT, reviewed.review!!.results.single().verdict)
        assertTrue(reviewed.review!!.results.single().explanation.isNotBlank())
        val state = harness.learner.knowledge(LOCAL_LEARNER).single()
        assertEquals(0, state.independentSuccessUnits)
        assertTrue(state.assistedSuccessUnits + state.errorUnits >= 1)
        val late = harness.lessons.explainSelection(session, mark.id).unwrap()
        harness.lessons.revealExplanation(session, mark.id, late.selectionRevision).unwrap()
        val freeze = harness.store.read { it.attemptFreeze(session) }!!
        assertEquals(1, freeze.explanations.size)
    }

    @Test
    fun frozenInputSurvivesRetryAndModelChange() = runBlocking {
        val ai = RecordingAi()
        val harness = Harness(ai = ai)
        val session = open(harness, listOf("他说这是原则。"))
        harness.lessons.prepareLesson(session).unwrap()
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(session).unwrap()
        harness.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "это принцип")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        val frozen = harness.store.read { it.inputSnapshot(session, "evaluate-user") }
        ai.failEvaluate = true
        assertTrue(harness.lessons.evaluateAnswers(session) is AppResult.Err)
        assertEquals(frozen, harness.store.read { it.inputSnapshot(session, "evaluate-user") })
        harness.models.snapshot = harness.models.snapshot.copy(model = "model-b")
        harness.lessons.evaluateAnswers(session).unwrap()
        val calls = harness.store.read { it.callsForSession(session) }
        assertTrue(calls.any { it.model == "model-a" && it.operation.name == "PREPARE_LESSON" })
        assertEquals("model-b", calls.last { it.operation.name == "EVALUATE_ANSWERS" }.model)
        assertEquals(frozen, harness.store.read { it.inputSnapshot(session, "evaluate-user") })
    }

    @Test
    fun concurrentEvaluateDoesNotDoubleEvidence() = runBlocking {
        val ai = RecordingAi()
        val harness = Harness(ai = ai)
        val session = open(harness, listOf("第二天他又坐到书桌前。"))
        harness.lessons.prepareLesson(session).unwrap()
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(session).unwrap()
        harness.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "он снова сел")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        coroutineScope {
            val left = async { harness.lessons.evaluateAnswers(session) }
            val right = async { harness.lessons.evaluateAnswers(session) }
            assertTrue(left.await() is AppResult.Ok)
            assertTrue(right.await() is AppResult.Ok)
        }
        assertEquals(1, harness.store.read { it.observations(LOCAL_LEARNER) }.count { it.outcome == Outcome.POSITIVE })
    }

    @Test
    fun supplementarySelectionRoundTripsAndAmbiguousQuoteIsNotGuessed() {
        val han = "甲\uD840\uDC00乙"
        val range = utf16ToCodePointRange(han, 1, 2)
        assertEquals(1 to 2, range)
        val across = fragmentOf("甲\uD840\uDC00", "乙丙乙")
        val mapped = mapSelection(across, 1, 4)!!
        assertEquals("\uD840\uDC00\n乙", mapped.quotedText)
        val bounds = selectionBounds(across, mapped.anchors)
        assertEquals(1 to 4, bounds)
        assertEquals(mapped.quotedText, across.originalText.sliceCodePoints(bounds!!.first, bounds.second))
        val repeated = fragmentOf("乙丙乙")
        val located = com.tepmex.extensivereadingtutor.books.locateQuote(repeated, "乙")
        assertEquals(com.tepmex.extensivereadingtutor.books.QuoteLocate.AMBIGUOUS, located.kind)
        assertNull(located.span)
    }

    @Test
    fun outputLimitsRejectPartialReviews() {
        assertTrue(parseEvaluationV2("{", setOf("q1")) is AppResult.Err)
        val duplicate = """{"schemaVersion":"evaluate-v2","questionReviews":[
            {"questionId":"q1","verdict":"CORRECT","evidenceSufficient":true,"demonstratedMeaning":"a","explanation":"Потому что текст это говорит.","nuance":null,"productionFeedback":null,"errorNotes":"","examples":[],"rubricIssue":null},
            {"questionId":"q1","verdict":"CORRECT","evidenceSufficient":true,"demonstratedMeaning":"a","explanation":"Потому что текст это говорит.","nuance":null,"productionFeedback":null,"errorNotes":"","examples":[],"rubricIssue":null}
        ],"selectionReviews":[],"feedbackResponse":null,"observations":[],"nextRecommendations":[],"continuityNotes":[]}"""
        assertTrue(parseEvaluationV2(duplicate, setOf("q1")) is AppResult.Err)
        val blank = duplicate.replace("Потому что текст это говорит.", "")
        assertTrue(parseEvaluationV2(blank, setOf("q1")) is AppResult.Err)
    }

    @Test
    fun truncatedGenerationIsNotParsed() = runBlocking {
        val gateway = GatewayLessonAi(
            ProviderLookup {
                AppResult.Ok(object : LlmProvider {
                    override val capabilities = LlmCapabilities()
                    override suspend fun generate(request: LlmRequest) = AppResult.Ok(LlmResponse("{", null, "length"))
                })
            },
            RetryingCalls(sleeper = { }),
        )
        val result = gateway.evaluateAnswers(
            EvaluateLessonInput(
                "default", "OPENAI_COMPATIBLE", "model-a", PedagogicalStrategy.V2,
                com.tepmex.extensivereadingtutor.domain.defaultPreferences(Instant.EPOCH),
                "甲", emptyList(),
                StudentSubmission(SessionId("s"), emptyList(), null, false, null, null, 0, Instant.EPOCH),
                MemoryPacket(emptyList(), emptyList(), emptyList(), 0),
                userPayload = "{}",
            ),
        )
        assertTrue((result as AppResult.Err).error.explain().contains("обрезан"))
    }

    @Test
    fun retractionAndEraseDropDerivedMemory() = runBlocking {
        val harness = Harness()
        val book = import(harness, listOf("雷打不动。他每天五点起床读书。", "朋友问他为什么如此坚持。"))
        val session = harness.lessons.startOrResumeLesson(book).unwrap().id
        harness.lessons.prepareLesson(session).unwrap()
        val question = harness.store.read { it.session(session) }!!.prepared!!.questions.single()
        harness.lessons.revealQuestions(session).unwrap()
        harness.lessons.submitAnswers(session, StudentSubmission(session, listOf(SubmittedAnswer(question.id, "это бесстрашие")), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))).unwrap()
        harness.lessons.evaluateAnswers(session).unwrap()
        harness.store.transaction {
            it.saveContinuityNotes(book, listOf(ContinuityNote(session, "заметка", "雷打不动", Instant.parse("2026-01-01T00:00:00Z"))))
        }
        val observation = harness.store.read { it.observations(LOCAL_LEARNER) }.single { it.outcome == Outcome.NEGATIVE }
        harness.learner.retract(observation.id, "Ошибочный вывод.")
        assertTrue(harness.learner.knowledge(LOCAL_LEARNER).isEmpty())
        assertEquals(1, harness.store.read { it.observations(LOCAL_LEARNER) }.size)
        assertTrue(harness.store.read { it.continuityNotes(book) }.isEmpty())
        harness.books.eraseLearningHistory(book)
        assertTrue(harness.store.read { it.observations(LOCAL_LEARNER) }.isEmpty())
        assertTrue(harness.store.read { it.continuityNotes(book) }.isEmpty())
    }

    @Test
    fun budgetOverflowKeepsEverySelection() = runBlocking {
        val harness = Harness()
        val session = open(harness, listOf("他天天读书，手不释卷。"))
        val mark = harness.lessons.markSelection(session, 0, 2, SelectionRequestType.GENERAL, null, HelpTiming.WITH_REVIEW).unwrap()
        harness.store.transaction {
            it.upsertProvider(ProviderConfig("default", "local", ProviderKind.OPENAI_COMPATIBLE, "http://local", LlmCapabilities(contextWindowTokens = 64)))
        }
        val prepared = harness.lessons.prepareLesson(session)
        assertTrue(prepared is AppResult.Err)
        assertTrue((prepared as AppResult.Err).error is DomainError.BudgetExceeded)
        assertEquals(mark.quotedText, harness.lessons.selections(session).single().quotedText)
        assertEquals(SessionStatus.CREATED, harness.store.read { it.session(session) }!!.status)
    }

    @Test
    fun promptsCarryPolicyAndSchemaOutsideTheUserText() {
        val system = PromptPack.system(PromptPack.EVALUATE, EVALUATE_SCHEMA_JSON)
        assertTrue(system.contains("You are the reading tutor inside an application."))
        assertTrue(system.contains("evaluate-v2"))
        assertFalse(system.contains("雷打不动"))
        assertTrue(PREPARE_SCHEMA_JSON.contains("prepare-v2"))
        assertTrue(Catalog.diagnostics.contains("reading.unsupported_inference"))
    }

    @Test
    fun goldenFixturesNameTheRequiredCases() {
        val text = javaClass.classLoader.getResource("pedagogy/golden-cases.json")!!.readText()
        val required = listOf(
            "ge-guan-yi-tan", "hand-never-trembles", "seems-he-reads", "jie-hua", "typo-cause",
            "meaning-shown", "extra-guess", "blank-answer", "unclear-notes", "bad-rubric",
            "ignore-instructions", "missing-referent", "contradictory-unit", "short-enough",
            "all-correct-no-new-concept", "new-sense", "wants-more", "every-correct-explained",
            "jie-hua-selection", "grammar-selection", "several-sentences", "unresolvable-reference",
            "now-before-correct", "selection-outside-questions", "no-invented-nuance",
        )
        required.forEach { id -> assertTrue(text.contains("\"$id\"")) }
        assertTrue(text.split("\"id\"").size - 1 >= 12)
        assertEquals("semantic evaluation не запускалась", SemanticEvalRunner.status())
    }

    @Test
    fun helpRequestAndInterpretationAreDifferentEvidence() {
        val fragment = fragmentOf("同事接话说。")
        val question = question(fragment, "q1", "接话")
        val selection = com.tepmex.extensivereadingtutor.domain.FrozenSelection(
            SelectionId("sel"), 1, question.sourceAnchors, "接话", SelectionRequestType.CUSTOM, "не понимаю", "", "", HelpTiming.WITH_REVIEW,
        )
        val guess = selection.copy(comment = "он перебил?")
        val help = policy.decide(
            commentProposal("не понимаю", EvidenceMode.DEMONSTRATED),
            fragment, submission("q1", "подхватил"), listOf(question), listOf(review(question.id, AnswerVerdict.CORRECT, true)),
            emptyList(), "zh", SessionId("s"), listOf(selection),
        )
        assertTrue(rejectReason(help).contains("self-report"))
        val bookQuote = policy.decide(
            commentProposal("接话", EvidenceMode.SELF_REPORTED),
            fragment, submission("q1", "подхватил"), listOf(question), listOf(review(question.id, AnswerVerdict.CORRECT, true)),
            emptyList(), "zh", SessionId("s"), listOf(selection.copy(comment = "接话")),
        )
        assertTrue(rejectReason(bookQuote).contains("book quote"))
        val interpreted = policy.decide(
            commentProposal("он перебил?", EvidenceMode.SELF_REPORTED).let {
                it.copy(input = it.input.copy(outcome = Outcome.WEAK, observationType = ObservationType.PARTIAL_UNDERSTANDING))
            },
            fragment, submission("q1", "подхватил"), listOf(question), listOf(review(question.id, AnswerVerdict.CORRECT, true)),
            emptyList(), "zh", SessionId("s"), listOf(guess),
        )
        assertTrue(interpreted.toString().contains("Accept") || rejectReason(interpreted).contains("self-report"))
    }

    private fun commentProposal(comment: String, mode: EvidenceMode) = V2Proposal(
        ObservationOutputV2(
            null, "sel", 1, null, "接话", null, SkillType.IDIOM_COMPREHENSION,
            if (mode == EvidenceMode.SELF_REPORTED) ObservationType.PARTIAL_UNDERSTANDING else ObservationType.CORRECT_UNDERSTANDING,
            if (mode == EvidenceMode.SELF_REPORTED) Outcome.WEAK else Outcome.POSITIVE,
            Confidence.MEDIUM, EvidenceSource.SELECTION_COMMENT, comment, "接话", mode, AssistanceLevel.NONE_RECORDED, "Комментарий проверен по тексту.",
        ),
        "{\"comment\":${comment.hashCode()}}",
    )
}

private class RecordingAi(private val hints: Boolean = true) : ScriptedLessonAi() {
    var explainCalls = 0
    var failExplain = false
    var omitSelectionReviews = false
    val evaluateInputs = mutableListOf<EvaluateLessonInput>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun prepareLesson(input: PrepareLessonInput): AppResult<PreparedDraft> {
        val prepared = super.prepareLesson(input) as AppResult.Ok
        return if (hints) prepared else AppResult.Ok(prepared.value.copy(hints = emptyList()))
    }

    override suspend fun evaluateAnswers(input: EvaluateLessonInput): AppResult<EvaluationDraft> {
        evaluateInputs += input
        val drafted = super.evaluateAnswers(input)
        if (drafted is AppResult.Err || omitSelectionReviews) return drafted
        val payload = input.userPayload ?: return drafted
        val selections = json.decodeFromString<EvaluateInputV2>(payload).selections
        val reviews = selections.map {
            SelectionReviewDraft(it.id, it.revision, SelectionReviewStatus.EXPLAINED, "Разбор выделения: ${it.quotedText}.", it.quotedText, null, null, listOf(it.quotedText))
        }
        return AppResult.Ok((drafted as AppResult.Ok).value.copy(selectionReviews = reviews))
    }

    override suspend fun explainSelection(input: ExplainSelectionInput): AppResult<ExplainDraft> {
        explainCalls += 1
        if (failExplain) return AppResult.Err(DomainError.LlmFailed(com.tepmex.extensivereadingtutor.domain.LlmFailureKind.NETWORK, "offline"))
        val quoted = json.decodeFromString<ExplainInputV1>(input.userPayload).selection.quotedText
        return AppResult.Ok(
            ExplainDraft(
                input.selectionId, input.selectionRevision, SelectionReviewStatus.EXPLAINED,
                "Краткое объяснение выделенного места.", quoted, null, null, listOf(quoted),
                "{}", TokenUsage(1, 1, 2), input.providerKind, input.model, "",
            ),
        )
    }
}

private fun seen(
    session: String,
    concept: String,
    at: String,
    context: String,
    sequence: Long,
    id: String,
) = LearnerObservation(
    id = ObservationId(id),
    learnerId = LOCAL_LEARNER,
    language = concept.substringBefore(':'),
    sessionId = SessionId(session),
    questionId = QuestionId("q"),
    conceptId = ConceptId(concept),
    skillType = SkillType.IDIOM_COMPREHENSION,
    observationType = ObservationType.CORRECT_UNDERSTANDING,
    outcome = Outcome.POSITIVE,
    studentEvidence = "ответ",
    sourceEvidence = context,
    sourceAnchors = emptyList(),
    interpretation = "понимание",
    confidence = Confidence.MEDIUM,
    evaluatorProvider = "p",
    evaluatorModel = "m",
    evaluationVersion = "evaluate-v2",
    createdAt = Instant.parse(at),
    sequence = sequence,
    evidenceMode = EvidenceMode.DEMONSTRATED,
    assistance = AssistanceLevel.NONE_RECORDED,
    eligibleForIndependentSuccess = true,
    evidenceUnitId = "unit-$session",
    contextId = context,
    timeZone = "UTC",
    senseScope = SenseScope.CONTEXTUAL_ONLY,
)

private fun knowledge(language: String, id: String, label: String, status: KnowledgeStatus) = LearnerKnowledgeState(
    id = id, learnerId = LOCAL_LEARNER, language = language, conceptId = ConceptId(id), diagnosticKey = null,
    skillType = SkillType.IDIOM_COMPREHENSION, status = status, successCount = 0, weakCount = 0, errorCount = 1,
    lastOutcome = Outcome.NEGATIVE, lastObservedAt = Instant.EPOCH, evidenceStrength = EvidenceStrength.LOW,
    observationIds = emptyList(), errorUnits = 1, projectorVersion = "projector-v2", senseScope = SenseScope.CONTEXTUAL_ONLY,
)

private fun concept(id: String, language: String, label: String) = Concept(ConceptId(id), language, label, ConceptKind.IDIOM, SenseScope.CONTEXTUAL_ONLY, null)

private fun fragmentOf(vararg blocks: String): BookFragment {
    val book = BookId("book")
    val anchors = blocks.mapIndexed { index, text ->
        TextAnchor(book, 0, "c.html", BlockId("b$index"), 0, text.codePointLength(), text, "h$index")
    }
    val original = blocks.joinToString("\n")
    return BookFragment(FragmentId("frag"), book, anchors, original, "hash", "chapter", "c.html", original.codePointLength(), "paragraph-sentence-v1")
}

private fun question(fragment: BookFragment, id: String, quote: String, skill: SkillType = SkillType.IDIOM_COMPREHENSION) = LessonQuestion(
    QuestionId(id), SessionId("s"), QuestionType.IDIOM, "Как вы понимаете это место?", skill, quote,
    listOf(SourceSpan(quote, fragment.anchors.first().blockId, 0, quote.codePointLength())),
    0, QuestionPurpose.COMPREHENSION, null,
    QuestionRubric("Смысл в этом фрагменте.", listOf("опора на цитату"), emptyList(), emptyList(), listOf(quote)),
    id,
)

private fun review(id: QuestionId, verdict: AnswerVerdict, sufficient: Boolean) = QuestionReview(
    id, verdict, "Объяснение опирается на текст фрагмента.", "", sufficient, emptyList(), "смысл",
)

private fun submission(questionId: String, text: String) = StudentSubmission(
    SessionId("s"), listOf(SubmittedAnswer(QuestionId(questionId), text)), null, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"),
)

private fun proposal(questionId: String, type: ObservationType, outcome: Outcome, evidence: String, quote: String) = V2Proposal(
    ObservationOutputV2(
        questionId, null, null, null, quote, null, SkillType.IDIOM_COMPREHENSION, type, outcome, Confidence.MEDIUM,
        EvidenceSource.ANSWER, evidence, quote, EvidenceMode.DEMONSTRATED, AssistanceLevel.NONE_RECORDED, "Интерпретация ответа.",
    ),
    "$questionId|$type|$evidence|$quote",
)

private fun positiveOutput(questionId: String) = ObservationOutputV2(
    questionId, null, null, null, "甲", null, SkillType.IDIOM_COMPREHENSION, ObservationType.CORRECT_UNDERSTANDING,
    Outcome.POSITIVE, Confidence.MEDIUM, EvidenceSource.ANSWER, "верно", "甲", EvidenceMode.DEMONSTRATED,
    AssistanceLevel.NONE_RECORDED, "Ответ показывает смысл.",
)

private fun rejectReason(decision: com.tepmex.extensivereadingtutor.learner.V2Decision): String = when (decision) {
    is com.tepmex.extensivereadingtutor.learner.V2Decision.Reject -> decision.reason
    is com.tepmex.extensivereadingtutor.learner.V2Decision.Accept -> "Accept"
}

private suspend fun import(harness: Harness, paragraphs: List<String>): BookId {
    val book = (harness.books.import(sampleEpub("书", "作者", "zh", paragraphs), "memory://${paragraphs.hashCode()}", "zh") as AppResult.Ok).value
    val prefs = harness.learner.preferences(LOCAL_LEARNER)!!
    harness.learner.savePreferences(prefs.copy(targetFragmentCodePoints = 400))
    return book.id
}

private suspend fun open(harness: Harness, paragraphs: List<String>): SessionId =
    harness.lessons.startOrResumeLesson(import(harness, paragraphs)).unwrap().id

private fun <T> AppResult<T>.unwrap(): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> error(error.toString())
}
