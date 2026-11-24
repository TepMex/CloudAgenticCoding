package com.tepmex.assumptions.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    @Test
    fun quadrantsFollowBeforeThenAfter() {
        assertEquals(Quadrant.NegativeNegative, quadrantOf(-2, -4))
        assertEquals(Quadrant.NegativePositive, quadrantOf(-5, 1))
        assertEquals(Quadrant.PositiveNegative, quadrantOf(3, -1))
        assertEquals(Quadrant.PositivePositive, quadrantOf(5, 2))
        assertEquals(null, quadrantOf(0, 3))
        assertEquals(null, quadrantOf(2, 0))
        assertEquals(null, quadrantOf(-1, null))
    }

    @Test
    fun colorsAreRedBrightGreenDarkGreenAndGray() {
        assertTrue(isRed(Quadrant.NegativeNegative.colorArgb))
        assertTrue(isBrightGreen(Quadrant.PositivePositive.colorArgb))
        assertTrue(isDarkGreen(Quadrant.NegativePositive.colorArgb))
        assertTrue(isGray(Quadrant.PositiveNegative.colorArgb))
    }

    @Test
    fun countsSkipPendingAndNeutral() {
        val report = AssumptionReport.from(
            listOf(
                expectation(1, -2, -1),
                expectation(2, -3, -4),
                expectation(3, -1, 4),
                expectation(4, 2, -3),
                expectation(5, 4, 5),
                expectation(6, 1, 1),
                expectation(7, -2, null),
                expectation(8, 3, null),
                expectation(9, 0, 2),
                expectation(10, -4, 0),
            ),
        )
        assertEquals(2, report.counts.getValue(Quadrant.NegativeNegative))
        assertEquals(1, report.counts.getValue(Quadrant.NegativePositive))
        assertEquals(1, report.counts.getValue(Quadrant.PositiveNegative))
        assertEquals(2, report.counts.getValue(Quadrant.PositivePositive))
        assertEquals(6, report.matrixTotal)
        assertEquals(2, report.pending)
        assertEquals(2, report.neutral)
    }

    private fun expectation(id: Long, before: Int, after: Int?) = Expectation(
        id = id,
        eventText = "событие $id",
        expectedOutcome = "исход",
        beforeScore = before,
        afterScore = after,
        afterNote = "",
        createdAtEpochMs = id,
        resolvedAtEpochMs = after?.let { id + 100 },
    )

    private fun channel(color: Long, shift: Int): Int = ((color shr shift) and 0xFF).toInt()

    private fun isRed(color: Long): Boolean {
        val red = channel(color, 16)
        val green = channel(color, 8)
        val blue = channel(color, 0)
        return red > 180 && green < 90 && blue < 90
    }

    private fun isBrightGreen(color: Long): Boolean {
        val red = channel(color, 16)
        val green = channel(color, 8)
        val blue = channel(color, 0)
        return green > 160 && red < 100 && green > blue + 60
    }

    private fun isDarkGreen(color: Long): Boolean {
        val red = channel(color, 16)
        val green = channel(color, 8)
        val blue = channel(color, 0)
        return green in 70..130 && green > red + 30 && green > blue && red < 60
    }

    private fun isGray(color: Long): Boolean {
        val red = channel(color, 16)
        val green = channel(color, 8)
        val blue = channel(color, 0)
        return kotlin.math.abs(red - green) < 12 &&
            kotlin.math.abs(green - blue) < 12 &&
            red in 130..180
    }
}
