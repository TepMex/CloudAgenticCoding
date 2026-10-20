package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.books.BookFileStore
import com.tepmex.extensivereadingtutor.books.BookService
import com.tepmex.extensivereadingtutor.books.EpubExtractor
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.AppClock
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.IdGenerator
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.LessonReview
import com.tepmex.extensivereadingtutor.domain.ModelSnapshot
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.domain.SubmittedAnswer
import com.tepmex.extensivereadingtutor.domain.TokenUsage
import com.tepmex.extensivereadingtutor.learner.LearnerService
import com.tepmex.extensivereadingtutor.lessons.LessonService
import com.tepmex.extensivereadingtutor.llm.EvaluateLessonInput
import com.tepmex.extensivereadingtutor.llm.EvaluationDraft
import com.tepmex.extensivereadingtutor.llm.LessonAi
import com.tepmex.extensivereadingtutor.llm.ObservationDraft
import com.tepmex.extensivereadingtutor.llm.PrepareLessonInput
import com.tepmex.extensivereadingtutor.llm.PreparedDraft
import com.tepmex.extensivereadingtutor.llm.QuestionDraft
import com.tepmex.extensivereadingtutor.llm.ReviewDraft
import com.tepmex.extensivereadingtutor.storage.InMemoryTutorStore
import com.tepmex.extensivereadingtutor.storage.StoreSession
import com.tepmex.extensivereadingtutor.storage.TutorStore
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorScenariosTest {
    @Test
    fun lessonRunsThroughCompletionAndTheNextFragment() = runBlocking {
        val harness = Harness()
        val book = harness.import()
        val first = harness.lessons.startOrResumeLesson(book).unwrap()
        val again = harness.lessons.startOrResumeLesson(book).unwrap()
        assertEquals(first.id, again.id)
        assertEquals(SessionStatus.CREATED, first.status)
        val prepared = harness.lessons.prepareLesson(first.id).unwrap()
        assertEquals(SessionStatus.INTRO_READY, prepared.status)
        assertEquals(1, harness.ai.prepareCalls)
        val fragment = harness.store.read { it.fragment(prepared.fragmentId) }!!
        assertTrue(fragment.originalText.contains("雷打不动"))
        assertNotEquals(fragment.originalText, prepared.prepared!!.introduction)
        assertEquals(fragment.originalText, harness.store.read { it.fragment(prepared.fragmentId) }!!.originalText)
        val repeated = harness.lessons.prepareLesson(first.id).unwrap()
        assertEquals(1, harness.ai.prepareCalls)
        assertEquals(prepared.prepared!!.questions.single().id, repeated.prepared!!.questions.single().id)
        val question = prepared.prepared!!.questions.single()
        harness.lessons.revealQuestions(first.id).unwrap()
        harness.lessons.saveAnswerDraft(first.id, question.id, "черновик").unwrap()
        val resumed = Harness(harness.store, harness.ai, harness.clock, harness.models).lessons
        val drafts = resumed.drafts(first.id)
        assertEquals("черновик", drafts.single().text)
        val answering = resumed.getActiveLesson(book)!!
        assertEquals(SessionStatus.AWAITING_ANSWERS, answering.status)
        val submitted = resumed.submitAnswers(first.id, answer(first.id, question.id, "это бесстрашие", SubjectiveDifficulty.TOO_EASY)).unwrap()
        assertEquals(SessionStatus.AWAITING_ANSWERS, submitted.status)
        assertEquals("это бесстрашие", submitted.submission!!.answers.single().text)
        val locked = resumed.saveAnswerDraft(first.id, question.id, "другой")
        assertTrue(locked is AppResult.Err)
        val reviewed = resumed.evaluateAnswers(first.id).unwrap()
        assertEquals(SessionStatus.REVIEW_READY, reviewed.status)
        assertEquals(AnswerVerdict.INCORRECT, reviewed.review!!.results.single().verdict)
        val observations = harness.store.read { it.observations(LOCAL_LEARNER) }
        assertEquals(1, observations.size)
        assertEquals(ObservationType.SEMANTIC_MISINTERPRETATION, observations.single().observationType)
        assertEquals("雷打不动", observations.single().sourceEvidence)
        assertEquals("model-a", observations.single().evaluatorModel)
        val proposals = harness.store.read { it.proposals(first.id) }
        assertEquals(2, proposals.size)
        assertTrue(proposals.any { it.rejectionReason?.contains("quote") == true })
        val secondPass = resumed.evaluateAnswers(first.id).unwrap()
        assertEquals(1, harness.ai.evaluateCalls)
        assertEquals(reviewed.review, secondPass.review)
        assertEquals(1, harness.store.read { it.observations(LOCAL_LEARNER) }.size)
        val knowledge = harness.learner.knowledge(LOCAL_LEARNER).single()
        assertEquals("zh:雷打不动", knowledge.conceptId!!.value)
        assertEquals(1, knowledge.errorCount)
        val completed = resumed.completeLesson(first.id).unwrap()
        assertEquals(SessionStatus.COMPLETED, completed.status)
        assertNull(resumed.getActiveLesson(book))
        val progress = resumed.getReadingProgress(book)!!
        assertEquals(listOf(fragment.id), progress.completedFragmentIds)
        assertTrue(progress.cursor.blockOrder > 0 || progress.cursor.codePoint > 0)
        val hash = fragment.textHash
        val prefs = harness.learner.preferences(LOCAL_LEARNER)!!
        assertTrue(prefs.targetFragmentCodePoints > 20)
        val next = resumed.startOrResumeLesson(book).unwrap()
        assertNotEquals(first.id, next.id)
        val nextFragment = harness.store.read { it.fragment(next.fragmentId) }!!
        assertNotEquals(hash, nextFragment.textHash)
        assertEquals(hash, harness.store.read { it.fragment(fragment.id) }!!.textHash)
        harness.models.snapshot = harness.models.snapshot.copy(model = "model-b")
        val secondPrepared = resumed.prepareLesson(next.id).unwrap()
        assertEquals("model-b", harness.store.read { it.callsForSession(next.id) }.single().model)
        assertEquals("model-a", harness.store.read { it.callsForSession(first.id) }.single { it.operation.name == "PREPARE_LESSON" }.model)
        assertTrue(harness.ai.prepareInputs.last().memory.concepts.any { it.label == "雷打不动" })
        assertTrue(harness.ai.prepareInputs.last().fragmentText == secondPrepared.let {
            harness.store.read { db -> db.fragment(next.fragmentId) }!!.originalText
        })
        val retracted = harness.learner.retract(observations.single().id, "Оценка перепутала контекст.")
        assertTrue(retracted is AppResult.Ok)
        assertEquals(1, harness.store.read { it.observations(LOCAL_LEARNER) }.size)
        assertTrue(harness.learner.knowledge(LOCAL_LEARNER).isEmpty())
        val rebuilt = harness.learner.recompute(LOCAL_LEARNER)
        assertTrue(rebuilt.isEmpty())
    }

    @Test
    fun crashesRestoreTheLessonWithoutLosingAnswers() = runBlocking {
        val store = BoomStore(InMemoryTutorStore())
        val harness = Harness(store)
        val book = harness.import()
        val session = harness.lessons.startOrResumeLesson(book).unwrap()
        store.boomOnPrepared = true
        try {
            harness.lessons.prepareLesson(session.id)
            error("prepare should crash before save")
        } catch (error: RuntimeException) {
            assertEquals("crash-before-save", error.message)
        }
        assertEquals(SessionStatus.PREPARING, harness.lessons.getActiveLesson(book)!!.status)
        val recovered = harness.lessons.recoverInterruptedSessions()
        assertEquals(SessionStatus.CREATED, recovered.single().status)
        val prepared = harness.lessons.prepareLesson(session.id).unwrap()
        assertEquals(2, harness.ai.prepareCalls)
        assertEquals(1, prepared.prepared!!.questions.size)
        val question = prepared.prepared!!.questions.single()
        harness.lessons.revealQuestions(session.id).unwrap()
        harness.lessons.saveAnswerDraft(session.id, question.id, "черновик").unwrap()
        val reopened = Harness(store, harness.ai, harness.clock, harness.models)
        assertEquals("черновик", reopened.lessons.drafts(session.id).single().text)
        reopened.lessons.submitAnswers(session.id, answer(session.id, question.id, "это бесстрашие", null)).unwrap()
        assertEquals(SessionStatus.AWAITING_ANSWERS, reopened.lessons.getActiveLesson(book)!!.status)
        harness.ai.failEvaluate = true
        val failed = reopened.lessons.evaluateAnswers(session.id)
        assertTrue(failed is AppResult.Err)
        val afterFailure = reopened.lessons.getActiveLesson(book)!!
        assertEquals(SessionStatus.EVALUATING, afterFailure.status)
        assertEquals("это бесстрашие", afterFailure.submission!!.answers.single().text)
        store.boomOnReview = true
        try {
            reopened.lessons.evaluateAnswers(session.id)
            error("review should crash before save")
        } catch (error: RuntimeException) {
            assertEquals("crash-before-review", error.message)
        }
        assertEquals(0, store.read { it.observations(LOCAL_LEARNER) }.size)
        assertEquals(SessionStatus.EVALUATING, reopened.lessons.getActiveLesson(book)!!.status)
        val reviewed = reopened.lessons.evaluateAnswers(session.id).unwrap()
        assertEquals(SessionStatus.REVIEW_READY, reviewed.status)
        assertEquals(1, store.read { it.observations(LOCAL_LEARNER) }.size)
        val duplicate = reopened.lessons.evaluateAnswers(session.id).unwrap()
        assertEquals(reviewed.review!!.aiCallId, duplicate.review!!.aiCallId)
        assertEquals(1, store.read { it.observations(LOCAL_LEARNER) }.size)
    }

    @Test
    fun sameBytesImportOnceAndHistoryCanBeErasedSeparately() = runBlocking {
        val harness = Harness()
        val bytes = lessonBook()
        val first = (harness.books.import(bytes, "content://a", "zh") as AppResult.Ok).value
        val second = (harness.books.import(bytes, "content://b", "zh") as AppResult.Ok).value
        assertEquals(first.id, second.id)
        val session = harness.lessons.startOrResumeLesson(first.id).unwrap()
        harness.books.removeFromLibrary(first.id)
        val hidden = harness.lessons.startOrResumeLesson(first.id)
        assertTrue(hidden is AppResult.Err)
        assertEquals(session.id, harness.store.read { it.session(session.id) }!!.id)
        val restored = (harness.books.import(bytes, "content://c", "zh") as AppResult.Ok).value
        assertEquals(first.id, restored.id)
        assertEquals(session.id, harness.lessons.getActiveLesson(restored.id)!!.id)
        harness.books.eraseLearningHistory(restored.id)
        assertNull(harness.lessons.getActiveLesson(restored.id))
        assertTrue(harness.store.read { it.observations(LOCAL_LEARNER) }.isEmpty())
        assertTrue(harness.lessons.startOrResumeLesson(restored.id) is AppResult.Ok)
    }

    private fun answer(session: SessionId, question: com.tepmex.extensivereadingtutor.domain.QuestionId, text: String, difficulty: SubjectiveDifficulty?) =
        StudentSubmission(session, listOf(SubmittedAnswer(question, text)), difficulty, false, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"))
}

class Harness(
    val store: TutorStore = InMemoryTutorStore(),
    val ai: ScriptedLessonAi = ScriptedLessonAi(),
    val clock: MutableClock = MutableClock(),
    val models: HoldingModel = HoldingModel(ModelSnapshot("default", "OPENAI_COMPATIBLE", "model-a")),
    val files: MemoryFiles = MemoryFiles(),
) {
    val learner = LearnerService(store, clock = clock, ids = SeqIds())
    val books = BookService(store, files, EpubExtractor, clock, SeqIds(), learner)
    val lessons = LessonService(store, ai, learner, clock, SeqIds(), models)

    suspend fun import(): BookId {
        val book = (books.import(lessonBook(), "memory://book", "zh") as AppResult.Ok).value
        val prefs = learner.preferences(LOCAL_LEARNER)!!
        learner.savePreferences(prefs.copy(targetFragmentCodePoints = 20))
        return book.id
    }
}

class BoomStore(private val inner: TutorStore) : TutorStore {
    var boomOnPrepared = false
    var boomOnReview = false

    override suspend fun <T> transaction(block: (StoreSession) -> T): T = inner.transaction { db ->
        block(object : StoreSession by db {
            override fun savePrepared(sessionId: SessionId, introduction: String, hints: List<String>, strategyVersion: String) {
                if (boomOnPrepared) {
                    boomOnPrepared = false
                    throw RuntimeException("crash-before-save")
                }
                db.savePrepared(sessionId, introduction, hints, strategyVersion)
            }

            override fun insertReview(review: LessonReview) {
                if (boomOnReview) {
                    boomOnReview = false
                    throw RuntimeException("crash-before-review")
                }
                db.insertReview(review)
            }
        })
    }

    override suspend fun <T> read(block: (StoreSession) -> T): T = inner.read(block)
}

class ScriptedLessonAi : LessonAi {
    val prepareInputs = mutableListOf<PrepareLessonInput>()
    var prepareCalls = 0
    var evaluateCalls = 0
    var failEvaluate = false

    override suspend fun prepareLesson(input: PrepareLessonInput): AppResult<PreparedDraft> {
        prepareCalls += 1
        prepareInputs += input
        val quote = if ("雷打不动" in input.fragmentText) "雷打不动" else input.fragmentText.take(1)
        return AppResult.Ok(
            PreparedDraft(
                introduction = "Прочитайте фрагмент самостоятельно.",
                hints = listOf("Полного перевода пока нет."),
                questions = listOf(
                    QuestionDraft(QuestionType.IDIOM, "Как вы понимаете это место?", SkillType.IDIOM_COMPREHENSION, quote, quote),
                ),
                rawJson = "{}",
                usage = TokenUsage(10, 20, 30),
                providerKind = input.providerKind,
                model = input.model,
            ),
        )
    }

    override suspend fun evaluateAnswers(input: EvaluateLessonInput): AppResult<EvaluationDraft> {
        evaluateCalls += 1
        if (failEvaluate) {
            failEvaluate = false
            return AppResult.Err(DomainError.LlmFailed(com.tepmex.extensivereadingtutor.domain.LlmFailureKind.NETWORK, "offline"))
        }
        val reviews = input.questions.map { question ->
            val answer = input.submission.answers.first { it.questionId == question.id }.text
            val wrong = "бесстраш" in answer
            ReviewDraft(question.id.value, if (wrong) AnswerVerdict.INCORRECT else AnswerVerdict.CORRECT, "Пояснение.", if (wrong) "Смысл другой." else "", !wrong, emptyList())
        }
        val observations = input.questions.map { question ->
            val answer = input.submission.answers.first { it.questionId == question.id }.text
            val quote = question.sourceAnchors.first().quote
            val wrong = "бесстраш" in answer
            ObservationDraft(
                question.id.value, quote, question.targetSkill,
                if (wrong) ObservationType.SEMANTIC_MISINTERPRETATION else ObservationType.CORRECT_UNDERSTANDING,
                if (wrong) Outcome.NEGATIVE else Outcome.POSITIVE,
                if (wrong) "бесстраш" else answer,
                quote,
                if (wrong) "Идиома прочитана как бесстрашие." else "Смысл передан.",
                Confidence.MEDIUM,
                "{}",
            )
        }
        val bad = ObservationDraft(
            input.questions.first().id.value, "没有这个词", SkillType.VOCABULARY_RECOGNITION,
            ObservationType.UNKNOWN_VOCABULARY, Outcome.NEGATIVE, input.submission.answers.first().text.take(1),
            "不存在的句子", "Цитаты нет.", Confidence.LOW, """{"bad":true}""",
        )
        return AppResult.Ok(
            EvaluationDraft(reviews, observations + bad, emptyList(), listOf("Вернуться к идиоме."), "{}", TokenUsage(4, 6, 10), input.providerKind, input.model),
        )
    }
}

class MutableClock(var instant: Instant = Instant.parse("2026-01-01T00:00:00Z")) : AppClock {
    override fun now(): Instant = instant
}

class SeqIds : IdGenerator {
    private var value = 0
    override fun newId(prefix: String): String {
        value += 1
        return "$prefix-$value"
    }
}

class MemoryFiles : BookFileStore {
    val files = mutableMapOf<String, ByteArray>()
    override fun put(bookId: String, bytes: ByteArray): String {
        files[bookId] = bytes
        return "memory://$bookId"
    }
    override fun delete(localRef: String) {
        files.remove(localRef.removePrefix("memory://"))
    }
}

private fun lessonBook(): ByteArray = sampleEpub(
    title = "原则",
    author = "不知名",
    language = "zh-CN",
    paragraphs = listOf(
        "雷打不动。他每天五点起床读书。",
        "朋友问他为什么如此坚持。",
        "他说这是原则，不是勇气。",
        "第二天他又坐到书桌前。",
    ),
)

private fun <T> AppResult<T>.unwrap(): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> error(error.toString())
}
