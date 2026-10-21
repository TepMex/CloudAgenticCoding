package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty

@Composable
fun LessonScreen(state: TutorUiState, model: TutorViewModel) {
    val lesson = state.lesson
    val step = lesson?.let { lessonStep(it.status, it.submitted, state.checkingAnswers) } ?: LessonStep.TEXT
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = lesson?.chapter ?: "Занятие",
            onBack = {
                if (step == LessonStep.CHECK) model.hideCheck() else model.leaveLesson()
            },
            trailing = { Text("${stepNumber(step)}/4", color = TutorSecondary, fontWeight = FontWeight.SemiBold) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (lesson == null) {
                Text("Занятие ещё не открыто.", color = TutorMuted)
            } else {
                when (step) {
                    LessonStep.TEXT -> TextPhase(lesson, state, model)
                    LessonStep.QUESTIONS -> QuestionsPhase(lesson, model)
                    LessonStep.CHECK -> CheckPhase(lesson, model)
                    LessonStep.REVIEW -> ReviewPhase(lesson, state, model)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            LessonDock(lesson, step, model)
        }
    }
}

@Composable
private fun LessonDock(lesson: LessonPanel?, step: LessonStep, model: TutorViewModel) {
    if (lesson == null) return
    when (step) {
        LessonStep.TEXT -> when (lesson.status) {
            SessionStatus.INTRO_READY -> PrimaryButton("К вопросам") { model.reveal() }
            else -> PrimaryButton("Подготовить занятие") { model.prepare() }
        }
        LessonStep.QUESTIONS -> PrimaryButton("К ответам", enabled = lesson.questions.isNotEmpty()) { model.showCheck() }
        LessonStep.CHECK -> PrimaryButton("Отправить ответы") { model.submit() }
        LessonStep.REVIEW -> when {
            lesson.reviews.isEmpty() -> PrimaryButton("Повторить разбор") { model.evaluateAgain() }
            lesson.status == SessionStatus.COMPLETED -> PrimaryButton("К книге") { model.leaveLesson() }
            else -> PrimaryButton("Дальше по книге") { model.complete() }
        }
    }
}

@Composable
private fun TextPhase(lesson: LessonPanel, state: TutorUiState, model: TutorViewModel) {
    Text("Сегодня", color = TutorBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    if (lesson.introduction.isNotBlank()) {
        SurfaceCard {
            Text(lesson.introduction, color = TutorText, fontSize = 16.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(12.dp))
    } else {
        Text(
            "Модель ещё не подготовила введение. Кнопка внизу запросит его вместе с вопросами.",
            color = TutorMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
    Text("Выделите непонятное место в тексте. Подсветка не меняет книгу.", color = TutorMuted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    SurfaceCard {
        SelectablePassage(lesson.original, lesson.selections, { start, end -> model.selectRange(start, end) }) { word, sentence ->
            model.openExpression(word, sentence)
        }
    }
    if (state.pendingStart != null && state.pendingEnd != null) {
        SelectionBar(state, lesson.submitted, model)
    }
    if (lesson.selections.isNotEmpty()) {
        SectionLabel("Не понимаю")
        lesson.selections.forEach { mark ->
            SurfaceCard(Modifier.padding(bottom = 8.dp)) {
                Text(mark.quote, color = TutorText, fontWeight = FontWeight.SemiBold)
                if (mark.comment.isNotBlank()) {
                    Text(mark.comment, color = TutorSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                mark.explanation?.let {
                    Text(it, color = TutorText, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "Объяснить сейчас",
                        color = TutorBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { model.explainSaved(mark.id) },
                    )
                    Text(
                        "Убрать",
                        color = TutorMuted,
                        modifier = Modifier.clickable { model.removeSelection(mark.id) },
                    )
                }
            }
        }
    }
    if (lesson.hints.isNotEmpty()) {
        SectionLabel("Подсказки")
        lesson.hints.forEach { hint ->
            Text(hint, color = TutorSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun SelectablePassage(
    text: String,
    marks: List<SelectionUi>,
    onSelect: (Int, Int) -> Unit,
    onWord: (String, String) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var anchor by remember { mutableStateOf<Int?>(null) }
    val annotated = buildAnnotatedString {
        val ranges = marks.mapNotNull { mark ->
            val start = utf16AtCodePoint(text, mark.start)
            val end = utf16AtCodePoint(text, mark.end)
            if (start < end && end <= text.length) start to end else null
        }.sortedBy { it.first }
        var cursor = 0
        ranges.forEach { (start, end) ->
            val safeStart = start.coerceAtLeast(cursor)
            if (safeStart > cursor) append(text.substring(cursor, safeStart))
            if (end > safeStart) {
                withStyle(SpanStyle(background = TutorBlueSoft)) { append(text.substring(safeStart, end)) }
            }
            cursor = maxOf(cursor, end)
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    Text(
        annotated,
        fontSize = 20.sp,
        lineHeight = 32.sp,
        color = TutorText,
        onTextLayout = { layout = it },
        modifier = Modifier
            .pointerInput(text) {
                detectTapGestures { offset ->
                    val index = layout?.getOffsetForPosition(offset) ?: return@detectTapGestures
                    val word = hanRunAt(text, index) ?: return@detectTapGestures
                    onWord(word, sentenceAround(text, word))
                }
            }
            .pointerInput(text) {
            detectDragGestures(
                onDragStart = { offset -> anchor = layout?.getOffsetForPosition(offset) },
                onDragEnd = { anchor = null },
                onDragCancel = { anchor = null },
                onDrag = { change, _ ->
                    val start = anchor ?: return@detectDragGestures
                    val end = layout?.getOffsetForPosition(change.position) ?: return@detectDragGestures
                    change.consume()
                    if (start != end) onSelect(minOf(start, end), maxOf(start, end))
                },
            )
        },
    )
}

@Composable
private fun SelectionBar(state: TutorUiState, submitted: Boolean, model: TutorViewModel) {
    SurfaceCard(Modifier.padding(top = 10.dp)) {
        Text("Не понимаю", fontWeight = FontWeight.SemiBold, color = TutorText)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            listOf("GENERAL", "MEANING", "GRAMMAR", "REFERENCE", "CUSTOM").forEach { type ->
                FilterChip(selectionTypeLabel(type), state.selectionType == type) { model.setSelectionType(type) }
            }
        }
        Spacer(Modifier.height(8.dp))
        AnswerField(state.selectionComment, "Уточнение, если нужно", model::setSelectionComment)
        Spacer(Modifier.height(8.dp))
        if (!submitted) {
            PrimaryButton("Не понимаю") { model.saveSelection(false) }
            Text(
                "Разобрать вместе с ответами",
                color = TutorMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "Объяснить сейчас",
            color = TutorBlue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { model.saveSelection(true) },
        )
    }
}

@Composable
private fun QuestionsPhase(lesson: LessonPanel, model: TutorViewModel) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val types = lesson.questions.map { it.type }.distinct()
    Text("Вопросы по фрагменту", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TutorText)
    Spacer(Modifier.height(8.dp))
    if (types.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip("Все", filter == "all") { filter = "all" }
            types.take(3).forEach { type ->
                FilterChip(questionTypeLabel(type), filter == type.name) { filter = type.name }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    lesson.questions.filter { filter == "all" || it.type.name == filter }.forEach { question ->
        SurfaceCard(Modifier.padding(bottom = 10.dp)) {
            Text(questionTypeLabel(question.type), color = TutorBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(question.prompt, color = TutorText, fontSize = 16.sp)
            question.expression?.let {
                Text(it, color = TutorSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CheckPhase(lesson: LessonPanel, model: TutorViewModel) {
    Text("Твои ответы", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TutorText)
    Spacer(Modifier.height(10.dp))
    lesson.questions.forEach { question ->
        SurfaceCard(Modifier.padding(bottom = 10.dp)) {
            Text(question.prompt, color = TutorSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            AnswerField(
                value = lesson.drafts[question.id].orEmpty(),
                onValue = { model.editDraft(question.id, it) },
            )
        }
    }
    Text("Как дался фрагмент", color = TutorMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DifficultyFaces.forEach { (difficulty, face, label) ->
            val selected = lesson.difficulty == difficulty
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) TutorBlueSoft else TutorCard)
                    .border(1.dp, if (selected) TutorBlue else TutorLine, RoundedCornerShape(16.dp))
                    .clickable { model.setDifficulty(difficulty) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(face, fontSize = 22.sp)
                Text(label, fontSize = 12.sp, color = TutorText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    AnswerField(lesson.unclear, "Что осталось неясным", model::setUnclear)
    Spacer(Modifier.height(8.dp))
    Text(
        if (lesson.wantsMore) "Нужно подробнее: да" else "Нужно подробнее",
        color = if (lesson.wantsMore) TutorBlue else TutorText,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (lesson.wantsMore) TutorBlueSoft else TutorCard)
            .border(1.dp, if (lesson.wantsMore) TutorBlue else TutorLine, RoundedCornerShape(99.dp))
            .clickable { model.setWantsMore(!lesson.wantsMore) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
    Spacer(Modifier.height(8.dp))
    AnswerField(lesson.comment, "Комментарий, если хочется", model::setComment)
}

@Composable
private fun ReviewPhase(lesson: LessonPanel, state: TutorUiState, model: TutorViewModel) {
    Text("Разбор", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TutorText)
    Spacer(Modifier.height(8.dp))
    if (lesson.reviews.isEmpty()) {
        Text("Ответы сохранены. Разбор ещё не получен — его можно запросить снова.", color = TutorMuted)
        return
    }
    val correct = lesson.reviews.count { it.verdict == AnswerVerdict.CORRECT }
    Text("$correct из ${lesson.reviews.size} без ошибок", color = TutorSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    lesson.reviews.forEach { review ->
        val question = lesson.questions.firstOrNull { it.id == review.questionId }
        val (fg, bg) = verdictColors(review.verdict)
        SurfaceCard(Modifier.padding(bottom = 10.dp)) {
            Pill(verdictLabel(review.verdict), fg, bg)
            Spacer(Modifier.height(8.dp))
            Text(question?.prompt ?: "Вопрос", fontWeight = FontWeight.SemiBold, color = TutorText)
            Spacer(Modifier.height(6.dp))
            Text(review.explanation, color = TutorText, fontSize = 15.sp)
            review.nuance?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = TutorSecondary, fontSize = 14.sp)
            }
            review.productionFeedback?.let {
                Spacer(Modifier.height(4.dp))
                Text("Формулировка: $it", color = TutorAmber, fontSize = 14.sp)
            }
            if (review.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(review.notes, color = TutorMuted, fontSize = 13.sp)
            }
        }
    }
    if (lesson.selectionReviews.isNotEmpty()) {
        SectionLabel("Выделенные места")
        lesson.selectionReviews.forEach { review ->
            SurfaceCard(Modifier.padding(bottom = 8.dp)) {
                Text(review.quote, fontWeight = FontWeight.SemiBold, color = TutorText)
                Spacer(Modifier.height(6.dp))
                Text(review.explanation, color = TutorText, fontSize = 15.sp)
                review.nuance?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = TutorSecondary, fontSize = 14.sp)
                }
            }
        }
    }
    lesson.feedback?.let {
        SectionLabel("К самоотчёту")
        Text(it, color = TutorText)
    }
    val expressions = lesson.questions.mapNotNull { it.expression?.takeIf { value -> value.isNotBlank() } }.distinct()
    if (expressions.isNotEmpty()) {
        SectionLabel("Выражения из вопросов")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            expressions.forEach { expression ->
                Text(
                    expression,
                    color = TutorText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(TutorCard)
                        .border(1.dp, TutorLine, RoundedCornerShape(99.dp))
                        .clickable { model.openExpression(expression, sentenceAround(lesson.original, expression)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (lesson.recommendations.isNotEmpty()) {
        SectionLabel("Дальше")
        lesson.recommendations.forEach { Text(it, color = TutorText, modifier = Modifier.padding(bottom = 4.dp)) }
    }
    SectionLabel("Спросить о тексте")
    Text("Новая пометка после отправки не меняет уже отправленный разбор.", color = TutorMuted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    SurfaceCard {
        SelectablePassage(lesson.original, lesson.selections, { start, end -> model.selectRange(start, end) }) { word, sentence ->
            model.openExpression(word, sentence)
        }
    }
    if (state.pendingStart != null && state.pendingEnd != null) {
        SelectionBar(state, lesson.submitted, model)
    }
}

@Composable
private fun AnswerField(value: String, hint: String = "Ответ", onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        textStyle = androidx.compose.ui.text.TextStyle(color = TutorText, fontSize = 16.sp),
        cursorBrush = SolidColor(TutorBlue),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TutorBg)
            .padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = TutorMuted)
                inner()
            }
        },
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.White else TutorText,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) TutorBlue else TutorCard)
            .border(1.dp, if (selected) TutorBlue else TutorLine, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun verdictColors(verdict: AnswerVerdict): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (verdict) {
    AnswerVerdict.CORRECT -> TutorGreen to TutorGreenSoft
    AnswerVerdict.PARTIAL -> TutorAmber to TutorAmberSoft
    AnswerVerdict.INCORRECT -> TutorRed to TutorRedSoft
    AnswerVerdict.UNANSWERED -> TutorSecondary to TutorTrack
    AnswerVerdict.NOT_ASSESSABLE -> TutorMuted to TutorTrack
}

private fun selectionTypeLabel(type: String) = when (type) {
    "MEANING" -> "Смысл"
    "GRAMMAR" -> "Грамматика"
    "REFERENCE" -> "Отсылка"
    "CUSTOM" -> "Свой вопрос"
    else -> "Общее"
}

private fun utf16AtCodePoint(text: String, codePoints: Int): Int {
    var seen = 0
    var index = 0
    while (index < text.length && seen < codePoints) {
        index += Character.charCount(text.codePointAt(index))
        seen += 1
    }
    return index
}

private fun hanRunAt(text: String, utf16: Int): String? {
    if (text.isEmpty()) return null
    val index = utf16.coerceIn(0, text.length - 1)
    val snapped = if (Character.isLowSurrogate(text[index]) && index > 0) index - 1 else index
    if (!isHan(text.codePointAt(snapped))) return null
    var start = snapped
    while (start > 0) {
        val previous = start - 1
        val at = if (Character.isLowSurrogate(text[previous]) && previous > 0) previous - 1 else previous
        if (!isHan(text.codePointAt(at))) break
        start = at
    }
    var end = start
    while (end < text.length && isHan(text.codePointAt(end))) {
        end += Character.charCount(text.codePointAt(end))
    }
    return text.substring(start, end).takeIf { it.isNotEmpty() }
}

private val DifficultyFaces = listOf(
    Triple(SubjectiveDifficulty.TOO_EASY, "😌", "Легко"),
    Triple(SubjectiveDifficulty.SUITABLE, "🙂", "Нормально"),
    Triple(SubjectiveDifficulty.TOO_HARD, "😣", "Сложно"),
)
