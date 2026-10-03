package com.tepmex.byokassistedreader.domain

enum class ReferenceKind {
    NAME,
    PLACE,
    TERM,
    ;

    val ru: String
        get() = when (this) {
            NAME -> "Имена собственные"
            PLACE -> "Места"
            TERM -> "Термины"
        }
}

data class ReferenceEntry(val word: String, val explanation: String)

data class ReferencePage(
    val names: List<ReferenceEntry>,
    val places: List<ReferenceEntry>,
    val terms: List<ReferenceEntry>,
)

data class ReferenceSpan(val start: Int, val end: Int, val kind: ReferenceKind)

fun visibleReference(pageText: String, parsed: ReferencePage): ReferencePage {
    val names = clean(parsed.names)
    val places = clean(parsed.places).filter { entry -> names.none { it.word == entry.word } }
    val taken = (names + places).map { it.word }.toSet()
    val terms = clean(parsed.terms).filter { it.word !in taken }
    val longerPool = (names + places + terms).map { it.word }
    return ReferencePage(
        names = independent(pageText, names, longerPool),
        places = independent(pageText, places, longerPool),
        terms = independent(pageText, terms, longerPool),
    )
}

fun alignReference(pageText: String, page: ReferencePage): List<ReferenceSpan> {
    val items = buildList {
        page.names.forEach { add(it.word to ReferenceKind.NAME) }
        page.places.forEach { add(it.word to ReferenceKind.PLACE) }
        page.terms.forEach { add(it.word to ReferenceKind.TERM) }
    }.sortedWith(
        compareByDescending<Pair<String, ReferenceKind>> { it.first.length }
            .thenBy { it.second.ordinal },
    )
    val used = BooleanArray(pageText.length)
    val spans = ArrayList<ReferenceSpan>()
    for ((word, kind) in items) {
        if (word.isEmpty()) continue
        var from = 0
        while (from <= pageText.length - word.length) {
            val at = pageText.indexOf(word, from)
            if (at < 0) break
            val end = at + word.length
            if ((at until end).all { !used[it] }) {
                for (i in at until end) used[i] = true
                spans.add(ReferenceSpan(at, end, kind))
            }
            from = at + 1
        }
    }
    return spans.sortedBy { it.start }
}

private fun clean(entries: List<ReferenceEntry>): List<ReferenceEntry> =
    entries.mapNotNull { entry ->
        val word = entry.word.trim()
        val explanation = entry.explanation.trim()
        if (word.isEmpty() || explanation.isEmpty()) null else ReferenceEntry(word, explanation)
    }.distinctBy { it.word }

private fun independent(
    pageText: String,
    entries: List<ReferenceEntry>,
    pool: List<String>,
): List<ReferenceEntry> = entries.filter { entry ->
    val longer = pool.filter { it.length > entry.word.length }
    hasIndependentOccurrence(pageText, entry.word, longer)
}

private fun hasIndependentOccurrence(page: String, word: String, longer: List<String>): Boolean {
    if (word.isEmpty()) return false
    var from = 0
    while (from <= page.length - word.length) {
        val at = page.indexOf(word, from)
        if (at < 0) return false
        val end = at + word.length
        val buried = longer.any { long -> covers(page, long, at, end) }
        if (!buried) return true
        from = at + 1
    }
    return false
}

private fun covers(page: String, long: String, start: Int, end: Int): Boolean {
    if (long.length <= end - start) return false
    var from = (start - long.length + 1).coerceAtLeast(0)
    while (from <= start) {
        val hit = page.indexOf(long, from)
        if (hit < 0 || hit > start) return false
        if (hit <= start && hit + long.length >= end) return true
        from = hit + 1
    }
    return false
}
