package com.tepmex.wodeluyou.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryParserTest {
    private val sample = """
        Категория	Город / регион	Русский	中文	Pinyin	Примечание / как использовать	Приоритет	Источник
        Населённый пункт	Синьцзян	Урумчи	乌鲁木齐	Wūlǔmùqí	Основной пункт маршрута	★★★	Китай 2026
        Фраза		Я не понимаю	我听不懂	Wǒ tīng bu dǒng		★★★	Дорожная лексика
        Напиток	Синьцзян	Муселес	木赛来斯 / 穆塞莱斯	Mùsàiláisī / Mùsāiláisī	Виноградный напиток	★★	Еда
    """.trimIndent()

    @Test
    fun parsesColumnsAndPriority() {
        val catalog = DictionaryParser.parse(sample)
        assertEquals(3, catalog.entries.size)
        val urumqi = catalog.entries.first()
        assertEquals("Населённый пункт", urumqi.category)
        assertEquals("Синьцзян", urumqi.region)
        assertEquals("Урумчи", urumqi.russian)
        assertEquals("乌鲁木齐", urumqi.hanzi)
        assertEquals("Wūlǔmùqí", urumqi.pinyin)
        assertEquals(3, urumqi.priorityStars)
        assertEquals("Основной пункт маршрута", urumqi.note)
    }

    @Test
    fun categoriesKeepFirstAppearanceOrder() {
        val catalog = DictionaryParser.parse(sample)
        assertEquals(
            listOf("Населённый пункт", "Фраза", "Напиток"),
            catalog.categories.map { it.name },
        )
        assertEquals(listOf(1, 1, 1), catalog.categories.map { it.count })
    }

    @Test
    fun bundledGlossaryHasExpectedShape() {
        val tsv = javaClass.classLoader!!.getResource("dictionary.tsv")!!.readText(Charsets.UTF_8)
        val catalog = DictionaryParser.parse(tsv)
        assertEquals(174, catalog.entries.size)
        assertEquals(20, catalog.categories.size)
        assertEquals("Населённый пункт", catalog.categories.first().name)
        assertEquals(23, catalog.categories.first().count)
        val phrases = catalog.categories.first { it.name == "Фраза" }
        assertEquals(20, phrases.count)
        assertTrue(catalog.entries.all { it.hanzi.isNotBlank() && it.pinyin.isNotBlank() && it.russian.isNotBlank() })
        assertEquals(3, catalog.entriesIn("Населённый пункт").first().priorityStars)
    }

    @Test
    fun searchMatchesHanziPinyinRussianAndFoldedPinyin() {
        val catalog = DictionaryParser.parse(sample)
        assertEquals(listOf("乌鲁木齐"), catalog.search("乌鲁木齐").map { it.hanzi })
        assertEquals(listOf("乌鲁木齐"), catalog.search("урумчи").map { it.hanzi })
        assertEquals(listOf("乌鲁木齐"), catalog.search("wulumuqi").map { it.hanzi })
        assertEquals(listOf("我听不懂"), catalog.search("не понимаю").map { it.hanzi })
        assertTrue(catalog.search("   ").isEmpty())
    }
}
