package com.tepmex.zoulushang.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLookupRemapperTest {
    @Test
    fun subdivide_increasesTileCountWhenZoomingIn() {
        val coarseKey = TileMath.packTileKey(14, 10, 20)
        val lookup = hashMapOf(coarseKey to 5)
        val remapped = TileLookupRemapper.remap(lookup, 16)
        assertEquals(16, remapped.size)
        remapped.values.forEach { assertEquals(5, it) }
    }

    @Test
    fun merge_decreasesTileCountWhenZoomingOut() {
        val keys = listOf(
            TileMath.packTileKey(16, 40, 80),
            TileMath.packTileKey(16, 41, 80),
            TileMath.packTileKey(16, 40, 81),
            TileMath.packTileKey(16, 41, 81),
        )
        val lookup = keys.associateWith { 1 }.let { HashMap(it) }
        val remapped = TileLookupRemapper.remap(lookup, 15)
        assertEquals(1, remapped.size)
        assertEquals(4, remapped.values.single())
    }

    @Test
    fun sameZoom_returnsCopy() {
        val key = TileMath.packTileKey(15, 1, 2)
        val lookup = hashMapOf(key to 3)
        val remapped = TileLookupRemapper.remap(lookup, 15)
        assertEquals(lookup, remapped)
    }

    @Test
    fun emptyLookup_returnsEmpty() {
        val remapped = TileLookupRemapper.remap(hashMapOf(), 15)
        assertTrue(remapped.isEmpty())
    }

    @Test
    fun subdivide_multipleCoarseTiles_fillsEachArea() {
        val keys = listOf(
            TileMath.packTileKey(15, 10, 20),
            TileMath.packTileKey(15, 11, 20),
            TileMath.packTileKey(15, 10, 21),
        )
        val lookup = keys.associateWith { 2 }.let { HashMap(it) }
        val remapped = TileLookupRemapper.remap(lookup, 16)
        assertEquals(12, remapped.size)
        remapped.values.forEach { assertEquals(2, it) }
    }
}
