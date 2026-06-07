package com.tepmex.zoulushang.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileMathTest {
    @Test
    fun boundsFromTileKeys_emptyReturnsNull() {
        assertNull(TileMath.boundsFromTileKeys(emptyList()))
    }

    @Test
    fun boundsFromTileKeys_envelopsTiles() {
        val keyA = TileMath.packTileKey(TileMath.GRID_ZOOM, 100, 200)
        val keyB = TileMath.packTileKey(TileMath.GRID_ZOOM, 101, 201)
        val bounds = TileMath.boundsFromTileKeys(listOf(keyA, keyB))!!

        val boundsA = TileMath.tileBounds(TileMath.GRID_ZOOM, 100, 200)
        val boundsB = TileMath.tileBounds(TileMath.GRID_ZOOM, 101, 201)

        assertEquals(boundsA.latNorth, bounds.latNorth, 0.0)
        assertEquals(boundsB.latSouth, bounds.latSouth, 0.0)
        assertEquals(boundsA.lonWest, bounds.lonWest, 0.0)
        assertEquals(boundsB.lonEast, bounds.lonEast, 0.0)
    }
}
