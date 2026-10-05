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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.byokassistedreader.domain.AssistCard
import com.tepmex.byokassistedreader.domain.ColoredSpan
import com.tepmex.byokassistedreader.domain.GlossEntry
import com.tepmex.byokassistedreader.domain.KnownLexicon
import com.tepmex.byokassistedreader.domain.PinyinScope
import com.tepmex.byokassistedreader.domain.PinyinSyllable
import com.tepmex.byokassistedreader.domain.ReadingLayer
import com.tepmex.byokassistedreader.domain.layerCaption
import com.tepmex.byokassistedreader.domain.ReferenceKind
import com.tepmex.byokassistedreader.domain.ReferenceSpan
import com.tepmex.byokassistedreader.domain.RubyFit
import com.tepmex.byokassistedreader.domain.Sentence
import com.tepmex.byokassistedreader.domain.StpvoRole
import com.tepmex.byokassistedreader.domain.pinyinToShow
import com.tepmex.byokassistedreader.domain.rubyRows

internal val ReaderHorizontalPadding = 12.dp

private val GridFont = FontFamily.SansSerif
private val WindowShape = RoundedCornerShape(16.dp)

@Composable
fun ReaderScreen(
    title: String,
    pages: List<List<Sentence>>,
    pageIndex: Int,
    layer: ReadingLayer,
    assistCard: AssistCard,
    legendVisible: Boolean,
    pinyinScope: PinyinScope,
    literalText: String?,
    retellingText: String?,
    readingStatus: ReadingStatus,
    charsPerLine: Int,
    knownWords: String,
    assist: AssistState,
    onLayout: (columns: Int, rowHeightPx: Int, viewportPx: Int) -> Unit,
    onTurn: (forward: Boolean) -> Unit,
    onOverlaySwipe: (dragPx: Float) -> Unit,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val page = pages.getOrNull(pageIndex).orEmpty()
    val text = page.joinToString("") { it.text }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    layerCaption(layer, pinyinScope),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (assist is AssistState.Failed) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Повторить")
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки")
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Файл")
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(layer) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            onOverlaySwipe(drag)
                            drag = 0f
                        },
                    )
                },
        ) {
            val density = LocalDensity.current
            val padPx = with(density) { ReaderHorizontalPadding.roundToPx() }
            val contentWidth = (constraints.maxWidth - padPx * 2).coerceAtLeast(1)
            val metrics = gridMetrics(contentWidth, charsPerLine)
            val passagePx = RubyFit.passagePx(constraints.maxHeight)
            val windowMax = maxHeight * 0.46f
            LaunchedEffect(metrics.columns, metrics.rowHeightPx, passagePx, text.length) {
                onLayout(metrics.columns, metrics.rowHeightPx, passagePx)
            }
            GlyphGrid(
                text = text,
                knownWords = knownWords,
                pinyinScope = pinyinScope,
                layer = layer,
                assistCard = assistCard,
                assist = assist,
                metrics = metrics,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            )
            OverlayWindow(
                layer = layer,
                assistCard = assistCard,
                legendVisible = legendVisible,
                literalText = literalText,
                retellingText = retellingText,
                readingStatus = readingStatus,
                assist = assist,
                windowMax = windowMax,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onTurn(false) }, enabled = pageIndex > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(if (pages.isEmpty()) "—" else "${pageIndex + 1} / ${pages.size}")
            IconButton(onClick = { onTurn(true) }, enabled = pageIndex < pages.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Дальше")
            }
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
    pinyinScope: PinyinScope,
    layer: ReadingLayer,
    assistCard: AssistCard,
    assist: AssistState,
    metrics: GridMetrics,
    modifier: Modifier,
) {
    val familiar = remember(knownWords) { KnownLexicon.familiarHanzi(knownWords) }
    val rows = rubyRows(text, metrics.columns) { glyph ->
        pinyinToShow(glyph, familiar, PinyinSyllable.of(glyph), pinyinScope)
    }
    val structureSpans = (assist as? AssistState.Structure)?.spans.orEmpty()
    val referenceSpans = if (layer == ReadingLayer.ASSIST && assistCard == AssistCard.REFERENCE) {
        (assist as? AssistState.Notes)?.spans.orEmpty()
    } else {
        emptyList()
    }
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val slotDp = with(density) { metrics.slotPx.toDp() }
    val pinyinHeight = with(density) { metrics.pinyinHeightPx.toDp() }
    val hanziHeight = with(density) { metrics.hanziHeightPx.toDp() }
    val ink = MaterialTheme.colorScheme.onBackground
    val ruby = MaterialTheme.colorScheme.onSurfaceVariant
    val showPinyin = layer.showsPinyin
    Column(modifier.padding(horizontal = ReaderHorizontalPadding)) {
        for (row in rows) {
            Row {
                for (cell in row) {
                    val tint = when (layer) {
                        ReadingLayer.STRUCTURE -> roleAt(structureSpans, cell.index)?.let { roleColor(it, dark) }
                        ReadingLayer.ASSIST -> kindAt(referenceSpans, cell.index)?.let { referenceColor(it, dark) }
                        else -> null
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(slotDp).clipToBounds(),
                    ) {
                        Box(
                            Modifier.width(slotDp).height(pinyinHeight).clipToBounds(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (showPinyin && cell.pinyin.isNotEmpty()) {
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
                                .background(tint ?: Color.Transparent),
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

private fun kindAt(spans: List<ReferenceSpan>, index: Int): ReferenceKind? =
    spans.firstOrNull { index >= it.start && index < it.end }?.kind

@Composable
private fun OverlayWindow(
    layer: ReadingLayer,
    assistCard: AssistCard,
    legendVisible: Boolean,
    literalText: String?,
    retellingText: String?,
    readingStatus: ReadingStatus,
    assist: AssistState,
    windowMax: Dp,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val dark = isSystemInDarkTheme()
    val legendOpen = layer == ReadingLayer.STRUCTURE && legendVisible
    val cardOpen = layer == ReadingLayer.ASSIST && assistCard != AssistCard.NONE
    val failure = assist as? AssistState.Failed
    when {
        legendOpen -> FloatWindow(MaterialTheme.colorScheme.surface, windowMax, modifier) {
            StructureLegend()
            AssistStatus(assist, onRetry, loading = "Разбираю предложения…")
        }
        cardOpen && assistCard.needsReading -> FloatWindow(assistBackground(assistCard, dark), windowMax, modifier) {
            ReadingPane(
                card = assistCard,
                body = if (assistCard == AssistCard.LITERAL) literalText else retellingText,
                status = readingStatus,
                onRetry = onRetry,
            )
        }
        cardOpen -> {
            FloatWindow(assistBackground(assistCard, dark), windowMax, modifier) {
                when (assist) {
                    AssistState.Loading -> Text("Собираю справку…")
                    is AssistState.Failed -> FailedNote(assist.message, onRetry)
                    is AssistState.Notes -> when (assistCard) {
                        AssistCard.WORDS -> DualGlossary(
                            title = assistCard.label,
                            zh = assist.glossZh.words,
                            ru = assist.glossRu.words,
                            empty = "Нет незнакомых слов на этой странице.",
                        )
                        AssistCard.CHENGYU -> DualGlossary(
                            title = assistCard.label,
                            zh = assist.glossZh.chengyu,
                            ru = assist.glossRu.chengyu,
                            empty = "На этой странице нет чэнъюев.",
                        )
                        AssistCard.REFERENCE -> ReferenceWindow(assist)
                        AssistCard.LITERAL, AssistCard.RETELLING, AssistCard.NONE -> Unit
                    }
                    else -> Text("Справка появится после ответа модели.")
                }
            }
        }
        failure != null &&
            (layer == ReadingLayer.STRUCTURE || layer == ReadingLayer.ASSIST) -> {
            val message = failure.message
            FloatWindow(MaterialTheme.colorScheme.surface, windowMax, modifier) {
                FailedNote(message, onRetry)
            }
        }
        else -> Unit
    }
}

@Composable
private fun FloatWindow(
    background: Color,
    windowMax: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(max = windowMax)
            .clip(WindowShape)
            .background(background.copy(alpha = OverlayAlpha))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun StructureLegend() {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = Modifier.fillMaxWidth(),
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

private fun assistBackground(card: AssistCard, dark: Boolean): Color = when (card) {
    AssistCard.WORDS -> dictionaryBackground(dark)
    AssistCard.CHENGYU -> chengyuBackground(dark)
    AssistCard.REFERENCE -> referenceWindowBackground(dark)
    AssistCard.LITERAL -> literalBackground(dark)
    AssistCard.RETELLING -> retellingBackground(dark)
    AssistCard.NONE -> Color.Transparent
}

@Composable
private fun ReadingPane(
    card: AssistCard,
    body: String?,
    status: ReadingStatus,
    onRetry: () -> Unit,
) {
    Text(card.label, style = MaterialTheme.typography.labelLarge)
    if (card.hint.isNotEmpty()) Text(card.hint)
    when {
        !body.isNullOrBlank() -> Text(body)
        status is ReadingStatus.Failed -> FailedNote(status.message, onRetry)
        else -> Text(if (card == AssistCard.LITERAL) "Делаю подстрочник…" else "Делаю пересказ…")
    }
}

@Composable
private fun AssistStatus(assist: AssistState, onRetry: () -> Unit, loading: String) {
    when (assist) {
        AssistState.Loading -> Text(loading)
        is AssistState.Failed -> FailedNote(assist.message, onRetry)
        else -> Unit
    }
}

@Composable
private fun FailedNote(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = "Повторить")
        }
    }
}

@Composable
private fun DualGlossary(
    title: String,
    zh: List<GlossEntry>,
    ru: List<GlossEntry>,
    empty: String,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (zh.isEmpty() && ru.isEmpty()) {
        Text(empty)
        return
    }
    if (zh.isNotEmpty()) {
        Text("简单", style = MaterialTheme.typography.labelLarge)
        zh.forEach { GlossLine(it) }
    }
    if (ru.isNotEmpty()) {
        Text("По-русски", style = MaterialTheme.typography.labelLarge)
        ru.forEach { GlossLine(it) }
    }
}

@Composable
private fun GlossLine(entry: GlossEntry) {
    Text(entry.word, style = MaterialTheme.typography.titleLarge)
    Text(entry.explanation)
}

@Composable
private fun ReferenceWindow(notes: AssistState.Notes) {
    val dark = isSystemInDarkTheme()
    val groups = ReferenceKind.entries.map { kind ->
        kind to when (kind) {
            ReferenceKind.NAME -> notes.reference.names
            ReferenceKind.PLACE -> notes.reference.places
            ReferenceKind.TERM -> notes.reference.terms
        }
    }
    Text(AssistCard.REFERENCE.label, style = MaterialTheme.typography.labelLarge)
    if (groups.all { it.second.isEmpty() }) {
        Text("На этой странице нет имён, мест и терминов.")
        return
    }
    groups.forEach { (kind, entries) ->
        if (entries.isEmpty()) return@forEach
        Text(
            kind.ru,
            modifier = Modifier.background(referenceColor(kind, dark)).padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        entries.forEach { entry ->
            Text(entry.word, style = MaterialTheme.typography.titleLarge)
            Text(entry.explanation)
        }
    }
}
