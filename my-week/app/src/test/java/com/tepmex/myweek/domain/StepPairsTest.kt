package com.tepmex.myweek.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepPairsTest {
    private val pairs = StepPairs(Random(42))

    @Test
    fun fewerThanTwoReturnsNull() {
        assertNull(pairs.select(emptyList()))
        assertNull(pairs.select(listOf(option("only"))))
    }

    @Test
    fun avoidsARecentPair() {
        val a = option("a")
        val b = option("b")
        val c = option("c")
        val key = StepPairs.pairKey(a.key, b.key)
        val history = StepPairs.History(
            recentPairKeys = List(20) { key },
            headToHeadCounts = mapOf(key to 10),
        )
        val counts = mutableMapOf<String, Int>()
        repeat(100) {
            val chosen = pairs.select(listOf(a, b, c), history)!!
            val chosenKey = StepPairs.pairKey(chosen.leftKey, chosen.rightKey)
            counts[chosenKey] = (counts[chosenKey] ?: 0) + 1
        }
        assertTrue((counts[key] ?: 0) < 40)
        assertEquals(100, counts.values.sum())
    }

    private fun option(key: String) = StepOption(
        key = key,
        emoji = "•",
        label = key,
        openCount = 1,
        rating = StepElo.INITIAL,
        comparisonCount = 0,
    )
}
