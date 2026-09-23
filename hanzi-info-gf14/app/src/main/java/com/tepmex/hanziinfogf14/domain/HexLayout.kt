package com.tepmex.hanziinfogf14.domain

import com.tepmex.hanziinfogf14.data.Branch

data class PlacedHanzi(
    val q: Int,
    val r: Int,
    val hanzi: String,
    val branchIndex: Int?,
    val component: String?,
)

object HexLayout {
    const val MAX_BRANCHES = 6

    val directionArrows: List<String> = listOf("→", "←", "↗", "↙", "↖", "↘")

    /**
     * At most six branches. Extra branches keep the six with the most characters;
     * ties keep the original component order, and the kept branches stay in that order.
     */
    fun visibleBranches(branches: List<Branch>): List<Branch> {
        if (branches.size <= MAX_BRANCHES) return branches
        return branches.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Branch>> { it.value.characters.size }
                    .thenBy { it.index },
            )
            .take(MAX_BRANCHES)
            .sortedBy { it.index }
            .map { it.value }
    }

    fun place(center: String, branches: List<Branch>): List<PlacedHanzi> {
        val visible = visibleBranches(branches)
        val placed = ArrayList<PlacedHanzi>(1 + visible.sumOf { it.characters.size })
        placed.add(PlacedHanzi(0, 0, center, branchIndex = null, component = null))
        visible.forEachIndexed { index, branch ->
            val direction = HexDirection.spokes[index]
            branch.characters.forEachIndexed { step, hanzi ->
                val distance = step + 1
                placed.add(
                    PlacedHanzi(
                        q = direction.q * distance,
                        r = direction.r * distance,
                        hanzi = hanzi,
                        branchIndex = index,
                        component = branch.component,
                    ),
                )
            }
        }
        return placed
    }
}
