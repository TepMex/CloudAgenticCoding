package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseMoneyTest {
    @Test
    fun spokenFormsMatchShopChinese() {
        assertEquals("一块", ChineseMoney.toSpokenKuai(1))
        assertEquals("两块", ChineseMoney.toSpokenKuai(2))
        assertEquals("十块", ChineseMoney.toSpokenKuai(10))
        assertEquals("十二块", ChineseMoney.toSpokenKuai(12))
        assertEquals("二十块", ChineseMoney.toSpokenKuai(20))
        assertEquals("二十五块", ChineseMoney.toSpokenKuai(25))
        assertEquals("一百块", ChineseMoney.toSpokenKuai(100))
        assertEquals("一百零一块", ChineseMoney.toSpokenKuai(101))
        assertEquals("一百一十四块", ChineseMoney.toSpokenKuai(114))
    }
}
