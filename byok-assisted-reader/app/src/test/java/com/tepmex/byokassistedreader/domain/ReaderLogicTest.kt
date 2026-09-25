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
        val broken = rubyRows("你\n好。", columns = 4) { "x" }
        assertEquals(2, broken.size)
        assertEquals("", broken[1][1].pinyin)
        assertEquals("。", broken[1][1].glyph)
    }

    @Test
    fun rubyFitKeepsSixLetterPinyinReadable() {
        val widest = 72
        val hanzi = RubyFit.hanziPx(widest)
        assertTrue(hanzi >= widest)
        assertEquals(12f, RubyFit.pinyinSp(12f, widest.toFloat(), hanzi.toFloat()))
        assertEquals("zhuāng", RubyFit.SAMPLE)
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
