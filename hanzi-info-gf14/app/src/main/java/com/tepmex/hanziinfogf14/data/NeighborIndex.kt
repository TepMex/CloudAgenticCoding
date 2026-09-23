package com.tepmex.hanziinfogf14.data

/**
 * One-component substitution index.
 *
 * Character B is a neighbor of A when B's component multiset is A's multiset
 * with exactly one component replaced by a different component. Neighbors that
 * replace the same center component share a branch, in catalog order.
 * Same-multiset pairs (古 / 叶) are not neighbors.
 */
object NeighborIndex {
    fun build(entries: List<Pair<String, List<String>>>): HanziCatalog {
        val components = LinkedHashMap<String, List<String>>(entries.size)
        val restIndex = HashMap<List<String>, MutableList<Pair<String, String>>>()
        for ((hanzi, comps) in entries) {
            components[hanzi] = comps
            val seen = HashSet<String>()
            for (component in comps) {
                if (!seen.add(component)) continue
                val rest = withoutOne(comps, component)
                restIndex.getOrPut(rest) { mutableListOf() }.add(hanzi to component)
            }
        }

        val branches = LinkedHashMap<String, List<Branch>>(entries.size)
        for ((hanzi, comps) in entries) {
            val seen = HashSet<String>()
            val built = mutableListOf<Branch>()
            for (component in comps) {
                if (!seen.add(component)) continue
                val rest = withoutOne(comps, component)
                val neighbors = LinkedHashSet<String>()
                for ((other, added) in restIndex[rest].orEmpty()) {
                    if (other == hanzi || added == component) continue
                    neighbors.add(other)
                }
                if (neighbors.isNotEmpty()) {
                    built.add(Branch(component, neighbors.toList()))
                }
            }
            branches[hanzi] = built
        }
        return HanziCatalog(components, branches)
    }

    private fun withoutOne(comps: List<String>, component: String): List<String> {
        val rest = ArrayList<String>(comps.size)
        var removed = false
        for (item in comps) {
            if (!removed && item == component) {
                removed = true
                continue
            }
            rest.add(item)
        }
        rest.sort()
        return rest
    }
}
