package com.tepmex.byokassistedreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.byokassistedreader.domain.ReadingLayer
import com.tepmex.byokassistedreader.domain.RubyFit
import com.tepmex.byokassistedreader.domain.Sentence
import com.tepmex.byokassistedreader.domain.StpvoRole
import com.tepmex.byokassistedreader.domain.rubyRows
import com.tepmex.byokassistedreader.domain.PinyinSyllable

@Composable
fun ReaderScreen(
    title: String,
    pages: List<List<Sentence>>,
    pageIndex: Int,
    layer: ReadingLayer,
    assist: AssistState,
    onLayout: (columns: Int, rowHeightPx: Int, viewportPx: Int) -> Unit,
    onTurn: (forward: Boolean) -> Unit,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val page = pages.getOrNull(pageIndex).orEmpty()
    val text = page.joinToString("") { it.text }
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(pageIndex, pages.size) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        when {
                            drag > 80f -> onTurn(false)
                            drag < -80f -> onTurn(true)
                        }
                        drag = 0f
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Text("${layer.ordinal} ${layer.label}")
            TextButton(onClick = onSettings) { Text("Настройки") }
            TextButton(onClick = onOpen) { Text("Файл") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val pinyinStyle = TextStyle(fontSize = RubyFit.READABLE_PINYIN_SP.sp, fontFamily = FontFamily.SansSerif)
            val widest = measurer.measure(RubyFit.SAMPLE, pinyinStyle).size.width
            val hanziPx = RubyFit.hanziPx(widest)
            val hanziSp = with(density) { hanziPx.toSp() }
            val pinyinSize = RubyFit.pinyinSp(RubyFit.READABLE_PINYIN_SP, widest.toFloat(), hanziPx.toFloat()).sp
            val pinyinHeight = measurer.measure(RubyFit.SAMPLE, TextStyle(fontSize = pinyinSize)).size.height
            val hanziHeight = measurer.measure("字", TextStyle(fontSize = hanziSp)).size.height
            val rowHeight = (pinyinHeight + hanziHeight + with(density) { 2.dp.roundToPx() }).coerceAtLeast(1)
            val columns = (constraints.maxWidth / hanziPx).coerceAtLeast(1)
            val viewport = constraints.maxHeight
            LaunchedEffect(columns, rowHeight, viewport, text.length) {
                onLayout(columns, rowHeight, viewport)
            }
            val passage: @Composable (Modifier) -> Unit = { modifier ->
                Passage(
                    text = text,
                    layer = layer,
                    assist = assist,
                    columns = columns,
                    hanziSp = hanziSp,
                    pinyinSize = pinyinSize,
                    modifier = modifier,
                )
            }
            if (layer == ReadingLayer.GLOSS_ZH || layer == ReadingLayer.GLOSS_RU) {
                Column(Modifier.fillMaxSize()) {
                    passage(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    GlossaryPane(
                        assist = assist,
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    passage(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp))
                    if (layer == ReadingLayer.STRUCTURE) {
                        StructureLegend()
                        AssistNote(assist, onRetry)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onTurn(false) }, enabled = pageIndex > 0) { Text("Назад") }
            Text(if (pages.isEmpty()) "—" else "${pageIndex + 1} / ${pages.size}")
            TextButton(onClick = { onTurn(true) }, enabled = pageIndex < pages.lastIndex) { Text("Дальше") }
        }
    }
}

@Composable
private fun Passage(
    text: String,
    layer: ReadingLayer,
    assist: AssistState,
    columns: Int,
    hanziSp: androidx.compose.ui.unit.TextUnit,
    pinyinSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
) {
    val color = MaterialTheme.colorScheme.onBackground
    when (layer) {
        ReadingLayer.PINYIN -> RubyText(text, columns, hanziSp, pinyinSize, modifier)
        ReadingLayer.STRUCTURE -> {
            val spans = (assist as? AssistState.Structure)?.spans.orEmpty()
            val dark = isSystemInDarkTheme()
            val annotated = buildAnnotatedString {
                var i = 0
                while (i < text.length) {
                    val span = spans.firstOrNull { i >= it.start && i < it.end }
                    if (span == null) {
                        append(text[i])
                        i += 1
                    } else {
                        withStyle(SpanStyle(background = roleColor(span.role, dark))) {
                            append(text.substring(span.start, span.end))
                        }
                        i = span.end
                    }
                }
            }
            Text(
                annotated,
                modifier = modifier.padding(12.dp),
                fontSize = hanziSp,
                lineHeight = (hanziSp.value * 1.4f).sp,
                color = color,
            )
        }
        else -> Text(
            text,
            modifier = modifier.padding(12.dp),
            fontSize = hanziSp,
            lineHeight = (hanziSp.value * 1.35f).sp,
            color = color,
        )
    }
}

@Composable
private fun RubyText(
    text: String,
    columns: Int,
    hanziSp: androidx.compose.ui.unit.TextUnit,
    pinyinSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
) {
    val rows = rubyRows(text, columns, PinyinSyllable::of)
    val hanziWidth = with(LocalDensity.current) { hanziSp.toDp() }
    val pinyinHeight = with(LocalDensity.current) { (pinyinSize.value * 1.4f).sp.toDp() }
    Column(modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        for (row in rows) {
            Row {
                for (cell in row) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(hanziWidth)) {
                        Box(Modifier.width(hanziWidth).height(pinyinHeight), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                cell.pinyin,
                                fontSize = pinyinSize,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            cell.glyph,
                            fontSize = hanziSp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dark = isSystemInDarkTheme()
        StpvoRole.entries.forEach { role ->
            Text(
                role.ru,
                modifier = Modifier.background(roleColor(role, dark)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AssistNote(assist: AssistState, onRetry: () -> Unit) {
    when (assist) {
        AssistState.Loading -> Text("Разбираю предложения…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        is AssistState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(assist.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f).padding(start = 16.dp))
            TextButton(onClick = onRetry) { Text("Повторить") }
        }
        else -> Unit
    }
}

@Composable
private fun GlossaryPane(assist: AssistState, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (assist) {
            AssistState.Loading -> Text("Собираю словарь…")
            is AssistState.Failed -> {
                Text(assist.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            is AssistState.Gloss -> {
                Text("Незнакомые слова", style = MaterialTheme.typography.labelLarge)
                if (assist.page.words.isEmpty()) Text("Нет незнакомых слов на этой странице.")
                assist.page.words.forEach { entry ->
                    Text(entry.word, fontFamily = FontFamily.SansSerif, style = MaterialTheme.typography.titleLarge)
                    Text(entry.explanation)
                }
                Text("成语", style = MaterialTheme.typography.labelLarge)
                if (assist.page.chengyu.isEmpty()) Text("На этой странице нет чэнъюев.")
                assist.page.chengyu.forEach { entry ->
                    Text(entry.word, style = MaterialTheme.typography.titleLarge)
                    Text(entry.explanation)
                }
            }
            AssistState.Idle -> Text("Словарь появится после ответа модели.")
            is AssistState.Structure -> Unit
        }
    }
}
