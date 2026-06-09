package com.tepmex.zoulushang.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun latLngToTile_roundTripsThroughTileBounds() {
        val cases = listOf(
            41.0082 to 28.9784, // Istanbul
            54.9885 to 73.3242, // Omsk
            -33.8688 to 151.2093, // Sydney (southern hemisphere)
        )
        val zoom = TileMath.GRID_ZOOM
        for ((lat, lng) in cases) {
            val (x, y) = TileMath.latLngToTile(lat, lng, zoom)
            val bounds = TileMath.tileBounds(zoom, x, y)
            assertTrue(
                "lat=$lat lng=$lng should fall inside tile ($x, $y)",
                lat <= bounds.latNorth && lat >= bounds.latSouth &&
                    lng >= bounds.lonWest && lng <= bounds.lonEast,
            )
        }
    }

    @Test
    fun approximateTileWidthMeters_decreasesWithHigherZoom() {
        val lat = 41.0
        val wider = TileMath.approximateTileWidthMeters(lat, TileMath.MIN_GRID_ZOOM)
        val narrower = TileMath.approximateTileWidthMeters(lat, TileMath.MAX_GRID_ZOOM)
        assertTrue(wider > narrower)
    }

    @Test
    fun tileYToLat_matchesWebMercatorFormula() {
        val zoom = TileMath.GRID_ZOOM
        val y = 12345
        val n = Math.PI - 2.0 * Math.PI * y / (1 shl zoom)
        val expected = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
        assertEquals(expected, TileMath.tileYToLat(y, zoom), 1e-9)
    }
}
