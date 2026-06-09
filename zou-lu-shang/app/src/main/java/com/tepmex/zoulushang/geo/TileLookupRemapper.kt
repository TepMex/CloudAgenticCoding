package com.tepmex.zoulushang.geo

object TileLookupRemapper {
    fun remap(lookup: HashMap<Long, Int>, targetZoom: Int): HashMap<Long, Int> {
        if (lookup.isEmpty()) return lookup
        val result = HashMap<Long, Int>()
        for ((key, count) in lookup) {
            val (storedZoom, x, y) = TileMath.unpackTileKey(key)
            if (storedZoom == targetZoom) {
                result.merge(key, count, Int::plus)
                continue
            }
            if (targetZoom > storedZoom) {
                val factor = 1 shl (targetZoom - storedZoom)
                val baseX = x shl (targetZoom - storedZoom)
                val baseY = y shl (targetZoom - storedZoom)
                for (dx in 0 until factor) {
                    for (dy in 0 until factor) {
                        val childKey = TileMath.packTileKey(targetZoom, baseX + dx, baseY + dy)
                        result[childKey] = count
                    }
                }
            } else {
                val shift = storedZoom - targetZoom
                val parentKey = TileMath.packTileKey(targetZoom, x shr shift, y shr shift)
                result.merge(parentKey, count, Int::plus)
            }
        }
        return result
    }
}
