package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.SkillType
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun StudyScreen(state: TutorUiState, onOpenBook: (String) -> Unit, onContinue: (String) -> Unit, onWord: (String) -> Unit) {
    val current = state.shelf.firstOrNull { !it.done } ?: state.shelf.firstOrNull()
    val due = state.knowledge.filter { it.status == KnowledgeStatus.NEEDS_REVIEW }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(large = "Учёба")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (current != null) {
                val status = current.activeStatus?.let { statusLabel(it) }
                SurfaceCard {
                    Text("Сейчас", color = TutorBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(current.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TutorText)
                    Text(
                        buildString {
                            append(current.place)
                            if (status != null) append(" · $status")
                        },
                        color = TutorMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(if (current.done) "Открыть книгу" else "Продолжить занятие") {
                        if (current.done) onOpenBook(current.id) else onContinue(current.id)
                    }
                }
            } else {
                Text("Импортируйте EPUB на полке «Книги», и здесь появится текущее занятие.", color = TutorMuted)
            }
            SectionLabel("Нужно повторить")
            if (due.isEmpty()) {
                Text("Нет выражений со статусом «Повторить».", color = TutorMuted, fontSize = 14.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    due.take(12).forEach { row ->
                        val label = knowledgeTitle(row, state.concepts)
                        Text(
                            label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(TutorAmberSoft)
                                .clickable { onWord(label) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = TutorAmber,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            SectionLabel("Книги в работе")
            state.shelf.filter { !it.done }.forEach { book ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TutorCard)
                        .clickable { onOpenBook(book.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(TutorBlue))
                    Column {
                        Text(book.title, fontWeight = FontWeight.SemiBold, color = TutorText)
                        Text("${book.place} · ${book.percent}%", color = TutorMuted, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun KnowledgeScreen(state: TutorUiState, onWord: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("words") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val words = state.knowledge.filter { it.skillType != SkillType.GRAMMAR_COMPREHENSION }
    val grammar = state.knowledge.filter { it.skillType == SkillType.GRAMMAR_COMPREHENSION }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(large = "Мои знания")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segmented(
                options = listOf("words" to "Слова", "grammar" to "Грамматика", "skills" to "Навыки"),
                value = tab,
                onChange = { tab = it },
            )
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(color = TutorText, fontSize = 16.sp),
                cursorBrush = SolidColor(TutorBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TutorCard)
                    .border(1.dp, TutorLine, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Поиск по словам и выражениям", color = TutorMuted)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            when (tab) {
                "grammar" -> KnowledgeList(grammar.filter { matches(knowledgeTitle(it, state.concepts), needle) }, state.concepts, onWord)
                "skills" -> SkillList(state.knowledge)
                else -> {
                    val counts = words.groupingBy { it.status }.eachCount()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(
                            "all" to "Все (${words.size})",
                            "review" to "Повторить (${counts[KnowledgeStatus.NEEDS_REVIEW] ?: 0})",
                            "learning" to "Учу (${counts[KnowledgeStatus.LEARNING] ?: 0})",
                            "known" to "Знаю (${(counts[KnowledgeStatus.LIKELY_KNOWN] ?: 0) + (counts[KnowledgeStatus.STABLE] ?: 0)})",
                        ).forEach { (id, label) ->
                            val on = filter == id
                            Text(
                                label,
                                color = if (on) Color.White else TutorText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (on) TutorBlue else TutorCard)
                                    .border(1.dp, if (on) TutorBlue else TutorLine, RoundedCornerShape(99.dp))
                                    .clickable { filter = id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    KnowledgeList(
                        words.filter { row ->
                            val statusOk = when (filter) {
                                "review" -> row.status == KnowledgeStatus.NEEDS_REVIEW
                                "learning" -> row.status == KnowledgeStatus.LEARNING
                                "known" -> row.status == KnowledgeStatus.LIKELY_KNOWN || row.status == KnowledgeStatus.STABLE
                                else -> true
                            }
                            statusOk && matches(knowledgeTitle(row, state.concepts), needle)
                        },
                        state.concepts,
                        onWord,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun KnowledgeList(rows: List<LearnerKnowledgeState>, concepts: List<Concept>, onWord: (String) -> Unit) {
    if (rows.isEmpty()) {
        Text("Ничего не нашлось.", color = TutorMuted)
        return
    }
    rows.forEach { row ->
        val title = knowledgeTitle(row, concepts)
        val (fg, bg) = knowledgeColors(row.status)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TutorCard)
                .border(1.dp, TutorLine, RoundedCornerShape(18.dp))
                .clickable { onWord(title) }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TutorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Pill(knowledgeLabel(row.status), fg, bg)
            }
            Text("${skillLabel(row.skillType)} · +${row.successCount} / −${row.errorCount}", color = TutorMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SkillList(rows: List<LearnerKnowledgeState>) {
    if (rows.isEmpty()) {
        Text("Навыки появятся после разобранных занятий.", color = TutorMuted)
        return
    }
    rows.groupBy { it.skillType }.forEach { (skill, group) ->
        SurfaceCard(Modifier.padding(bottom = 8.dp)) {
            Text(skillLabel(skill), fontWeight = FontWeight.SemiBold, color = TutorText)
            Text("${group.size} записей", color = TutorMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ProfileScreen(state: TutorUiState, onSettings: () -> Unit) {
    val reading = state.shelf.count { !it.done }
    val words = state.knowledge.mapNotNull { it.conceptId }.distinct().size
    val chars = state.shelf.sumOf { it.charsRead }
    val level = state.preferences?.selfAssessedLevel ?: "HSK 4"
    val language = languageLabel(state.preferences?.explanationLanguage ?: "ru")
    val model = state.modelName.ifBlank { "модель не задана" }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Профиль", modifier = Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TutorText)
            Text("Изменить", color = TutorBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSettings))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("Мой прогресс")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ProfileStat(reading.toString(), "книги")
                ProfileStat(words.toString(), "слов")
                ProfileStat(chars.toString(), "знаков")
                ProfileStat(state.observations.size.toString(), "наблюдений")
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TutorCard)
                    .clickable(onClick = onSettings)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(TutorBlue))
                Column {
                    Text("Настройки ИИ и обучения", fontWeight = FontWeight.SemiBold, color = TutorText)
                    Text("$model · $level · $language", color = TutorMuted, fontSize = 13.sp)
                }
            }
            SectionLabel("Активность")
            SurfaceCard {
                Heatmap(state.completedDays.toSet(), state.observationDays.toSet())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(TutorBlue, "Занятие")
                    Legend(Color(0xFFB9D0FF), "Наблюдение")
                    Legend(TutorTrack, "Нет")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Heatmap(completed: Set<LocalDate>, observed: Set<LocalDate>) {
    val today = LocalDate.now()
    val start = today.minusDays((today.dayOfWeek.value - 1 + 19 * 7).toLong())
    val weeks = 20
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            repeat(weeks) { week ->
                val day = start.plusDays(week * 7L)
                val label = if (week == 0 || day.dayOfMonth <= 7) {
                    day.month.getDisplayName(JavaTextStyle.SHORT_STANDALONE, Locale.forLanguageTag("ru")).replace(".", "")
                } else {
                    ""
                }
                Text(label, modifier = Modifier.size(width = 14.dp, height = 16.dp), fontSize = 8.sp, color = TutorMuted, maxLines = 1)
            }
        }
        Row {
            repeat(weeks) { week ->
                Column {
                    repeat(7) { dayIndex ->
                        val date = start.plusDays(week * 7L + dayIndex)
                        val color = when {
                            date.isAfter(today) -> Color.Transparent
                            date in completed -> TutorBlue
                            date in observed -> Color(0xFFB9D0FF)
                            else -> TutorTrack
                        }
                        Box(Modifier.padding(1.dp).size(12.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = TutorSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TutorText)
        Text(label, color = TutorMuted, fontSize = 12.sp)
    }
}

private fun matches(title: String, needle: String): Boolean = needle.isEmpty() || title.contains(needle, ignoreCase = true)

private fun knowledgeTitle(row: LearnerKnowledgeState, concepts: List<Concept>): String =
    concepts.firstOrNull { it.id == row.conceptId }?.label
        ?: row.diagnosticKey
        ?: skillLabel(row.skillType)

private fun knowledgeColors(status: KnowledgeStatus): Pair<Color, Color> = when (status) {
    KnowledgeStatus.LEARNING -> TutorBlue to TutorBlueSoft
    KnowledgeStatus.NEEDS_REVIEW -> TutorAmber to TutorAmberSoft
    KnowledgeStatus.LIKELY_KNOWN -> TutorGreen to TutorGreenSoft
    KnowledgeStatus.STABLE -> TutorGreen to TutorGreenSoft
}

private fun languageLabel(code: String): String = when (code) {
    "en" -> "English"
    "zh" -> "中文"
    else -> "Русский"
}
