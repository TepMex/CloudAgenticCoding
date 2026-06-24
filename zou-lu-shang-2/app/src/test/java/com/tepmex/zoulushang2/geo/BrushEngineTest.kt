package com.tepmex.zoulushang2.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEngineTest {
    @Test
    fun stayingAtSameSpotIncreasesIntensity() {
        val painted = mutableMapOf<Long, Int>()
        BrushEngine.applyLocation(39.9, 116.4, null, null) { key, delta ->
            painted[key] = (painted[key] ?: 0) + delta
        }
        val firstTotal = painted.values.sum()
        BrushEngine.applyLocation(39.90001, 116.40001, 39.9, 116.4) { key, delta ->
            painted[key] = (painted[key] ?: 0) + delta
        }
        val secondTotal = painted.values.sum()
        assertTrue(secondTotal > firstTotal)
    }

    @Test
    fun movementPaintsLineBetweenCells() {
        val painted = mutableSetOf<Long>()
        BrushEngine.applyLocation(39.90, 116.40, null, null) { key, _ ->
            painted.add(key)
        }
        val startCount = painted.size
        BrushEngine.applyLocation(39.905, 116.405, 39.90, 116.40) { key, _ ->
            painted.add(key)
        }
        assertTrue(painted.size > startCount)
    }
}
