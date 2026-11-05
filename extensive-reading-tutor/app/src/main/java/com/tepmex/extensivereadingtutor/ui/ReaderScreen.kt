package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.tepmex.extensivereadingtutor.books.codePointRangeToUtf16
import com.tepmex.extensivereadingtutor.books.normalizedUtf16Selection
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.pedagogy.LessonPolicyConfig
import kotlinx.coroutines.delay

@Composable
fun ReaderScreen(
    reader: ReaderUi,
    onBack: () -> Unit,
    onSettle: (Int) -> Unit,
    onRequest: () -> Unit,
    actions: LessonActions,
    pendingStart: Int? = null,
    pendingEnd: Int? = null,
    selectionType: String = "GENERAL",
    selectionComment: String = "",
) {
    if (reader.pages.isEmpty()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ReaderTopBar(title = reader.title, onBack = onBack)
            Text("В книге нет текста.", color = TutorMuted, modifier = Modifier.padding(24.dp))
        }
        return
    }
    val pager = rememberPagerState(initialPage = reader.index.coerceIn(0, reader.pages.lastIndex)) { reader.pages.size }
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(reader.index, reader.pages.size) {
        val target = reader.index.coerceIn(0, reader.pages.lastIndex)
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager.settledPage) { onSettle(pager.settledPage) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ReaderTopBar(title = reader.title, onBack = onBack)
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).navigationBarsPadding(),
            userScrollEnabled = !pulling,
            key = { reader.pages[it].place },
        ) { pageIndex ->
            val page = reader.pages[pageIndex]
            ReaderPage(
                page = page,
                showPinyin = reader.showPinyin,
                analyzing = reader.analyzing && pageIndex == reader.index,
                onPulling = { if (pageIndex == pager.currentPage) pulling = it },
                onRequest = onRequest,
                actions = actions,
                pendingStart = if (pageIndex == pager.currentPage) pendingStart else null,
                pendingEnd = if (pageIndex == pager.currentPage) pendingEnd else null,
                selectionType = selectionType,
                selectionComment = selectionComment,
                selecting = pageIndex == pager.currentPage,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", fontSize = 28.sp, color = TutorText)
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = TutorMuted,
        )
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
fun ReaderPage(
    page: ReaderPageUi,
    showPinyin: Boolean,
    analyzing: Boolean,
    onPulling: (Boolean) -> Unit,
    onRequest: () -> Unit,
    actions: LessonActions,
    pendingStart: Int? = null,
    pendingEnd: Int? = null,
    selectionType: String = "GENERAL",
    selectionComment: String = "",
    selecting: Boolean = false,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    var pullPx by remember(page.place) { mutableFloatStateOf(0f) }
    var armed by remember(page.place) { mutableStateOf(false) }
    val canRequest = !analyzing && page.lesson.acceptsIteration()
    val gate = remember(page.place) { PagePull() }
    gate.canRequest = canRequest
    gate.fire = onRequest
    gate.onPulling = onPulling
    val pulled = pullPx >= threshold
    LaunchedEffect(pulled, page.place) {
        if (!pulled) {
            armed = false
            return@LaunchedEffect
        }
        delay(PULL_HOLD_MILLIS)
        armed = true
    }
    LaunchedEffect(pullPx > 0f) { gate.onPulling(pullPx > 0f) }
    val connection = remember(page.place, threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !gate.canRequest) return Offset.Zero
                if (available.y < 0f) {
                    pullPx += -available.y
                    return Offset(0f, available.y)
                }
                if (available.y > 0f && pullPx > 0f) {
                    val back = minOf(pullPx, available.y)
                    pullPx -= back
                    return Offset(0f, back)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val fire = pullUpRelease(atEnd = true, pullPx = pullPx, armed = armed, thresholdPx = threshold) && gate.canRequest
                pullPx = 0f
                armed = false
                gate.onPulling(false)
                if (fire) gate.fire()
                return Velocity.Zero
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(connection)
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp),
    ) {
        if (page.chapter.isNotBlank()) {
            Text(page.chapter, color = TutorMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        val pending = if (selecting && pendingStart != null && pendingEnd != null) pendingStart to pendingEnd else null
        if (showPinyin) {
            RubyPassage(
                text = page.text,
                marks = page.lesson?.selections.orEmpty(),
                pending = pending,
                onWord = { actions.openExpression(it, page.text) },
                onSelect = actions::selectRange,
            )
        } else {
            PlainPassage(
                text = page.text,
                marks = page.lesson?.selections.orEmpty(),
                pending = pending,
                onWord = { actions.openExpression(it, page.text) },
                onSelect = actions::selectRange,
            )
        }
        if (selecting && pending != null) {
            SelectionOffer(
                text = page.text,
                start = pending.first,
                end = pending.second,
                type = selectionType,
                comment = selectionComment,
                locked = page.lesson?.submitted == true,
                actions = actions,
            )
        }
        page.lesson?.selections?.takeIf { it.isNotEmpty() }?.let { marks ->
            SavedMarks(marks, page.lesson?.submitted == true, actions)
        }
        page.lesson?.let { lesson ->
            val sections = visibleLessonSections(lesson.status, lesson.submitted)
            if (LessonFeedSection.INTRODUCTION in sections) IntroductionBlock(lesson)
            if (LessonFeedSection.QUESTIONS in sections) QuestionsBlock(lesson, actions)
            if (LessonFeedSection.REVIEW in sections) ReviewBlock(lesson, actions)
        }
        if (analyzing) {
            Spacer(Modifier.height(16.dp))
            Text("Разбираю фрагмент…", color = TutorMuted, fontSize = 14.sp)
        }
        if (pullPx > 8f) {
            Spacer(Modifier.height(with(density) { pullPx.toDp() }))
            Text(
                if (armed) "Отпустите — разбор" else "Удержите",
                color = TutorMuted,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RubyPassage(
    text: String,
    marks: List<SelectionUi>,
    pending: Pair<Int, Int>?,
    onWord: (String) -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    var utf16 = 0
    text.split('\n').forEachIndexed { lineIndex, line ->
        if (lineIndex > 0) utf16 += 1
        FlowRow(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            var index = 0
            while (index < line.length) {
                val cp = line.codePointAt(index)
                val count = Character.charCount(cp)
                val glyph = String(Character.toChars(cp))
                val han = Hanzi.isHanzi(cp)
                val reading = if (han) PinyinSyllable.of(glyph) else ""
                val start = utf16 + index
                val end = start + count
                val marked = marks.any { mark -> rangeOverlaps(text, mark.start, mark.end, start, end) } ||
                    (pending != null && start < pending.second && end > pending.first)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (marked) TutorAmberSoft else Color.Transparent)
                        .pointerInput(text, start, pending) {
                            detectTapGestures(
                                onTap = { if (han) onWord(glyph) },
                                onLongPress = {
                                    selectionFromPress(text, pending, start)?.let { (from, to) -> onSelect(from, to) }
                                },
                            )
                        },
                ) {
                    Text(
                        if (han && reading.isNotEmpty()) reading else " ",
                        color = TutorMuted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(glyph, color = TutorText, fontSize = 22.sp, lineHeight = 28.sp)
                }
                index += count
            }
        }
        utf16 += line.length
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IntroductionBlock(lesson: LessonPanel) {
    if (lesson.introduction.isNotBlank()) {
        Spacer(Modifier.height(22.dp))
        Text(lesson.introduction, color = TutorText, fontSize = 17.sp, lineHeight = 26.sp)
    }
    lesson.hints.forEach { hint ->
        Spacer(Modifier.height(8.dp))
        Text(hint, color = TutorSecondary, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun QuestionsBlock(lesson: LessonPanel, actions: LessonActions) {
    lesson.questions.forEach { question ->
        Spacer(Modifier.height(18.dp))
        Text(questionTypeLabel(question.type), color = TutorBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(question.prompt, color = TutorText, fontSize = 16.sp, lineHeight = 24.sp)
        question.expression?.let {
            Text(
                it,
                color = TutorSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp).clickable { actions.openExpression(it, lesson.original) },
            )
        }
        Spacer(Modifier.height(8.dp))
        AnswerField(
            value = lesson.drafts[question.id].orEmpty(),
            readOnly = lesson.submitted,
            onValue = { actions.editDraft(question.id, it) },
        )
    }
    Spacer(Modifier.height(14.dp))
    if (!lesson.submitted) {
        Text(
            if (lesson.wantsMore) "Подробнее включено" else "Нужно подробнее",
            color = if (lesson.wantsMore) TutorBlue else TutorSecondary,
            fontSize = 15.sp,
            modifier = Modifier.clickable { actions.setWantsMore(!lesson.wantsMore) },
        )
        Spacer(Modifier.height(8.dp))
    }
    AnswerField(lesson.unclear, "Что осталось неясным", lesson.submitted, actions::setUnclear)
    Spacer(Modifier.height(8.dp))
    AnswerField(lesson.comment, "Обратная связь", lesson.submitted, actions::setComment)
    if (!lesson.submitted) {
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Отправить ответы", enabled = lesson.questions.isNotEmpty()) { actions.submit() }
    }
}

@Composable
private fun ReviewBlock(lesson: LessonPanel, actions: LessonActions) {
    Spacer(Modifier.height(22.dp))
    Text("Разбор", color = TutorBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    if (lesson.reviews.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Ответы сохранены. Разбор ещё не получен.", color = TutorMuted, fontSize = 15.sp)
        return
    }
    lesson.reviews.forEach { review ->
        val question = lesson.questions.firstOrNull { it.id == review.questionId }
        val (fg, bg) = verdictColors(review.verdict)
        Spacer(Modifier.height(12.dp))
        Pill(verdictLabel(review.verdict), fg, bg)
        Spacer(Modifier.height(6.dp))
        Text(question?.prompt ?: "Вопрос", fontWeight = FontWeight.SemiBold, color = TutorText)
        val answer = lesson.drafts[review.questionId].orEmpty()
        if (answer.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(answer, color = TutorSecondary, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(review.explanation, color = TutorText, fontSize = 16.sp, lineHeight = 24.sp)
        review.nuance?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = TutorSecondary, fontSize = 15.sp)
        }
        review.productionFeedback?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = TutorAmber, fontSize = 15.sp)
        }
    }
    lesson.feedback?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = TutorText, fontSize = 16.sp, lineHeight = 24.sp)
    }
    lesson.selectionReviews.forEach { review ->
        Spacer(Modifier.height(12.dp))
        Text(review.quote, fontWeight = FontWeight.SemiBold, color = TutorText)
        Spacer(Modifier.height(4.dp))
        Text(review.explanation, color = TutorText, fontSize = 16.sp, lineHeight = 24.sp)
    }
    lesson.recommendations.forEach { note ->
        Spacer(Modifier.height(8.dp))
        Text(note, color = TutorSecondary, fontSize = 15.sp)
    }
    val expressions = lesson.questions.mapNotNull { it.expression?.takeIf(String::isNotBlank) }.distinct()
    if (expressions.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            expressions.forEach { expression ->
                Text(
                    expression,
                    color = TutorText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(TutorCard)
                        .border(1.dp, TutorLine, RoundedCornerShape(99.dp))
                        .clickable { actions.openExpression(expression, lesson.original) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AnswerField(
    value: String,
    hint: String = "Ответ",
    readOnly: Boolean = false,
    onValue: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = { if (!readOnly) onValue(it) },
        readOnly = readOnly,
        textStyle = TextStyle(color = TutorText, fontSize = 16.sp),
        cursorBrush = SolidColor(TutorBlue),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TutorCard)
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
private fun verdictColors(verdict: AnswerVerdict): Pair<Color, Color> = when (verdict) {
    AnswerVerdict.CORRECT -> TutorGreen to TutorGreenSoft
    AnswerVerdict.PARTIAL -> TutorAmber to TutorAmberSoft
    AnswerVerdict.INCORRECT -> TutorRed to TutorRedSoft
    AnswerVerdict.UNANSWERED -> TutorSecondary to TutorTrack
    AnswerVerdict.NOT_ASSESSABLE -> TutorMuted to TutorTrack
}

@Composable
private fun PlainPassage(
    text: String,
    marks: List<SelectionUi>,
    pending: Pair<Int, Int>?,
    onWord: (String) -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    val saved = TutorBlueSoft
    val active = TutorAmberSoft
    val annotated = remember(text, marks, pending, saved, active) {
        highlightedPassage(text, marks, pending, saved, active)
    }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        color = TutorText,
        fontSize = 20.sp,
        lineHeight = 34.sp,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(text, pending) {
            detectTapGestures(
                onTap = { offset ->
                    val index = layout?.getOffsetForPosition(offset) ?: return@detectTapGestures
                    val word = PinyinSyllable.hanRunAt(text, index) ?: return@detectTapGestures
                    onWord(word)
                },
                onLongPress = { offset ->
                    val index = layout?.getOffsetForPosition(offset) ?: return@detectTapGestures
                    val range = selectionFromPress(text, pending, index) ?: return@detectTapGestures
                    onSelect(range.first, range.second)
                },
            )
        },
    )
}

@Composable
private fun SelectionOffer(
    text: String,
    start: Int,
    end: Int,
    type: String,
    comment: String,
    locked: Boolean,
    actions: LessonActions,
) {
    val range = normalizedUtf16Selection(text, start, end) ?: return
    val quote = text.substring(range.first, range.second)
    val points = quote.codePointCount(0, quote.length)
    val limit = LessonPolicyConfig().maxSelectionCodePoints
    Spacer(Modifier.height(16.dp))
    Text(quote.replace('\n', ' '), color = TutorText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    if (points > limit) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Выделение $points знаков длиннее лимита $limit. Сократите диапазон. Текст не обрезан.",
            color = TutorRed,
            fontSize = 14.sp,
        )
    } else {
        Spacer(Modifier.height(4.dp))
        Text("Удержите другой край, чтобы расширить.", color = TutorMuted, fontSize = 13.sp)
    }
    if (!locked) {
        Spacer(Modifier.height(8.dp))
        TypeChips(type, actions::setSelectionType)
        Spacer(Modifier.height(8.dp))
        AnswerField(comment, "Уточнение, если нужно", false, actions::setSelectionComment)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Не понимаю", enabled = points <= limit) { actions.saveSelection(false) }
        Text(
            "Разобрать вместе с ответами",
            color = TutorMuted,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
    Text(
        "Отмена",
        color = TutorMuted,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clickable { actions.cancelSelection() }.padding(vertical = 6.dp),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Объяснить сейчас",
        color = TutorBlue,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = points <= limit) { actions.saveSelection(true) }
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SavedMarks(marks: List<SelectionUi>, submitted: Boolean, actions: LessonActions) {
    marks.forEach { mark ->
        Spacer(Modifier.height(12.dp))
        Text(mark.quote.replace('\n', ' '), fontWeight = FontWeight.SemiBold, color = TutorText)
        Text(selectionTypeLabel(mark.type), color = TutorMuted, fontSize = 12.sp)
        if (mark.comment.isNotBlank()) {
            Text(mark.comment, color = TutorSecondary, fontSize = 15.sp)
        }
        mark.explanation?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = TutorText, fontSize = 15.sp, lineHeight = 22.sp)
        }
        if (!submitted) {
            Spacer(Modifier.height(6.dp))
            TypeChips(mark.type) { type ->
                actions.reviseSelection(mark.id, type, mark.comment)
            }
            var draft by remember(mark.id, mark.revision) { mutableStateOf(mark.comment) }
            Spacer(Modifier.height(6.dp))
            AnswerField(draft, "Уточнение", false) { draft = it }
            if (draft != mark.comment) {
                Text(
                    "Сохранить уточнение",
                    color = TutorBlue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp).clickable {
                        actions.reviseSelection(mark.id, mark.type, draft)
                    },
                )
            }
            Text(
                "Удалить",
                color = TutorRed,
                modifier = Modifier.padding(top = 4.dp).clickable { actions.removeSelection(mark.id) },
            )
        }
        if (mark.explanation == null) {
            Text(
                "Объяснить сейчас",
                color = TutorBlue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp).clickable { actions.explainSaved(mark.id) },
            )
        }
    }
}

@Composable
private fun TypeChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectionTypeOptions.forEach { (id, label) ->
            val on = id == selected
            Text(
                label,
                color = if (on) Color.White else TutorText,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (on) TutorBlue else TutorTrack)
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

internal fun selectionFromPress(text: String, pending: Pair<Int, Int>?, fingerUtf16: Int): Pair<Int, Int>? {
    val touched = normalizedUtf16Selection(text, fingerUtf16, fingerUtf16) ?: return null
    if (pending == null) return touched
    val from = minOf(pending.first, touched.first)
    val to = maxOf(pending.second, touched.second)
    return normalizedUtf16Selection(text, from, to)
}

private fun highlightedPassage(
    text: String,
    marks: List<SelectionUi>,
    pending: Pair<Int, Int>?,
    saved: Color,
    active: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    marks.forEach { mark ->
        val range = codePointRangeToUtf16(text, mark.start, mark.end) ?: return@forEach
        addStyle(SpanStyle(background = saved), range.first, range.second)
    }
    if (pending != null) {
        val range = normalizedUtf16Selection(text, pending.first, pending.second) ?: return@buildAnnotatedString
        addStyle(SpanStyle(background = active), range.first, range.second)
    }
}

private fun rangeOverlaps(text: String, startCodePoint: Int, endCodePoint: Int, utf16Start: Int, utf16End: Int): Boolean {
    val range = codePointRangeToUtf16(text, startCodePoint, endCodePoint) ?: return false
    return utf16Start < range.second && utf16End > range.first
}

private class PagePull {
    var canRequest: Boolean = true
    var fire: () -> Unit = {}
    var onPulling: (Boolean) -> Unit = {}
}

interface LessonActions {
    fun leaveLesson() {}
    fun prepare() {}
    fun reveal() {}
    fun submit() {}
    fun evaluateAgain() {}
    fun complete() {}
    fun selectRange(startUtf16: Int, endUtf16: Int) {}
    fun cancelSelection() {}
    fun openExpression(expression: String, sentence: String) {}
    fun explainSaved(id: String) {}
    fun removeSelection(id: String) {}
    fun reviseSelection(id: String, type: String, comment: String) {}
    fun setSelectionType(type: String) {}
    fun setSelectionComment(comment: String) {}
    fun saveSelection(now: Boolean) {}
    fun editDraft(questionId: String, text: String) {}
    fun setDifficulty(difficulty: SubjectiveDifficulty) {}
    fun setUnclear(text: String) {}
    fun setWantsMore(value: Boolean) {}
    fun setComment(comment: String) {}
}
