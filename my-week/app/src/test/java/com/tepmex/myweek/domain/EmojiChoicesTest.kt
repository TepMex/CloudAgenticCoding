package com.tepmex.myweek.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiChoicesTest {
    @Test
    fun unusedListKeepsBuiltinOrderThenRemembered() {
        val ordered = EmojiChoices.order(emptyMap(), listOf("🦄", "📚", "  ", "🧪"))
        assertEquals(EmojiChoices.builtins + listOf("🦄", "🧪"), ordered)
        assertEquals(1, ordered.count { it == "📚" })
        assertFalse(ordered.any { it.isBlank() })
    }

    @Test
    fun higherUseComesFirstAndTiesKeepBuiltinOrder() {
        val usage = mapOf("🎮" to 2, "📚" to 2, "🦄" to 4, "🧹" to 1)
        val ordered = EmojiChoices.order(usage, listOf("🦄"))
        assertEquals("🦄", ordered.first())
        assertTrue(ordered.indexOf("📚") < ordered.indexOf("🎮"))
        assertTrue(ordered.indexOf("🎮") < ordered.indexOf("🧹"))
        assertTrue(ordered.indexOf("🧹") < ordered.indexOf("•"))
        assertTrue(EmojiChoices.builtins.all { it in ordered })
    }

    @Test
    fun storedCustomEmojiAppearsEvenBeforeItIsRemembered() {
        val ordered = EmojiChoices.order(mapOf("🦊" to 3, "🦄" to 3), listOf("🦄"))
        assertEquals(listOf("🦄", "🦊"), ordered.take(2))
        assertEquals(EmojiChoices.builtins.size + 2, ordered.size)
    }

    @Test
    fun rememberedEmojiStaysWhenNothingUsesIt() {
        val ordered = EmojiChoices.order(mapOf("📚" to 5), listOf("🦄"))
        assertEquals("📚", ordered.first())
        assertEquals("🦄", ordered.last())
    }
}
