package com.tepmex.hanziinfogf14.data

import com.tepmex.hanziinfogf14.domain.Axial
import com.tepmex.hanziinfogf14.domain.HexLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonTest {
    private val catalog: HanziCatalog by lazy {
        CatalogJson.parse(catalogFile().readText())
    }

    @Test
    fun loadsLevelOneList() {
        assertEquals(3500, catalog.componentsByHanzi.size)
        assertEquals(listOf("讠", "龶", "月"), catalog.componentsOf("请"))
    }

    @Test
    fun qingBranchIsOneComponentSwapAlongEast() {
        val branches = catalog.branchesOf("请")
        assertEquals(listOf("讠"), branches.map { it.component })
        assertEquals(listOf("猜", "清", "情", "晴", "睛", "靖", "静", "蜻", "精"), branches.single().characters)
        val placed = HexLayout.place("请", branches)
        branches.single().characters.forEachIndexed { index, hanzi ->
            val node = placed.first { it.hanzi == hanzi }
            assertEquals(Axial(index + 1, 0), Axial(node.q, node.r))
        }
    }

    @Test
    fun haoSplitsByEachCenterComponent() {
        val branches = catalog.branchesOf("好")
        assertEquals(listOf("女", "子"), branches.map { it.component })
        assertEquals("孔", branches[0].characters.first())
        assertEquals("奶", branches[1].characters.first())
        assertTrue(branches[1].characters.contains("她"))
        val placed = HexLayout.place("好", branches)
        assertEquals(Axial(1, 0), axial(placed, "孔"))
        assertEquals(Axial(-1, 0), axial(placed, "奶"))
    }

    @Test
    fun sameMultisetIsNotANeighbor() {
        assertFalse(catalog.branchesOf("古").any { "叶" in it.characters })
        assertFalse(catalog.branchesOf("木").any { "林" in it.characters })
        assertFalse(catalog.branchesOf("林").any { "木" in it.characters })
    }

    @Test
    fun qingAndQingPointAtEachOther() {
        assertTrue(catalog.branchesOf("清").any { "请" in it.characters && it.component == "氵" })
    }

    @Test
    fun everyNeighborIsASingleSubstitutionAndFitsSixSpokes() {
        for ((hanzi, branches) in catalog.branchesByHanzi) {
            assertTrue(branches.size <= 6)
            val center = catalog.componentsOf(hanzi)
            val seen = HashSet<String>()
            for (branch in branches) {
                assertTrue(center.contains(branch.component))
                for (other in branch.characters) {
                    assertTrue(seen.add(other))
                    assertTrue(isSingleSubstitution(center, catalog.componentsOf(other), branch.component))
                }
            }
        }
    }

    @Test
    fun emptyDecompositionHasNoBranches() {
        assertTrue(catalog.componentsOf("比").isEmpty())
        assertTrue(catalog.branchesOf("比").isEmpty())
    }

    private fun isSingleSubstitution(center: List<String>, other: List<String>, replaced: String): Boolean {
        if (center.size != other.size || center.isEmpty()) return false
        val rest = center.toMutableList()
        if (!rest.remove(replaced)) return false
        val added = other.toMutableList()
        for (item in rest) {
            if (!added.remove(item)) return false
        }
        return added.size == 1 && added[0] != replaced
    }

    private fun axial(placed: List<com.tepmex.hanziinfogf14.domain.PlacedHanzi>, hanzi: String): Axial {
        val node = placed.first { it.hanzi == hanzi }
        return Axial(node.q, node.r)
    }

    private fun catalogFile(): File {
        val candidates = listOf(
            File("src/main/assets/hanzi_components_gf0014_6152.json"),
            File("app/src/main/assets/hanzi_components_gf0014_6152.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("catalog json missing from ${File(".").absolutePath}")
    }
}
