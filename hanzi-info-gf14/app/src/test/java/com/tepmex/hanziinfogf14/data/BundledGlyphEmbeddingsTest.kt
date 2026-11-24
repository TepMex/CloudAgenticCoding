package com.tepmex.hanziinfogf14.data

import com.tepmex.hanziinfogf14.domain.Axial
import com.tepmex.hanziinfogf14.domain.HexLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledGlyphEmbeddingsTest {
    private companion object {
        // Free spokes of 请, nearest first: west, northeast, southwest, northwest, southeast.
        val QING_LOOKALIKES = listOf("诉", "讳", "询", "祈", "谢")
    }

    @Test
    fun catalogOrderNormsAndQingFreeSpokes() {
        val catalog = CatalogJson.parse(asset("hanzi_components_gf0014_6152.json").readText())
        val index = GlyphEmbeddingIndex.read(asset("glyph_embeddings.bin").readBytes())
        val order = catalog.componentsByHanzi.keys.toList()
        assertEquals(order, index.hanzi)
        assertEquals(3500, index.hanzi.size)
        assertEquals(768, index.dim)
        assertEquals(70, index.visionTokens)
        assertEquals(224, index.imageSize)
        assertEquals(156, index.fontPx)
        assertEquals("一", index.hanzi.first())
        assertEquals("矗", index.hanzi.last())
        assertEquals("末", index.nearest("未", limit = 1, exclude = setOf("未")).single().hanzi)
        assertEquals("土", index.nearest("士", limit = 1, exclude = setOf("士")).single().hanzi)
        assertEquals("已", index.nearest("己", limit = 1, exclude = setOf("己")).single().hanzi)
        for (row in index.hanzi.indices) {
            var sum = 0.0
            val offset = row * index.dim
            for (column in 0 until index.dim) {
                val value = index.matrix[offset + column].toDouble()
                assertTrue(value.isFinite())
                sum += value * value
            }
            assertEquals(1.0, sum, 1e-3)
        }
        val center = "请"
        val branches = catalog.branchesOf(center)
        val excluded = HexLayout.visibleBranches(branches).flatMap { it.characters }.toSet() + center
        val nearest = index.nearest(center, limit = 5, exclude = excluded)
        assertEquals(5, nearest.size)
        assertTrue(nearest.zipWithNext().all { it.first.distance <= it.second.distance })
        val placed = HexLayout.place(center, branches, nearest.map { it.hanzi })
        assertEquals(Axial(1, 0), axial(placed, "猜"))
        val free = listOf(
            Axial(-1, 0),
            Axial(1, -1),
            Axial(-1, 1),
            Axial(0, -1),
            Axial(0, 1),
        )
        nearest.forEachIndexed { indexOnSpoke, scored ->
            val node = placed.first { it.hanzi == scored.hanzi }
            assertEquals(free[indexOnSpoke], Axial(node.q, node.r))
            assertTrue(node.lookalike)
            assertTrue(scored.hanzi !in excluded)
        }
        assertEquals(QING_LOOKALIKES, nearest.map { it.hanzi })
    }

    private fun asset(name: String): File {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $name in ${candidates.joinToString { it.path }}")
    }

    private fun axial(placed: List<com.tepmex.hanziinfogf14.domain.PlacedHanzi>, hanzi: String): Axial {
        val node = placed.first { it.hanzi == hanzi }
        return Axial(node.q, node.r)
    }
}
