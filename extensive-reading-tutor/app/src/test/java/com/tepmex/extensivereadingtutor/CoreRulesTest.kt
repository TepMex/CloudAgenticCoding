package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.books.EpubExtractor
import com.tepmex.extensivereadingtutor.books.FragmentSelector
import com.tepmex.extensivereadingtutor.books.TextSlice
import com.tepmex.extensivereadingtutor.books.anchorMatchesBlock
import com.tepmex.extensivereadingtutor.books.codePointLength
import com.tepmex.extensivereadingtutor.books.sliceCodePoints
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.EvidenceStrength
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.SessionEvent
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubmittedAnswer
import com.tepmex.extensivereadingtutor.domain.TextCursor
import com.tepmex.extensivereadingtutor.domain.next
import com.tepmex.extensivereadingtutor.learner.KnowledgeProjector
import com.tepmex.extensivereadingtutor.learner.ObservationPolicy
import com.tepmex.extensivereadingtutor.learner.ProposalInput
import com.tepmex.extensivereadingtutor.pedagogy.ContextBuilder
import com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy
import com.tepmex.extensivereadingtutor.pedagogy.Prompts
import com.tepmex.extensivereadingtutor.security.SecretEnvelope
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRulesTest {
    @Test
    fun transitionsFollowTheLessonMachine() {
        assertEquals(SessionStatus.PREPARING, SessionStatus.CREATED.next(SessionEvent.BEGIN_PREPARE))
        assertEquals(SessionStatus.INTRO_READY, SessionStatus.PREPARING.next(SessionEvent.COMMIT_PREPARE))
        assertEquals(SessionStatus.CREATED, SessionStatus.PREPARING.next(SessionEvent.ABORT_PREPARE))
        assertEquals(SessionStatus.QUESTIONS_READY, SessionStatus.INTRO_READY.next(SessionEvent.REVEAL_QUESTIONS))
        assertEquals(SessionStatus.AWAITING_ANSWERS, SessionStatus.QUESTIONS_READY.next(SessionEvent.BEGIN_ANSWERING))
        assertEquals(SessionStatus.EVALUATING, SessionStatus.AWAITING_ANSWERS.next(SessionEvent.BEGIN_EVALUATE))
        assertEquals(SessionStatus.REVIEW_READY, SessionStatus.EVALUATING.next(SessionEvent.COMMIT_REVIEW))
        assertEquals(SessionStatus.COMPLETED, SessionStatus.REVIEW_READY.next(SessionEvent.COMPLETE))
        assertNull(SessionStatus.COMPLETED.next(SessionEvent.BEGIN_PREPARE))
        assertNull(SessionStatus.AWAITING_ANSWERS.next(SessionEvent.COMPLETE))
        assertEquals(SessionStatus.EVALUATING, SessionStatus.EVALUATING.next(SessionEvent.BEGIN_EVALUATE))
    }

    @Test
    fun codePointsDoNotSplitASupplementaryCharacter() {
        val character = "𠮷"
        assertEquals(2, character.length)
        assertEquals(1, character.codePointLength())
        assertEquals(character, character.sliceCodePoints(0, 1))
        val blocks = listOf(block(0, character + "雷打不动。后面还有很长的句子没有空格直到边界"))
        val first = FragmentSelector.select(blocks, TextCursor(0, 0), 1, 1)
        assertEquals(character, first.single().text)
        assertFalse(first.single().text.any { Character.isSurrogate(it) && !first.single().text.contains(character) })
        val fragment = FragmentSelector.materialize(BookId("b"), com.tepmex.extensivereadingtutor.domain.FragmentId("f"), first)
        assertTrue(anchorMatchesBlock(fragment.anchors.single(), blocks.single()))
    }

    @Test
    fun fragmentsTileChineseAndEnglishWithoutGaps() {
        val blocks = listOf(
            block(0, "雷打不动。他每天五点起床读书。"),
            block(1, "Hello wonderful world. Next sentence stays whole."),
            block(2, "朋友问他为什么如此坚持。"),
        )
        val produced = walk(blocks, target = 8)
        assertTrue(produced.size >= 3)
        var cursor = TextCursor(0, 0)
        produced.forEach { fragment ->
            val anchor = fragment.anchors.first()
            val owning = blocks.first { it.id == anchor.blockId }
            assertEquals(cursor.blockOrder, owning.orderInBook)
            assertEquals(cursor.codePoint, anchor.startCodePoint)
            fragment.anchors.zipWithNext().forEach { (left, right) ->
                if (left.blockId == right.blockId) {
                    assertEquals(left.endCodePoint, right.startCodePoint)
                } else {
                    val leftBlock = blocks.first { it.id == left.blockId }
                    assertEquals(leftBlock.text.codePointLength(), left.endCodePoint)
                    assertEquals(0, right.startCodePoint)
                }
            }
            fragment.anchors.forEach { item ->
                assertTrue(anchorMatchesBlock(item, blocks.first { it.id == item.blockId }))
                assertTrue(fragment.originalText.contains(item.quote))
            }
            cursor = FragmentSelector.advance(blocks, fragment)
        }
        assertTrue(cursor.blockOrder > blocks.last().orderInBook || cursor.codePoint == blocks.last().text.codePointLength())
        val joined = blocks.joinToString("\n") { it.text }
        val rebuilt = buildString {
            produced.forEachIndexed { index, fragment ->
                if (index > 0) {
                    val previous = produced[index - 1].anchors.last()
                    val next = fragment.anchors.first()
                    if (previous.blockId != next.blockId) append('\n')
                }
                append(fragment.originalText)
            }
        }
        assertEquals(joined, rebuilt)
    }

    @Test
    fun englishSplitKeepsWordsIntact() {
        val blocks = listOf(block(0, "Hello wonderful"))
        val slices = FragmentSelector.select(blocks, TextCursor(0, 0), 4, 8)
        assertEquals("Hello ", slices.single().text)
        val rest = FragmentSelector.select(blocks, TextCursor(0, slices.single().endCodePoint), 20, 40)
        assertEquals("wonderful", rest.single().text)
    }

    @Test
    fun epubKeepsChineseAndDropsRuby() {
        val bytes = sampleEpub(
            title = "城南旧事",
            author = "林海音",
            language = "zh-CN",
            paragraphs = listOf("雷打不动。", "他<ruby>说<rt>shuo</rt></ruby>原则。"),
        )
        val extracted = (EpubExtractor.extract(bytes) as AppResult.Ok).value
        assertEquals("城南旧事", extracted.title)
        assertEquals("林海音", extracted.author)
        assertEquals("zh", extracted.language)
        assertEquals("雷打不动。", extracted.blocks[0].text)
        assertEquals("他说原则。", extracted.blocks[1].text)
        assertFalse(extracted.blocks[1].text.contains("shuo"))
    }

    @Test
    fun missingMetadataStillImportsText() {
        val bytes = sampleEpub(title = "", author = "", language = "", paragraphs = listOf("Текст."))
        val extracted = (EpubExtractor.extract(bytes) as AppResult.Ok).value
        assertEquals("Untitled", extracted.title)
        assertEquals("", extracted.author)
        assertNull(extracted.language)
    }

    @Test
    fun badQuoteIsRejectedAndMatchingQuoteIsKept() {
        val fragment = FragmentSelector.materialize(
            BookId("b"),
            com.tepmex.extensivereadingtutor.domain.FragmentId("f"),
            listOf(TextSlice(block(0, "雷打不动。他每天读书。"), 0, "雷打不动。他每天读书。".codePointLength())),
        )
        val submission = StudentSubmission(
            SessionId("s"),
            listOf(SubmittedAnswer(QuestionId("q"), "это бесстрашие")),
            null, false, null, null, 0, Instant.EPOCH,
        )
        val policy = ObservationPolicy()
        val rejected = policy.decide(
            proposal("雷打不动", "бесстрашие", "нет такой цитаты", ObservationType.SEMANTIC_MISINTERPRETATION, Outcome.NEGATIVE),
            fragment, submission, "zh", SessionId("s"),
        )
        assertTrue(rejected is com.tepmex.extensivereadingtutor.learner.PolicyDecision.Reject)
        val accepted = policy.decide(
            proposal("雷打不动", "бесстраш", "雷打不动", ObservationType.SEMANTIC_MISINTERPRETATION, Outcome.NEGATIVE),
            fragment, submission, "zh", SessionId("s"),
        )
        assertTrue(accepted is com.tepmex.extensivereadingtutor.learner.PolicyDecision.Accept)
        val mismatched = policy.decide(
            proposal("雷打不动", "бесстраш", "雷打不动", ObservationType.SEMANTIC_MISINTERPRETATION, Outcome.POSITIVE),
            fragment, submission, "zh", SessionId("s"),
        )
        assertTrue(mismatched is com.tepmex.extensivereadingtutor.learner.PolicyDecision.Reject)
    }

    @Test
    fun projectionIsDeterministicAndKeepsBothOutcomes() {
        val projector = KnowledgeProjector()
        val first = observation(1, Outcome.NEGATIVE, ObservationType.SEMANTIC_MISINTERPRETATION, "lesson-1")
        val learning = projector.project(listOf(first), emptySet()).single()
        assertEquals(KnowledgeStatus.LEARNING, learning.status)
        assertEquals(EvidenceStrength.LOW, learning.evidenceStrength)
        assertEquals(1, learning.errorCount)
        val second = observation(2, Outcome.POSITIVE, ObservationType.CORRECT_UNDERSTANDING, "lesson-2")
        val third = observation(3, Outcome.POSITIVE, ObservationType.CORRECT_UNDERSTANDING, "lesson-3")
        val reviewed = projector.project(listOf(first, second), emptySet()).single()
        assertEquals(KnowledgeStatus.NEEDS_REVIEW, reviewed.status)
        val likely = projector.project(listOf(first, second, third), emptySet()).single()
        assertEquals(KnowledgeStatus.LIKELY_KNOWN, likely.status)
        val again = projector.project(listOf(third, first, second), emptySet()).single()
        assertEquals(likely, again)
        val without = projector.project(listOf(first, second, third), setOf(first.id)).single()
        assertEquals(0, without.errorCount)
        assertEquals(listOf(second.id, third.id), without.observationIds)
        val production = observation(4, Outcome.POSITIVE, ObservationType.SUCCESSFUL_ACTIVE_USAGE, "lesson-4", SkillType.ACTIVE_PRODUCTION)
        val rows = projector.project(listOf(third, production), emptySet())
        assertEquals(2, rows.size)
    }

    @Test
    fun stableNeedsThreeRecentSuccessesInTwoLessons() {
        val items = (1..3).map {
            observation(it.toLong(), Outcome.POSITIVE, ObservationType.CORRECT_UNDERSTANDING, if (it == 1) "lesson-a" else "lesson-b")
        }
        val state = KnowledgeProjector().project(items, emptySet()).single()
        assertEquals(KnowledgeStatus.STABLE, state.status)
        assertEquals(EvidenceStrength.HIGH, state.evidenceStrength)
    }

    @Test
    fun hintDependentSuccessDoesNotCountAsMastery() {
        val items = (1..3).map {
            observation(it.toLong(), Outcome.WEAK, ObservationType.HINT_DEPENDENT_SUCCESS, "lesson-$it")
        }
        val state = KnowledgeProjector().project(items, emptySet()).single()
        assertEquals(KnowledgeStatus.LEARNING, state.status)
        assertEquals(0, state.successCount)
        assertEquals(3, state.weakCount)
    }

    @Test
    fun contextKeepsTheFragmentOutOfTheMemoryBudget() {
        val fragment = "雷打不动。" + "文".repeat(200)
        val concepts = (1..40).map { Concept(ConceptId("zh:词$it"), "zh", "词$it") }
        val knowledge = concepts.map {
            com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState(
                id = it.id.value, learnerId = LOCAL_LEARNER, language = "zh", conceptId = it.id, diagnosticKey = null,
                skillType = SkillType.VOCABULARY_RECOGNITION, status = KnowledgeStatus.LEARNING, successCount = 0,
                weakCount = 0, errorCount = 1, lastOutcome = Outcome.NEGATIVE, lastObservedAt = Instant.EPOCH,
                evidenceStrength = EvidenceStrength.LOW, observationIds = emptyList(),
            )
        }
        val packet = ContextBuilder(memoryBudgetChars = 80).build(fragment, knowledge, concepts, List(10) { "вопрос $it" })
        assertTrue(packet.omittedCount > 0)
        val payload = Prompts.prepareUser(
            com.tepmex.extensivereadingtutor.domain.defaultPreferences(Instant.EPOCH),
            "Глава", null, fragment,
            packet,
        )
        assertTrue(payload.contains(fragment))
        assertTrue(Prompts.systemPrepare(PedagogicalStrategy.V1).contains("Never rewrite"))
        assertTrue(payload.contains(Prompts.DATA_NOTICE))
    }

    @Test
    fun secretEnvelopeRoundTripsWithoutStoringTheKeyBesideTheCiphertext() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val packed = SecretEnvelope.pack(cipher.iv, cipher.doFinal("sk-test-key".toByteArray()))
        val (iv, ciphertext) = SecretEnvelope.unpack(packed)
        val back = Cipher.getInstance("AES/GCM/NoPadding")
        back.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        assertEquals("sk-test-key", back.doFinal(ciphertext).decodeToString())
        assertFalse(String(packed).contains("sk-test-key"))
    }

    private fun proposal(label: String, evidence: String, quote: String, type: ObservationType, outcome: Outcome) = ProposalInput(
        questionId = QuestionId("q"), conceptLabel = label, skillType = SkillType.IDIOM_COMPREHENSION,
        observationType = type, outcome = outcome, studentEvidence = evidence, sourceQuote = quote,
        interpretation = "Разбор ответа.", confidence = Confidence.MEDIUM, payloadJson = "{}",
    )

    private fun observation(
        sequence: Long,
        outcome: Outcome,
        type: ObservationType,
        session: String,
        skill: SkillType = SkillType.IDIOM_COMPREHENSION,
    ) = LearnerObservation(
        id = ObservationId("obs-$sequence"), learnerId = LOCAL_LEARNER, language = "zh", sessionId = SessionId(session),
        questionId = QuestionId("q"), conceptId = ConceptId("zh:雷打不动"), skillType = skill, observationType = type,
        outcome = outcome, studentEvidence = "ответ", sourceEvidence = "雷打不动", sourceAnchors = emptyList(),
        interpretation = "наблюдение", confidence = Confidence.MEDIUM, evaluatorProvider = "OPENAI_COMPATIBLE",
        evaluatorModel = "m", evaluationVersion = "evaluate-v1", createdAt = Instant.EPOCH.plusSeconds(sequence), sequence = sequence,
    )

    private fun walk(blocks: List<BookBlock>, target: Int) = buildList {
        var cursor = TextCursor(blocks.first().orderInBook, 0)
        repeat(20) {
            val slices = FragmentSelector.select(blocks, cursor, target, FragmentSelector.hardMax(target))
            if (slices.isEmpty()) return@buildList
            val fragment = FragmentSelector.materialize(BookId("b"), com.tepmex.extensivereadingtutor.domain.FragmentId("f$it"), slices)
            add(fragment)
            cursor = FragmentSelector.advance(blocks, fragment)
        }
    }

    private fun block(order: Int, text: String) = BookBlock(
        BlockId("block-$order"), BookId("b"), 0, "c.xhtml", order, text, "h$order", null,
    )
}

internal fun sampleEpub(title: String, author: String, language: String, paragraphs: List<String>): ByteArray {
    val html = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><body>""")
        paragraphs.forEach { append("<p>").append(it).append("</p>") }
        append("</body></html>")
    }
    val opf = """
        <?xml version="1.0" encoding="utf-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:creator>$author</dc:creator>
            <dc:language>$language</dc:language>
            <dc:identifier id="id">book-1</dc:identifier>
          </metadata>
          <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
          <spine><itemref idref="c1"/></spine>
        </package>
    """.trimIndent()
    val container = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("META-INF/container.xml"))
        zip.write(container.toByteArray())
        zip.putNextEntry(ZipEntry("OEBPS/book.opf"))
        zip.write(opf.toByteArray())
        zip.putNextEntry(ZipEntry("OEBPS/c1.xhtml"))
        zip.write(html.toByteArray())
    }
    return out.toByteArray()
}
