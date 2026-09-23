package com.tepmex.hanziinfogf14.domain

import com.tepmex.hanziinfogf14.data.Branch
import org.junit.Assert.assertEquals
import org.junit.Test

class HexLayoutTest {
    @Test
    fun spokesPlaceChainsOnDistinctRays() {
        val branches = listOf(
            Branch("女", listOf("孔", "仔")),
            Branch("子", listOf("奶")),
        )
        val placed = HexLayout.place("好", branches)
        assertEquals(Axial(0, 0), axial(placed, "好"))
        assertEquals(Axial(1, 0), axial(placed, "孔"))
        assertEquals(Axial(2, 0), axial(placed, "仔"))
        assertEquals(Axial(-1, 0), axial(placed, "奶"))
        assertEquals(placed.size, placed.map { it.q to it.r }.toSet().size)
    }

    @Test
    fun keepsSixLargestBranches() {
        val branches = (1..7).map { size ->
            Branch("c$size", List(size) { index -> "x$size$index" })
        }
        val visible = HexLayout.visibleBranches(branches)
        assertEquals(listOf("c2", "c3", "c4", "c5", "c6", "c7"), visible.map { it.component })
        val placed = HexLayout.place("中", branches)
        assertEquals(Axial(1, 0), axial(placed, "x20"))
        assertEquals(Axial(2, 0), axial(placed, "x21"))
        assertEquals(6, placed.mapNotNull { it.branchIndex }.toSet().size)
    }

    @Test
    fun pixelRoundTrip() {
        val size = 36f
        for (cell in listOf(Axial(0, 0), Axial(9, 0), Axial(-4, 4), Axial(1, -1), Axial(0, -3))) {
            val (x, y) = HexMath.axialToPixel(cell.q, cell.r, size)
            assertEquals(cell, HexMath.pixelToAxial(x, y, size))
        }
    }

    private fun axial(placed: List<PlacedHanzi>, hanzi: String): Axial {
        val node = placed.first { it.hanzi == hanzi }
        return Axial(node.q, node.r)
    }
}
