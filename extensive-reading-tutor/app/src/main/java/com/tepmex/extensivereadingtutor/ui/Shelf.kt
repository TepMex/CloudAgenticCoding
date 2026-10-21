package com.tepmex.extensivereadingtutor.ui

import com.tepmex.extensivereadingtutor.books.codePointLength
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LessonSession
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.TextCursor
import com.tepmex.extensivereadingtutor.storage.StoreSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ChapterMark { DONE, CURRENT, AHEAD }

enum class LessonFeedSection { FRAGMENT, INTRODUCTION, QUESTIONS, REVIEW }

data class ChapterRow(val title: String, val mark: ChapterMark)

data class SessionLine(
    val id: String,
    val status: SessionStatus,
    val at: Instant,
    val chapter: String,
)

data class ShelfBook(
    val id: String,
    val title: String,
    val author: String,
    val percent: Int,
    val done: Boolean,
    val place: String,
    val lastSession: String,
    val charsRead: Int,
    val charsTotal: Int,
    val completedLessons: Int,
    val chapters: List<ChapterRow>,
    val sessions: List<SessionLine>,
    val activeStatus: SessionStatus?,
    val activeSubmitted: Boolean,
    val coverSeed: Int,
)

data class ShelfLoad(
    val books: List<ShelfBook>,
    val knowledge: List<LearnerKnowledgeState>,
    val concepts: List<Concept>,
    val observations: List<LearnerObservation>,
    val completedDays: List<LocalDate>,
    val observationDays: List<LocalDate>,
)

data class WordCard(
    val expression: String,
    val sentence: String,
    val gloss: String,
    val example: String?,
    val status: KnowledgeStatus?,
    val skill: SkillType?,
)

fun readCodePoints(blocks: List<BookBlock>, cursor: TextCursor): Int {
    var read = 0
    for (block in blocks) {
        val length = block.text.codePointLength()
        when {
            block.orderInBook < cursor.blockOrder -> read += length
            block.orderInBook == cursor.blockOrder -> read += cursor.codePoint.coerceIn(0, length)
        }
    }
    return read
}

fun progressPercent(read: Int, total: Int): Int =
    if (total == 0) 0 else ((read * 100L) / total).toInt().coerceIn(0, 100)

fun finished(blocks: List<BookBlock>, cursor: TextCursor): Boolean {
    if (blocks.isEmpty()) return true
    val last = blocks.maxBy { it.orderInBook }
    return cursor.blockOrder > last.orderInBook ||
        (cursor.blockOrder == last.orderInBook && cursor.codePoint >= last.text.codePointLength())
}

fun chapterRows(blocks: List<BookBlock>, cursor: TextCursor): List<ChapterRow> {
    if (blocks.isEmpty()) return emptyList()
    val groups = mutableListOf<Triple<String, Int, Int>>()
    for (block in blocks.sortedBy { it.orderInBook }) {
        val title = block.chapterTitle?.takeIf { it.isNotBlank() } ?: "Без названия"
        val previous = groups.lastOrNull()
        if (previous != null && previous.first == title) {
            groups[groups.lastIndex] = Triple(title, previous.second, block.orderInBook)
        } else {
            groups += Triple(title, block.orderInBook, block.orderInBook)
        }
    }
    return groups.map { (title, first, last) ->
        val mark = when {
            cursor.blockOrder > last -> ChapterMark.DONE
            cursor.blockOrder < first -> ChapterMark.AHEAD
            else -> ChapterMark.CURRENT
        }
        ChapterRow(title, mark)
    }
}

fun placeLabel(blocks: List<BookBlock>, cursor: TextCursor): String {
    val ordered = blocks.sortedBy { it.orderInBook }
    val current = ordered.firstOrNull { it.orderInBook == cursor.blockOrder }
        ?: ordered.lastOrNull { it.orderInBook < cursor.blockOrder }
        ?: ordered.firstOrNull()
    return current?.chapterTitle?.takeIf { it.isNotBlank() } ?: "Начало книги"
}

fun visibleLessonSections(status: SessionStatus, submitted: Boolean): List<LessonFeedSection> {
    val sections = mutableListOf(LessonFeedSection.FRAGMENT)
    if (status.ordinal >= SessionStatus.INTRO_READY.ordinal) sections += LessonFeedSection.INTRODUCTION
    if (status.ordinal >= SessionStatus.QUESTIONS_READY.ordinal) sections += LessonFeedSection.QUESTIONS
    if (submitted || status.ordinal >= SessionStatus.EVALUATING.ordinal) sections += LessonFeedSection.REVIEW
    return sections
}

fun loadShelf(db: StoreSession, zone: ZoneId = ZoneId.systemDefault()): ShelfLoad {
    val sessions = db.allSessions()
    val concepts = db.concepts()
    val knowledge = db.knowledge(LOCAL_LEARNER)
    val observations = db.observations(LOCAL_LEARNER)
    val books = db.books().filter { it.removedAt == null }.map { book ->
        val blocks = db.blocks(book.id)
        val cursor = db.progress(book.id)?.cursor ?: TextCursor(blocks.firstOrNull()?.orderInBook ?: 0, 0)
        val total = blocks.sumOf { it.text.codePointLength() }
        val read = readCodePoints(blocks, cursor)
        val mine = sessions.filter { it.bookId == book.id }.sortedByDescending { it.updatedAt }
        val active = mine.firstOrNull { it.status != SessionStatus.COMPLETED }
        val latest = mine.firstOrNull()
        ShelfBook(
            id = book.id.value,
            title = book.title.ifBlank { "Без названия" },
            author = book.author.ifBlank { "Автор не указан" },
            percent = progressPercent(read, total),
            done = finished(blocks, cursor),
            place = placeLabel(blocks, cursor),
            lastSession = latest?.let { "${formatDay(it.updatedAt, zone)} · ${statusLabel(it.status)}" } ?: "Занятий ещё не было",
            charsRead = read,
            charsTotal = total,
            completedLessons = mine.count { it.status == SessionStatus.COMPLETED },
            chapters = chapterRows(blocks, cursor),
            sessions = mine.take(8).map { session ->
                val chapter = db.fragment(session.fragmentId)?.chapterTitle?.takeIf { it.isNotBlank() } ?: "Фрагмент"
                SessionLine(session.id.value, session.status, session.completedAt ?: session.updatedAt, chapter)
            },
            activeStatus = active?.status,
            activeSubmitted = active?.submission != null,
            coverSeed = book.title.hashCode(),
        )
    }
    return ShelfLoad(
        books = books,
        knowledge = knowledge,
        concepts = concepts,
        observations = observations,
        completedDays = sessions.mapNotNull { it.completedAt?.atZone(zone)?.toLocalDate() },
        observationDays = observations.map { it.createdAt.atZone(zone).toLocalDate() },
    )
}

fun wordCard(
    expression: String,
    sentence: String,
    concepts: List<Concept>,
    knowledge: List<LearnerKnowledgeState>,
    observations: List<LearnerObservation>,
): WordCard {
    val exact = concepts.firstOrNull { it.label == expression }
    val nested = concepts
        .filter { it.label.length >= 2 && expression.contains(it.label) }
        .maxByOrNull { it.label.length }
    val concept = exact ?: nested
    val rows = knowledge.filter { it.conceptId != null && it.conceptId == concept?.id }
    val observation = observations.filter { it.conceptId == concept?.id }.maxByOrNull { it.sequence }
    val gloss = observation?.interpretation?.takeIf { it.isNotBlank() }
        ?: if (concept == null) "В знаниях этого выражения ещё нет." else "Толкование появится после разбора занятия."
    return WordCard(
        expression = expression,
        sentence = sentence,
        gloss = gloss,
        example = observation?.sourceEvidence?.takeIf { it.isNotBlank() },
        status = rows.maxByOrNull { it.lastObservedAt ?: Instant.EPOCH }?.status,
        skill = rows.firstOrNull()?.skillType,
    )
}

fun sentenceAround(text: String, expression: String): String {
    if (expression.isBlank()) return ""
    val parts = text.split(Regex("(?<=[。！？!?\\n])"))
    return parts.firstOrNull { it.contains(expression) }?.trim()?.ifBlank { null }
        ?: text.lineSequence().firstOrNull { it.contains(expression) }?.trim()
        ?: ""
}

fun formatDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.atZone(zone).toLocalDate()
    val month = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    return "${date.dayOfMonth} ${month[date.monthValue - 1]}"
}

fun statusLabel(status: SessionStatus): String = when (status) {
    SessionStatus.CREATED -> "Создано"
    SessionStatus.PREPARING -> "Готовится"
    SessionStatus.INTRO_READY -> "Текст"
    SessionStatus.QUESTIONS_READY -> "Вопросы"
    SessionStatus.AWAITING_ANSWERS -> "Ответы"
    SessionStatus.EVALUATING -> "Разбор"
    SessionStatus.REVIEW_READY -> "Разбор готов"
    SessionStatus.COMPLETED -> "Завершено"
}

fun questionTypeLabel(type: QuestionType): String = when (type) {
    QuestionType.MAIN_IDEA -> "Главная мысль"
    QuestionType.DETAIL_LOOKUP -> "Деталь"
    QuestionType.CONTEXT_VOCABULARY -> "Слово"
    QuestionType.IDIOM -> "Идиома"
    QuestionType.GRAMMAR -> "Грамматика"
    QuestionType.INFERENCE_CHECK -> "Вывод"
    QuestionType.ACTIVE_PRODUCTION -> "Своими словами"
}

fun verdictLabel(verdict: AnswerVerdict): String = when (verdict) {
    AnswerVerdict.CORRECT -> "Верно"
    AnswerVerdict.PARTIAL -> "Частично верно"
    AnswerVerdict.INCORRECT -> "Неверно"
    AnswerVerdict.UNANSWERED -> "Нет ответа"
    AnswerVerdict.NOT_ASSESSABLE -> "Нельзя надёжно оценить"
}

fun knowledgeLabel(status: KnowledgeStatus): String = when (status) {
    KnowledgeStatus.LEARNING -> "Учу"
    KnowledgeStatus.NEEDS_REVIEW -> "Повторить"
    KnowledgeStatus.LIKELY_KNOWN -> "Знаю"
    KnowledgeStatus.STABLE -> "Устойчиво"
}

fun skillLabel(skill: SkillType): String = when (skill) {
    SkillType.VOCABULARY_RECOGNITION -> "Слова"
    SkillType.IDIOM_COMPREHENSION -> "Идиомы"
    SkillType.GRAMMAR_COMPREHENSION -> "Грамматика"
    SkillType.READING_COMPREHENSION -> "Понимание"
    SkillType.CONTEXTUAL_INFERENCE -> "Контекст"
    SkillType.TEXTUAL_ACCURACY -> "Точность"
    SkillType.ACTIVE_PRODUCTION -> "Активное употребление"
}

fun isHan(codePoint: Int): Boolean {
    val script = Character.UnicodeScript.of(codePoint)
    return script == Character.UnicodeScript.HAN
}
