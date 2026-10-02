package com.tepmex.byokassistedreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.byokassistedreader.domain.ColoredSpan
import com.tepmex.byokassistedreader.domain.GlossEntry
import com.tepmex.byokassistedreader.domain.KnownLexicon
import com.tepmex.byokassistedreader.domain.PinyinSyllable
import com.tepmex.byokassistedreader.domain.ReadingLayer
import com.tepmex.byokassistedreader.domain.RubyFit
import com.tepmex.byokassistedreader.domain.Sentence
import com.tepmex.byokassistedreader.domain.StpvoRole
import com.tepmex.byokassistedreader.domain.pinyinToShow
import com.tepmex.byokassistedreader.domain.rubyRows

internal val ReaderHorizontalPadding = 12.dp

private val GridFont = FontFamily.SansSerif

@Composable
fun ReaderScreen(
    title: String,
    pages: List<List<Sentence>>,
    pageIndex: Int,
    layer: ReadingLayer,
    charsPerLine: Int,
    knownWords: String,
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
            val density = LocalDensity.current
            val padPx = with(density) { ReaderHorizontalPadding.roundToPx() }
            val contentWidth = (constraints.maxWidth - padPx * 2).coerceAtLeast(1)
            val metrics = gridMetrics(contentWidth, charsPerLine)
            val passagePx = RubyFit.passagePx(constraints.maxHeight)
            LaunchedEffect(metrics.columns, metrics.rowHeightPx, passagePx, text.length) {
                onLayout(metrics.columns, metrics.rowHeightPx, passagePx)
            }
            Column(Modifier.fillMaxSize()) {
                GlyphGrid(
                    text = text,
                    knownWords = knownWords,
                    layer = layer,
                    assist = assist,
                    metrics = metrics,
                    modifier = Modifier
                        .weight(RubyFit.PASSAGE_FRACTION)
                        .verticalScroll(rememberScrollState()),
                )
                BottomBand(
                    layer = layer,
                    assist = assist,
                    onRetry = onRetry,
                    modifier = Modifier
                        .weight(1f - RubyFit.PASSAGE_FRACTION)
                        .verticalScroll(rememberScrollState()),
                )
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

internal data class GridMetrics(
    val columns: Int,
    val range: IntRange,
    val slotPx: Int,
    val hanziStyle: TextStyle,
    val pinyinStyle: TextStyle,
    val pinyinHeightPx: Int,
    val hanziHeightPx: Int,
    val rowHeightPx: Int,
)

@Composable
internal fun gridMetrics(contentWidthPx: Int, preferredColumns: Int): GridMetrics {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val comfortPinyin = gridStyle(RubyFit.COMFORT_PINYIN_SP.sp)
    val minPinyin = gridStyle(RubyFit.MIN_PINYIN_SP.sp)
    val comfortSamplePx = measurer.measure(RubyFit.SAMPLE, comfortPinyin).size.width.coerceAtLeast(1)
    val minSamplePx = measurer.measure(RubyFit.SAMPLE, minPinyin).size.width.coerceAtLeast(1)
    val hanziComfortPx = measurer.measure(
        "字",
        gridStyle(with(density) { comfortSamplePx.toSp() }),
    ).size.width.coerceAtLeast(1)
    val hanziMinPx = measurer.measure(
        "字",
        gridStyle(with(density) { minSamplePx.toSp() }),
    ).size.width.coerceAtLeast(1)
    val comfortCell = RubyFit.cellPx(comfortSamplePx, hanziComfortPx)
    val minCell = RubyFit.cellPx(minSamplePx, hanziMinPx)
    val range = RubyFit.columnRange(contentWidthPx, comfortCell, minCell)
    val columns = RubyFit.resolveColumns(preferredColumns, range)
    val slot = RubyFit.slotPx(contentWidthPx, columns, comfortCell, range.first)
    val pinyinSp = RubyFit.pinyinSp(
        RubyFit.COMFORT_PINYIN_SP,
        comfortSamplePx.toFloat(),
        slot.toFloat(),
        RubyFit.MIN_PINYIN_SP,
    )
    val probe = measurer.measure("字", gridStyle(100.sp))
    val naturalSp = with(density) { comfortSamplePx.toSp().value }
    val hanziSp = RubyFit.hanziSp(naturalSp, slot.toFloat(), probe.size.width.toFloat(), 100f)
    val hanziStyle = gridStyle(hanziSp.sp)
    val pinyinStyle = gridStyle(pinyinSp.sp)
    val pinyinHeight = measurer.measure(RubyFit.SAMPLE, pinyinStyle).size.height.coerceAtLeast(1)
    val hanziHeight = measurer.measure("字", hanziStyle).size.height.coerceAtLeast(1)
    val gap = with(density) { 2.dp.roundToPx() }
    return GridMetrics(
        columns = columns,
        range = range,
        slotPx = slot,
        hanziStyle = hanziStyle,
        pinyinStyle = pinyinStyle,
        pinyinHeightPx = pinyinHeight,
        hanziHeightPx = hanziHeight,
        rowHeightPx = (pinyinHeight + hanziHeight + gap).coerceAtLeast(1),
    )
}

private fun gridStyle(size: TextUnit) = TextStyle(
    fontSize = size,
    fontFamily = GridFont,
)

@Composable
private fun GlyphGrid(
    text: String,
    knownWords: String,
    layer: ReadingLayer,
    assist: AssistState,
    metrics: GridMetrics,
    modifier: Modifier,
) {
    val familiar = remember(knownWords) { KnownLexicon.familiarHanzi(knownWords) }
    val rows = rubyRows(text, metrics.columns) { glyph ->
        pinyinToShow(glyph, familiar, PinyinSyllable.of(glyph))
    }
    val spans = (assist as? AssistState.Structure)?.spans.orEmpty()
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val slotDp = with(density) { metrics.slotPx.toDp() }
    val pinyinHeight = with(density) { metrics.pinyinHeightPx.toDp() }
    val hanziHeight = with(density) { metrics.hanziHeightPx.toDp() }
    val ink = MaterialTheme.colorScheme.onBackground
    val ruby = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.padding(horizontal = ReaderHorizontalPadding)) {
        for (row in rows) {
            Row {
                for (cell in row) {
                    val role = if (layer == ReadingLayer.STRUCTURE) roleAt(spans, cell.index) else null
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(slotDp).clipToBounds(),
                    ) {
                        Box(
                            Modifier.width(slotDp).height(pinyinHeight).clipToBounds(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (layer == ReadingLayer.PINYIN && cell.pinyin.isNotEmpty()) {
                                Text(
                                    cell.pinyin,
                                    style = metrics.pinyinStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    textAlign = TextAlign.Center,
                                    color = ruby,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .width(slotDp)
                                .height(hanziHeight)
                                .clipToBounds()
                                .background(role?.let { roleColor(it, dark) } ?: Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell.glyph,
                                style = metrics.hanziStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center,
                                color = ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun roleAt(spans: List<ColoredSpan>, index: Int): StpvoRole? =
    spans.firstOrNull { index >= it.start && index < it.end }?.role

@Composable
private fun BottomBand(
    layer: ReadingLayer,
    assist: AssistState,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when (layer) {
        ReadingLayer.GLOSS_ZH, ReadingLayer.GLOSS_RU -> GlossaryPane(assist, onRetry, modifier)
        ReadingLayer.STRUCTURE -> Column(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            StructureLegend()
            AssistNote(assist, onRetry)
        }
        else -> Box(modifier)
    }
}

@Composable
private fun StructureLegend() {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendRow(dark, StpvoRole.SUBJECT, StpvoRole.TIME, StpvoRole.PLACE)
        LegendRow(dark, StpvoRole.VERB, StpvoRole.OBJECT)
    }
}

@Composable
private fun LegendRow(dark: Boolean, vararg roles: StpvoRole) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        roles.forEach { role ->
            Text(
                role.ru,
                modifier = Modifier.background(roleColor(role, dark)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun AssistNote(assist: AssistState, onRetry: () -> Unit) {
    when (assist) {
        AssistState.Loading -> Text("Разбираю предложения…", modifier = Modifier.padding(vertical = 4.dp))
        is AssistState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(assist.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Повторить") }
        }
        else -> Unit
    }
}

@Composable
private fun GlossaryPane(assist: AssistState, onRetry: () -> Unit, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (assist) {
            AssistState.Loading -> Text(
                "Собираю словарь…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            is AssistState.Failed -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(assist.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            is AssistState.Gloss -> {
                GlossaryBlock(
                    title = "Незнакомые слова",
                    entries = assist.page.words,
                    empty = "Нет незнакомых слов на этой странице.",
                    background = dictionaryBackground(dark),
                )
                GlossaryBlock(
                    title = "成语",
                    entries = assist.page.chengyu,
                    empty = "На этой странице нет чэнъюев.",
                    background = chengyuBackground(dark),
                )
            }
            AssistState.Idle -> Text(
                "Словарь появится после ответа модели.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            is AssistState.Structure -> Unit
        }
    }
}

@Composable
private fun GlossaryBlock(
    title: String,
    entries: List<GlossEntry>,
    empty: String,
    background: Color,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (entries.isEmpty()) {
            Text(empty)
        } else {
            entries.forEach { entry ->
                Text(entry.word, style = MaterialTheme.typography.titleLarge)
                Text(entry.explanation)
            }
        }
    }
}
