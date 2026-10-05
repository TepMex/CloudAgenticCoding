package com.tepmex.byokassistedreader.domain

import com.tepmex.byokassistedreader.ui.KnownWordsFieldMaxFraction
import com.tepmex.byokassistedreader.ui.OverlayAlpha
import com.tepmex.byokassistedreader.ui.chengyuBackground
import com.tepmex.byokassistedreader.ui.dictionaryBackground
import com.tepmex.byokassistedreader.ui.literalBackground
import com.tepmex.byokassistedreader.ui.pageBackground
import com.tepmex.byokassistedreader.ui.referenceColor
import com.tepmex.byokassistedreader.ui.referenceWindowBackground
import com.tepmex.byokassistedreader.ui.retellingBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderLogicTest {
    @Test
    fun knownWordsAndHanzi() {
        val raw = "我\n你好，猫"
        assertEquals(listOf("我", "你好", "猫"), KnownLexicon.words(raw))
        assertEquals(listOf("我", "你", "好", "猫"), KnownLexicon.hanzi(raw))
        assertEquals(setOf("我", "你", "好", "猫"), KnownLexicon.familiarHanzi(raw))
    }

    @Test
    fun pinyinOnlyForHanziOutsideKnownWords() {
        val familiar = KnownLexicon.familiarHanzi("你好，猫")
        assertEquals(setOf("你", "好", "猫"), familiar)
        assertEquals("", pinyinToShow("你", familiar, "nǐ"))
        assertEquals("", pinyinToShow("好", familiar, "hǎo"))
        assertEquals("", pinyinToShow("猫", familiar, "māo"))
        assertEquals("kàn", pinyinToShow("看", familiar, "kàn"))
        assertEquals("nǐ", pinyinToShow("你", emptySet(), "nǐ"))
        assertEquals("", pinyinToShow("。", familiar, ""))
        assertEquals("nǐ", pinyinToShow("你", familiar, "nǐ", PinyinScope.ALL))
        assertEquals("hǎo", pinyinToShow("好", familiar, "hǎo", PinyinScope.ALL))
        assertEquals("", pinyinToShow("。", familiar, "", PinyinScope.ALL))
        assertEquals(PinyinScope.ALL, PinyinScope.UNKNOWN.toggle())
        assertEquals(PinyinScope.UNKNOWN, PinyinScope.ALL.toggle())
        assertEquals("только неизвестные", PinyinScope.UNKNOWN.label)
        assertEquals("все иероглифы", PinyinScope.ALL.label)
        assertEquals(emptySet<String>(), KnownLexicon.familiarHanzi("hello, 123"))
    }

    @Test
    fun knownWordsEditorIsCappedAtHalfTheScreen() {
        assertEquals(0.5f, KnownWordsFieldMaxFraction)
    }

    @Test
    fun structureLegendKeepsObjectLabelWhole() {
        assertEquals("С чем", StpvoRole.OBJECT.ru)
        assertFalse(StpvoRole.OBJECT.ru.contains('\n'))
        assertEquals(
            listOf("Кто", "Когда", "Где", "Что делает", "С чем"),
            StpvoRole.entries.map { it.ru },
        )
    }

    @Test
    fun glossaryBackgroundsAreThreeDistinctShades() {
        assertEquals(0.86f, OverlayAlpha)
        assertTrue(OverlayAlpha < 1f)
        for (dark in listOf(false, true)) {
            val colors = listOf(
                pageBackground(dark),
                dictionaryBackground(dark),
                chengyuBackground(dark),
                referenceWindowBackground(dark),
                referenceColor(ReferenceKind.NAME, dark),
                referenceColor(ReferenceKind.PLACE, dark),
                referenceColor(ReferenceKind.TERM, dark),
                literalBackground(dark),
                retellingBackground(dark),
            )
            assertEquals(colors.toSet().size, colors.size)
        }
    }

    @Test
    fun sentencesAndPagesDoNotSplitASentence() {
        val sentences = splitSentences("昨天我看书。今天")
        assertEquals(listOf(Sentence("昨天我看书。", true), Sentence("今天", false)), sentences)
        val pages = packPages(sentences, { if (it.complete) 80 else 40 }, capacity = 100)
        assertEquals(2, pages.size)
        assertEquals(listOf(sentences[0]), pages[0])
        assertEquals(listOf(sentences[1]), pages[1])
        val huge = packPages(listOf(Sentence("很长。", true)), { 500 }, capacity = 100)
        assertEquals(1, huge.size)
        assertEquals(1, huge[0].size)
    }

    @Test
    fun rubyRows() {
        val rows = rubyRows("你好", columns = 4) { glyph -> if (glyph == "你") "nǐ" else "" }
        assertEquals(1, rows.size)
        assertEquals(listOf("你", "好"), rows[0].map { it.glyph })
        assertEquals("nǐ", rows[0][0].pinyin)
        assertEquals("", rows[0][1].pinyin)
        assertEquals(listOf(0, 1), rows[0].map { it.index })
        val broken = rubyRows("你\n好。", columns = 4) { "x" }
        assertEquals(2, broken.size)
        assertEquals("", broken[1][1].pinyin)
        assertEquals("。", broken[1][1].glyph)
        assertEquals(listOf(0), broken[0].map { it.index })
        assertEquals(listOf(2, 3), broken[1].map { it.index })
    }

    @Test
    fun rubyFitKeepsSixLetterPinyinReadable() {
        val sample = 180
        val hanzi = 160
        val cell = RubyFit.cellPx(sample, hanzi)
        assertTrue(cell >= sample)
        assertEquals(12f, RubyFit.pinyinSp(12f, sample.toFloat(), cell.toFloat(), 8f))
        assertEquals("zhuāng", RubyFit.SAMPLE)
        assertEquals(8f, RubyFit.MIN_PINYIN_SP)
    }

    @Test
    fun widerHanziDoesNotAddAnOverlappingColumn() {
        val content = 1080
        val pinyinSample = 180
        val hanzi = 216
        val comfort = RubyFit.cellPx(pinyinSample, hanzi)
        assertEquals(216, comfort)
        val range = RubyFit.columnRange(content, comfort, RubyFit.cellPx(120, 144))
        assertEquals(5, range.first)
        assertTrue(range.first < content / pinyinSample)
        val slot = RubyFit.slotPx(content, range.first, comfort, range.first)
        assertEquals(comfort, slot)
        assertTrue(range.first * slot <= content)
        assertEquals(12f, RubyFit.pinyinSp(12f, pinyinSample.toFloat(), slot.toFloat(), 8f))
    }

    @Test
    fun columnCountFitsAndKeepsPinyinReadable() {
        val content = 1000
        val sample = 180
        val comfort = RubyFit.cellPx(sample, sample)
        val minCell = RubyFit.cellPx(120, 120)
        val range = RubyFit.columnRange(content, comfort, minCell)
        assertEquals(5..12, range)
        assertEquals(12, RubyFit.DENSE_COLUMNS_MAX)
        assertEquals(5, RubyFit.resolveColumns(0, range))
        assertEquals(5, RubyFit.resolveColumns(1, range))
        assertEquals(12, RubyFit.resolveColumns(99, range))
        assertEquals(6, RubyFit.resolveColumns(6, range))
        assertEquals(9, RubyFit.resolveColumns(9, range))
        for (columns in range) {
            val slot = RubyFit.slotPx(content, columns, comfort, range.first)
            assertTrue("$columns slots of $slot overflow $content", columns * slot <= content)
            val pinyin = RubyFit.pinyinSp(12f, sample.toFloat(), slot.toFloat(), 8f)
            val proportional = 12f * (slot / sample.toFloat())
            if (columns <= 8) {
                assertTrue("pinyin $pinyin left the 8sp floor", pinyin in 8f..12f)
            } else {
                assertEquals(proportional.coerceAtMost(12f), pinyin, 0.05f)
                assertTrue("dense pinyin $pinyin was clamped back to 8sp", pinyin < 8f)
            }
            val drawn = sample * (pinyin / 12f)
            assertTrue("pinyin $drawn exceeds slot $slot", drawn <= slot + 0.01f)
        }
    }

    @Test
    fun wideScreenKeepsCountsBeyondTwelve() {
        val range = RubyFit.columnRange(contentWidthPx = 2400, comfortCellPx = 100, minCellPx = 50)
        assertEquals(24..48, range)
    }

    @Test
    fun hanziStaysInsideTheSlotAndDoesNotGrowPastComfort() {
        assertEquals(72f, RubyFit.hanziSp(72f, slotPx = 180f, probeWidthPx = 250f, probeSp = 100f), 0.01f)
        val shrunk = RubyFit.hanziSp(72f, slotPx = 150f, probeWidthPx = 280f, probeSp = 100f)
        assertTrue(shrunk < 72f)
        assertEquals(150f, 280f * (shrunk / 100f), 0.01f)
        val capped = RubyFit.hanziSp(72f, slotPx = 202f, probeWidthPx = 280f, probeSp = 100f)
        assertEquals(72f, capped, 0.01f)
        assertTrue(280f * (capped / 100f) <= 202f)
    }

    @Test
    fun passageUsesTheFullReaderBody() {
        assertEquals(1f, RubyFit.PASSAGE_FRACTION)
        assertEquals(1000, RubyFit.passagePx(1000))
    }

    @Test
    fun pinyinUsesCaron() {
        assertEquals("zhuāng", PinyinSyllable.of("装"))
        assertEquals("nǐ", PinyinSyllable.of("你"))
        assertFalse(PinyinSyllable.of("你").contains('ĭ'))
        assertEquals("", PinyinSyllable.of("。"))
    }

    @Test
    fun stpvoAlignSkipsOverlapAndMissing() {
        val sentence = "昨天我在学校看书。"
        val spans = alignParts(
            sentence,
            listOf(
                StpvoPart(StpvoRole.TIME, "昨天"),
                StpvoPart(StpvoRole.SUBJECT, "我"),
                StpvoPart(StpvoRole.PLACE, "在学校"),
                StpvoPart(StpvoRole.VERB, "看书"),
                StpvoPart(StpvoRole.OBJECT, "书"),
                StpvoPart(StpvoRole.OBJECT, "没有"),
            ),
        )
        assertEquals(listOf(StpvoRole.TIME, StpvoRole.SUBJECT, StpvoRole.PLACE, StpvoRole.VERB), spans.map { it.role })
        assertEquals("昨天", sentence.substring(spans[0].start, spans[0].end))
    }

    @Test
    fun glossKeepsUnknownAndChengyu() {
        val parsed = GlossPage(
            words = listOf(
                GlossEntry("我", "я"),
                GlossEntry("学校", "место учёбы"),
                GlossEntry("外星", "нет на странице"),
            ),
            chengyu = listOf(GlossEntry("一心一意", "очень сосредоточенно")),
        )
        val page = visibleGloss("我在学校一心一意看书。", setOf("我"), parsed)
        assertEquals(listOf("学校"), page.words.map { it.word })
        assertEquals(listOf("一心一意"), page.chengyu.map { it.word })
    }

    @Test
    fun jsonAndEndpoint() {
        val stpvo = parseStpvo(
            """```json
            {"sentences":[{"text":"我看书。","parts":[{"role":"subject","text":"我"},{"role":"verb","text":"看"},{"role":"object","text":"书"}]}]}
            ```""",
        )
        assertEquals("我", stpvo.single().parts.first().text)
        val gloss = parseGloss("""{"words":[{"word":"书","explanation":"книга"}],"chengyu":[]}""")
        assertEquals("书", gloss.words.single().word)
        assertEquals("https://api.openai.com/v1/chat/completions", chatCompletionsUrl("https://api.openai.com/v1"))
        assertEquals("https://example.test/v1/chat/completions", chatCompletionsUrl("https://example.test"))
        assertEquals("https://example.test/chat/completions", chatCompletionsUrl("https://example.test/chat/completions/"))
    }

    @Test
    fun epubSpineSkipsRubyPronunciation() {
        val bytes = epub(
            chapter1 = "<p>你<strong>好</strong>。</p><ruby>汉<rt>hàn</rt></ruby>",
            chapter2 = "<p>世界</p>",
        )
        val book = parseEpub(bytes)
        assertEquals("故事", book.title)
        assertEquals(listOf("你好。", "汉", "世界"), book.paragraphs)
    }

    @Test
    fun referenceKeepsNamesPlacesAndTermsAheadOfGlossaries() {
        val text = "孔子在长安谈科举和北京。我从北京去北京大学。"
        val parsed = ReferencePage(
            names = listOf(
                ReferenceEntry("孔子", "Конфуций."),
                ReferenceEntry("孔", "только внутри имени"),
                ReferenceEntry("长安", "как имя"),
                ReferenceEntry("无人", ""),
            ),
            places = listOf(
                ReferenceEntry("长安", "Древняя столица."),
                ReferenceEntry("北京", "Столица."),
                ReferenceEntry("北京大学", "Университет."),
                ReferenceEntry("外星", "нет на странице"),
            ),
            terms = listOf(
                ReferenceEntry("科举", "Экзамены на службу."),
                ReferenceEntry("北京", "не термин, уже место"),
            ),
        )
        val page = visibleReference(text, parsed)
        assertEquals(listOf("孔子", "长安"), page.names.map { it.word })
        assertEquals(listOf("北京", "北京大学"), page.places.map { it.word })
        assertEquals(listOf("科举"), page.terms.map { it.word })
        assertEquals("Столица.", page.places.first { it.word == "北京" }.explanation)
        val gloss = visibleGloss(
            text,
            setOf("北京"),
            GlossPage(words = listOf(GlossEntry("北京", "столица")), chengyu = emptyList()),
        )
        assertTrue(gloss.words.isEmpty())
        val spans = alignReference(text, page)
        assertEquals(
            listOf(
                ReferenceKind.NAME to "孔子",
                ReferenceKind.NAME to "长安",
                ReferenceKind.TERM to "科举",
                ReferenceKind.PLACE to "北京",
                ReferenceKind.PLACE to "北京",
                ReferenceKind.PLACE to "北京大学",
            ),
            spans.map { it.kind to text.substring(it.start, it.end) },
        )
        val university = spans.last { text.substring(it.start, it.end) == "北京大学" }
        assertTrue(spans.none { it.start > university.start && it.start < university.end })
    }

    @Test
    fun referenceJsonAndPrompt() {
        val parsed = parseReference(
            """```json
            {"names":[{"word":"孔子","explanation":"Конфуций."}],"places":[{"hanzi":"长安","gloss":"Столица."}],"terms":[],"items":[{"kind":"term","word":"科举","explanation":"Экзамены."}]}
            ```""",
        )
        assertEquals("孔子", parsed.names.single().word)
        assertEquals("Столица.", parsed.places.single().explanation)
        assertEquals("科举", parsed.terms.single().word)
        assertTrue(Prompts.referenceSystem.contains("\"names\""))
        assertTrue(Prompts.referenceSystem.contains("\"places\""))
        assertTrue(Prompts.referenceSystem.contains("\"terms\""))
        assertTrue(Prompts.referenceUser("孔子。").endsWith("孔子。"))
    }

    @Test
    fun pageReadingJsonAndPrompts() {
        assertEquals(
            "Я вчера в школа книга смотреть.",
            parsePageReading("""{"text":"Я вчера в школа книга смотреть."}"""),
        )
        assertEquals("Пересказ страницы.", parsePageReading("""```json
            {"translation":"Пересказ страницы."}
            ```"""))
        assertTrue(Prompts.literalSystem.contains("в ущерб русской структуре"))
        assertTrue(Prompts.retellingSystem.contains("более художественный"))
        assertTrue(Prompts.readingUser("昨天我看书。").endsWith("昨天我看书。"))
        assertEquals("Подстрочный перевод", AssistCard.LITERAL.label)
        assertEquals("Пересказ", AssistCard.RETELLING.label)
        assertTrue(AssistCard.LITERAL.hint.contains("важнее русской структуры"))
        assertTrue(AssistCard.RETELLING.hint.contains("родной русский"))
    }

    @Test
    fun layerWraps() {
        assertEquals(ReadingLayer.PINYIN, ReadingLayer.TEXT.step(forward = true))
        assertEquals(ReadingLayer.ASSIST, ReadingLayer.PINYIN.step(forward = true))
        assertEquals(ReadingLayer.STRUCTURE, ReadingLayer.ASSIST.step(forward = true))
        assertEquals(ReadingLayer.TEXT, ReadingLayer.STRUCTURE.step(forward = true))
        assertEquals(ReadingLayer.STRUCTURE, ReadingLayer.TEXT.step(forward = false))
        assertEquals("Справка", ReadingLayer.ASSIST.label)
        assertEquals("2 Справка", layerCaption(ReadingLayer.ASSIST, PinyinScope.UNKNOWN))
        assertEquals("3 Структура", layerCaption(ReadingLayer.STRUCTURE, PinyinScope.ALL))
        assertEquals(
            "1 Пиньинь · только неизвестные",
            layerCaption(ReadingLayer.PINYIN, PinyinScope.UNKNOWN),
        )
        assertEquals(
            "1 Пиньинь · все иероглифы",
            layerCaption(ReadingLayer.PINYIN, PinyinScope.ALL),
        )
        assertTrue(ReadingLayer.PINYIN.showsPinyin)
        assertTrue(ReadingLayer.STRUCTURE.showsPinyin)
        assertTrue(ReadingLayer.ASSIST.showsPinyin)
        assertFalse(ReadingLayer.TEXT.showsPinyin)
        assertEquals(
            listOf("Имена собственные", "Места", "Термины"),
            ReferenceKind.entries.map { it.ru },
        )
    }

    @Test
    fun assistWindowsCycleThroughAnEmptyScreen() {
        assertEquals(AssistCard.WORDS, AssistCard.NONE.step(forward = true))
        assertEquals(AssistCard.CHENGYU, AssistCard.WORDS.step(forward = true))
        assertEquals(AssistCard.REFERENCE, AssistCard.CHENGYU.step(forward = true))
        assertEquals(AssistCard.LITERAL, AssistCard.REFERENCE.step(forward = true))
        assertEquals(AssistCard.RETELLING, AssistCard.LITERAL.step(forward = true))
        assertEquals(AssistCard.NONE, AssistCard.RETELLING.step(forward = true))
        assertEquals(AssistCard.RETELLING, AssistCard.NONE.step(forward = false))
        assertEquals("Незнакомые слова", AssistCard.WORDS.label)
        assertEquals("成语", AssistCard.CHENGYU.label)
        assertEquals("Имена и места", AssistCard.REFERENCE.label)
        assertEquals("", AssistCard.NONE.label)
        assertTrue(AssistCard.LITERAL.needsReading)
        assertTrue(AssistCard.RETELLING.needsReading)
        assertFalse(AssistCard.WORDS.needsReading)
    }

    @Test
    fun horizontalSwipeChangesWindowsAndNeverTurnsThePage() {
        assertNull(overlaySwipe(ReadingLayer.TEXT, dragPx = -200f))
        assertNull(overlaySwipe(ReadingLayer.ASSIST, dragPx = 40f))
        assertEquals(OverlaySwipe.TogglePinyin, overlaySwipe(ReadingLayer.PINYIN, dragPx = -120f))
        assertEquals(OverlaySwipe.TogglePinyin, overlaySwipe(ReadingLayer.PINYIN, dragPx = 120f))
        assertEquals(OverlaySwipe.ToggleLegend, overlaySwipe(ReadingLayer.STRUCTURE, dragPx = -120f))
        assertEquals(OverlaySwipe.ToggleLegend, overlaySwipe(ReadingLayer.STRUCTURE, dragPx = 120f))
        assertEquals(OverlaySwipe.StepCard(forward = true), overlaySwipe(ReadingLayer.ASSIST, dragPx = -120f))
        assertEquals(OverlaySwipe.StepCard(forward = false), overlaySwipe(ReadingLayer.ASSIST, dragPx = 120f))
    }

    @Test
    fun htmlEntity() {
        assertEquals(listOf("你"), htmlToParagraphs("<p>&#20320;</p>"))
    }

    private fun epub(chapter1: String, chapter2: String): ByteArray {
        val files = mapOf(
            "META-INF/container.xml" to """
                <container version="1.0"><rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <package>
                  <metadata><dc:title>故事</dc:title></metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OEBPS/c1.xhtml" to chapter1,
            "OEBPS/text/c2.xhtml" to chapter2,
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
