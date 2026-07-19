package com.tepmex.ankientertainer.data.hanzi

/**
 * IDS tree structural differ used by unit tests (mirrors the offline build pipeline algorithm).
 * Runtime app reads precomputed rows from the bundled database; this stays test-local.
 */
object IdsStructuralDiffer {
    private val arity = mapOf(
        '\u2FF0' to 2,
        '\u2FF1' to 2,
        '\u2FF2' to 3,
        '\u2FF3' to 3,
        '\u2FF4' to 2,
        '\u2FF5' to 2,
        '\u2FF6' to 2,
        '\u2FF7' to 2,
        '\u2FF8' to 2,
        '\u2FF9' to 2,
        '\u2FFA' to 2,
        '\u2FFB' to 2,
    )

    data class Node(val value: String, val children: List<Node> = emptyList()) {
        val isOperator: Boolean get() = value.length == 1 && arity.containsKey(value[0])
        fun leafString(): String =
            if (children.isEmpty()) value else value + children.joinToString("") { it.leafString() }
    }

    data class Change(
        val traditionalComponent: String,
        val simplifiedComponent: String,
        val path: List<Int>,
    )

    data class Result(
        val classification: String,
        val evidenceType: String,
        val changedComponents: List<Change>,
    )

    fun compare(
        traditionalIds: String?,
        simplifiedIds: String?,
        traditionalChar: String,
        simplifiedChar: String,
        ambiguousMapping: Boolean = false,
        curated: Result? = null,
    ): Result {
        if (curated != null) return curated
        if (ambiguousMapping) {
            return Result("AMBIGUOUS_VARIANT_MAPPING", "derived", emptyList())
        }
        if (traditionalChar == simplifiedChar) {
            return Result("UNCHANGED", "derived", emptyList())
        }
        val t = parse(traditionalIds) ?: return Result("UNKNOWN", "derived", emptyList())
        val s = parse(simplifiedIds) ?: return Result("UNKNOWN", "derived", emptyList())
        if (equal(t, s)) return Result("UNCHANGED", "derived", emptyList())
        if (t.isOperator && s.isOperator && t.value == s.value) {
            val changes = diffSame(t, s, emptyList())
                ?: return Result("STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT", "derived", emptyList())
            return when (changes.size) {
                1 -> Result("SINGLE_COMPONENT_REPLACEMENT", "derived", changes)
                else -> Result("MULTIPLE_COMPONENT_REPLACEMENTS", "derived", changes)
            }
        }
        return Result("STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT", "derived", emptyList())
    }

    fun parse(ids: String?): Node? {
        if (ids.isNullOrBlank() || ids.contains('？') || ids.contains('?')) return null
        val chars = ids.toList()
        var index = 0
        fun parseNode(): Node? {
            if (index >= chars.size) return null
            val ch = chars[index++]
            val n = arity[ch]
            if (n == null) return Node(ch.toString())
            val children = ArrayList<Node>(n)
            repeat(n) {
                val child = parseNode() ?: return null
                children.add(child)
            }
            return Node(ch.toString(), children)
        }
        val root = parseNode() ?: return null
        return if (index == chars.size) root else null
    }

    private fun equal(a: Node, b: Node): Boolean {
        if (a.value != b.value || a.children.size != b.children.size) return false
        return a.children.indices.all { equal(a.children[it], b.children[it]) }
    }

    private fun diffSame(t: Node, s: Node, path: List<Int>): List<Change>? {
        if (!t.isOperator && !s.isOperator) {
            return if (t.value == s.value) emptyList()
            else listOf(Change(t.value, s.value, path))
        }
        if (t.isOperator != s.isOperator || t.value != s.value || t.children.size != s.children.size) {
            return null
        }
        val changes = mutableListOf<Change>()
        for (i in t.children.indices) {
            val tc = t.children[i]
            val sc = s.children[i]
            val childPath = path + i
            if (equal(tc, sc)) continue
            if ((tc.isOperator || sc.isOperator) && tc.isOperator == sc.isOperator && tc.value == sc.value) {
                val nested = diffSame(tc, sc, childPath)
                if (nested == null) {
                    changes.add(Change(tc.leafString(), sc.leafString(), childPath))
                } else {
                    changes.addAll(nested)
                }
            } else if (!tc.isOperator && !sc.isOperator) {
                changes.add(Change(tc.value, sc.value, childPath))
            } else {
                changes.add(Change(tc.leafString(), sc.leafString(), childPath))
            }
        }
        return changes
    }
}
