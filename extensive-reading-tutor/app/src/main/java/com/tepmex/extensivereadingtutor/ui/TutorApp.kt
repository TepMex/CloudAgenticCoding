package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TutorApp(model: TutorViewModel, onImport: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val showTabs = state.route is Route.Tabs
    TutorTheme {
        Box(Modifier.fillMaxSize().background(TutorBg).statusBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (val route = state.route) {
                        Route.Tabs -> when (state.tab) {
                            Tab.BOOKS -> LibraryScreen(
                                state,
                                model::openBook,
                                onImport,
                                model::askRemove,
                                model::cancelRemove,
                                model::removeBook,
                            )
                            Tab.STUDY -> StudyScreen(state, model::openBook) { model.openExpression(it, "") }
                            Tab.KNOWLEDGE -> KnowledgeScreen(state) { model.openExpression(it, "") }
                            Tab.PROFILE -> ProfileScreen(state, model::openSettings)
                        }
                        is Route.Reader -> {
                            val reader = state.reader?.takeIf { it.bookId == route.bookId }
                            if (reader == null) {
                                Column(Modifier.padding(16.dp)) {
                                    ScreenHeader(title = "Книга", onBack = model::leaveLesson)
                                    Text("Книга ещё не открыта.", color = TutorMuted)
                                }
                            } else {
                                ReaderScreen(
                                    reader = reader,
                                    onBack = model::leaveLesson,
                                    onSettle = model::settlePage,
                                    onRequest = model::requestIteration,
                                    actions = model,
                                )
                            }
                        }
                        Route.Settings -> SettingsScreen(state, model)
                        Route.AiLog -> AiJournalScreen(state, model)
                        is Route.AiExchange -> AiExchangeScreen(state, model)
                        is Route.AiRaw -> AiRawScreen(state, model)
                    }
                }
                if (showTabs) TutorTabs(state.tab, model::selectTab)
            }
            state.word?.let { word -> WordSheet(word, model::dismissWord) }
            state.error?.let { message ->
                Text(
                    message,
                    color = TutorRed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (showTabs) 72.dp else 16.dp)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(14.dp))
                        .background(TutorRedSoft)
                        .clickable { model.dismissError() }
                        .padding(12.dp),
                )
            }
            state.busy?.let { label ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = TutorText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(TutorCard)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TutorTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    // Four Russian labels do not fit a phone width at 200%. Keep them fully
    // visible and still larger than the default 100% size.
    val chrome = if (scale > LargeSystemFontScale) LargeSystemFontScale / scale else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .background(TutorCard)
            .navigationBarsPadding()
            .heightIn(min = 58.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(Tab.BOOKS, "Книги", selected, onSelect, chrome)
        TabItem(Tab.STUDY, "Учёба", selected, onSelect, chrome)
        TabItem(Tab.KNOWLEDGE, "Словарь", selected, onSelect, chrome)
        TabItem(Tab.PROFILE, "Профиль", selected, onSelect, chrome)
    }
}

@Composable
private fun TabItem(tab: Tab, label: String, selected: Tab, onSelect: (Tab) -> Unit, chrome: Float) {
    val color = if (tab == selected) TutorBlue else TutorMuted
    Column(
        Modifier.clickable { onSelect(tab) }.padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tabGlyph(tab), color = color, fontSize = (16f * chrome).sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        Text(
            label,
            color = color,
            fontSize = (11f * chrome).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
        )
    }
}

private fun tabGlyph(tab: Tab): String = when (tab) {
    Tab.BOOKS -> "▤"
    Tab.STUDY -> "✎"
    Tab.KNOWLEDGE -> "文"
    Tab.PROFILE -> "☺"
}

@Composable
private fun WordSheet(word: WordSheetUi, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(24.dp))
                .background(TutorCard)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    word.expression,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = TutorText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Закрыть", color = TutorBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClose))
            }
            if (word.sentence.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(word.sentence, color = TutorSecondary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                word.status?.let { status ->
                    val (fg, bg) = when (status) {
                        com.tepmex.extensivereadingtutor.domain.KnowledgeStatus.NEEDS_REVIEW -> TutorAmber to TutorAmberSoft
                        com.tepmex.extensivereadingtutor.domain.KnowledgeStatus.LEARNING -> TutorBlue to TutorBlueSoft
                        else -> TutorGreen to TutorGreenSoft
                    }
                    Pill(knowledgeLabel(status), fg, bg)
                }
                word.skill?.let { Pill(skillLabel(it), TutorSecondary, TutorTrack) }
            }
            Spacer(Modifier.height(10.dp))
            Text(word.gloss, color = TutorText, fontSize = 16.sp)
            word.example?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = TutorMuted, fontSize = 14.sp)
            }
        }
    }
}
