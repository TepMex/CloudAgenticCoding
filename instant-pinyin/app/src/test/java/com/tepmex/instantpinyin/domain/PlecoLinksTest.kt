package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlecoLinksTest {
    @Test
    fun hanBuildsPlecoSearch() {
        assertEquals(
            "plecoapi://x-callback-url/s?q=%E6%B1%89&x-source=instant-pinyin",
            PlecoLinks.searchUri("汉"),
        )
    }
}
