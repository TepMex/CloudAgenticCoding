package com.tepmex.wodeluyou.data

data class VocabEntry(
    val id: Int,
    val category: String,
    val region: String,
    val russian: String,
    val hanzi: String,
    val pinyin: String,
    val note: String,
    val priorityStars: Int,
    val source: String,
) {
    val hasRegion: Boolean get() = region.isNotBlank()
    val hasNote: Boolean get() = note.isNotBlank()
}

data class CategoryTile(
    val name: String,
    val count: Int,
)

data class DictionaryCatalog(
    val entries: List<VocabEntry>,
) {
    val categories: List<CategoryTile> = entries
        .groupingBy { it.category }
        .eachCount()
        .let { counts ->
            entries.map { it.category }.distinct().map { name ->
                CategoryTile(name = name, count = counts.getValue(name))
            }
        }

    fun entriesIn(category: String): List<VocabEntry> =
        entries.filter { it.category == category }
            .sortedWith(compareByDescending<VocabEntry> { it.priorityStars }.thenBy { it.id })

    fun search(query: String): List<VocabEntry> {
        val raw = query.trim()
        if (raw.isEmpty()) return emptyList()
        val folded = TextFold.fold(raw)
        return entries.filter { entry ->
            entry.hanzi.contains(raw) ||
                TextFold.fold(entry.pinyin).contains(folded) ||
                TextFold.fold(entry.russian).contains(folded) ||
                TextFold.fold(entry.region).contains(folded) ||
                TextFold.fold(entry.note).contains(folded) ||
                TextFold.fold(entry.category).contains(folded)
        }
    }
}
