package com.tepmex.byokassistedreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(5..8, range)
        assertEquals(5, RubyFit.resolveColumns(0, range))
        assertEquals(5, RubyFit.resolveColumns(1, range))
        assertEquals(8, RubyFit.resolveColumns(99, range))
        assertEquals(6, RubyFit.resolveColumns(6, range))
        for (columns in range) {
            val slot = RubyFit.slotPx(content, columns, comfort, range.first)
            assertTrue("$columns slots of $slot overflow $content", columns * slot <= content)
            val pinyin = RubyFit.pinyinSp(12f, sample.toFloat(), slot.toFloat(), 8f)
            assertTrue(pinyin in 8f..12f)
            val drawn = sample * (pinyin / 12f)
            assertTrue("pinyin $drawn exceeds slot $slot", drawn <= slot + 0.01f)
        }
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
    fun passageKeepsMostOfTheBodyForTheMainText() {
        assertTrue(RubyFit.PASSAGE_FRACTION > 0.70f)
        assertTrue(1f - RubyFit.PASSAGE_FRACTION < 0.30f)
        assertEquals(750, RubyFit.passagePx(1000))
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
    fun layerWraps() {
        assertEquals(ReadingLayer.PINYIN, ReadingLayer.TEXT.step(forward = true))
        assertEquals(ReadingLayer.TEXT, ReadingLayer.GLOSS_RU.step(forward = true))
        assertEquals(ReadingLayer.GLOSS_RU, ReadingLayer.TEXT.step(forward = false))
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
