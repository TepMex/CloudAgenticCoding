package com.tepmex.hanziinfogf14.domain

import com.tepmex.hanziinfogf14.data.Branch

data class PlacedHanzi(
    val q: Int,
    val r: Int,
    val hanzi: String,
    val branchIndex: Int?,
    val component: String?,
    val lookalike: Boolean = false,
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

    /**
     * Component rays keep the first spokes. Each remaining spoke gets one lookalike
     * at distance 1, nearest first. Characters already on the map are skipped.
     */
    fun place(
        center: String,
        branches: List<Branch>,
        lookalikes: List<String> = emptyList(),
    ): List<PlacedHanzi> {
        val visible = visibleBranches(branches)
        val placed = ArrayList<PlacedHanzi>(1 + visible.sumOf { it.characters.size } + lookalikes.size)
        val occupied = HashSet<String>()
        placed.add(PlacedHanzi(0, 0, center, branchIndex = null, component = null))
        occupied.add(center)
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
                occupied.add(hanzi)
            }
        }
        val free = HexDirection.spokes.drop(visible.size)
        var slot = 0
        for (hanzi in lookalikes) {
            if (slot >= free.size) break
            if (!occupied.add(hanzi)) continue
            val direction = free[slot]
            placed.add(
                PlacedHanzi(
                    q = direction.q,
                    r = direction.r,
                    hanzi = hanzi,
                    branchIndex = null,
                    component = null,
                    lookalike = true,
                ),
            )
            slot += 1
        }
        return placed
    }
}
