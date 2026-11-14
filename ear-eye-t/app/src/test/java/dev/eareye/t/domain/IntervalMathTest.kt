package dev.eareye.t.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalMathTest {
    @Test
    fun mergeOverlappingAndTouchingHalfOpenIntervals() {
        val merged = mergeIntervals(
            listOf(
                TimeInterval(6, 7),
                TimeInterval(0, 5),
                TimeInterval(5, 8),
                TimeInterval(20, 20),
                TimeInterval(10, 12),
            ),
        )
        assertEquals(listOf(TimeInterval(0, 8), TimeInterval(10, 12)), merged)
        assertEquals(10L, totalDuration(merged))
    }

    @Test
    fun intersectAndSubtractAndClip() {
        assertEquals(
            listOf(TimeInterval(8, 10)),
            intersectIntervals(listOf(TimeInterval(0, 10)), listOf(TimeInterval(8, 12))),
        )
        assertEquals(
            emptyList<TimeInterval>(),
            intersectIntervals(listOf(TimeInterval(0, 5)), listOf(TimeInterval(5, 9))),
        )
        assertEquals(
            listOf(TimeInterval(0, 2), TimeInterval(4, 8)),
            subtractIntervals(
                listOf(TimeInterval(0, 10)),
                listOf(TimeInterval(8, 12), TimeInterval(2, 4)),
            ),
        )
        assertEquals(
            listOf(TimeInterval(10, 40)),
            clipIntervals(listOf(TimeInterval(0, 100)), TimeInterval(10, 40)),
        )
        assertEquals(0L, totalDuration(subtractIntervals(listOf(TimeInterval(0, 5)), listOf(TimeInterval(0, 5)))))
    }
}
