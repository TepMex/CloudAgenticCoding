package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
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
                    LessonStep.TEXT -> TextPhase(lesson, state.selectedWord, model)
                    LessonStep.QUESTIONS -> QuestionsPhase(lesson, model)
                    LessonStep.CHECK -> CheckPhase(lesson, model)
                    LessonStep.REVIEW -> ReviewPhase(lesson, model)
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
private fun TextPhase(lesson: LessonPanel, selected: String?, model: TutorViewModel) {
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
    SurfaceCard {
        HanText(lesson.original, selected) { word, sentence -> model.openExpression(word, sentence) }
    }
    if (lesson.hints.isNotEmpty()) {
        SectionLabel("Подсказки")
        lesson.hints.forEach { hint ->
            Text(hint, color = TutorSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
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
    AnswerField(lesson.comment, "Комментарий, если хочется", model::setComment)
}

@Composable
private fun ReviewPhase(lesson: LessonPanel, model: TutorViewModel) {
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
            if (review.explanation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(review.explanation, color = TutorSecondary, fontSize = 14.sp)
            }
            if (review.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(review.notes, color = TutorMuted, fontSize = 13.sp)
            }
        }
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
}

@Composable
private fun HanText(text: String, selected: String?, onWord: (String, String) -> Unit) {
    val annotated = buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isHan(codePoint)) {
                val start = index
                var end = index
                while (end < text.length && isHan(text.codePointAt(end))) {
                    end += Character.charCount(text.codePointAt(end))
                }
                val word = text.substring(start, end)
                withLink(
                    LinkAnnotation.Clickable(
                        tag = word,
                        styles = TextLinkStyles(
                            SpanStyle(
                                color = TutorText,
                                background = if (word == selected) TutorBlueSoft else Color.Transparent,
                            ),
                        ),
                    ) { _ -> onWord(word, sentenceAround(text, word)) },
                ) { append(word) }
                index = end
            } else {
                val count = Character.charCount(codePoint)
                append(text.substring(index, index + count))
                index += count
            }
        }
    }
    Text(annotated, fontSize = 20.sp, lineHeight = 32.sp, color = TutorText)
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
}

private val DifficultyFaces = listOf(
    Triple(SubjectiveDifficulty.TOO_EASY, "😌", "Легко"),
    Triple(SubjectiveDifficulty.SUITABLE, "🙂", "Нормально"),
    Triple(SubjectiveDifficulty.TOO_HARD, "😣", "Сложно"),
)
