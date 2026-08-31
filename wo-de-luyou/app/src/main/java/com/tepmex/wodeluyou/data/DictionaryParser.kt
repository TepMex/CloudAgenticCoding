package com.tepmex.wodeluyou.data

object DictionaryParser {
    const val EXPECTED_COLUMNS = 8

    fun parse(tsv: String): DictionaryCatalog {
        val lines = tsv.split('\n')
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "Dictionary TSV is empty" }

        val header = lines.first().split('\t')
        require(header.size >= EXPECTED_COLUMNS) {
            "Expected at least $EXPECTED_COLUMNS header columns, got ${header.size}"
        }

        val entries = lines.drop(1).mapIndexed { index, line ->
            val columns = line.split('\t')
            require(columns.size >= EXPECTED_COLUMNS) {
                "Row ${index + 2} has ${columns.size} columns; expected $EXPECTED_COLUMNS"
            }
            VocabEntry(
                id = index,
                category = columns[0].trim(),
                region = columns[1].trim(),
                russian = columns[2].trim(),
                hanzi = columns[3].trim(),
                pinyin = columns[4].trim(),
                note = columns[5].trim(),
                priorityStars = columns[6].count { it == '★' },
                source = columns[7].trim(),
            )
        }
        require(entries.isNotEmpty()) { "Dictionary TSV has a header but no entries" }
        return DictionaryCatalog(entries)
    }
}
