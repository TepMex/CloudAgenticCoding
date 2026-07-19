package com.tepmex.ankientertainer.data.hanzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsStructuralDifferTest {
    @Test
    fun unchanged() {
        val r = IdsStructuralDiffer.compare("\u2ff0\u5973\u5b50", "\u2ff0\u5973\u5b50", "\u597d", "\u597d")
        assertEquals("UNCHANGED", r.classification)
    }

    @Test
    fun singleComponentReplacement() {
        val r = IdsStructuralDiffer.compare("\u2ff0\u8a00\u9752", "\u2ff0\u8ba0\u9752", "\u8acb", "\u8bf7")
        assertEquals("SINGLE_COMPONENT_REPLACEMENT", r.classification)
        assertEquals(1, r.changedComponents.size)
        assertEquals("\u8a00", r.changedComponents[0].traditionalComponent)
        assertEquals("\u8ba0", r.changedComponents[0].simplifiedComponent)
        assertEquals(listOf(0), r.changedComponents[0].path)
        assertEquals("derived", r.evidenceType)
    }

    @Test
    fun multipleComponentReplacements() {
        val r = IdsStructuralDiffer.compare("\u2ff0\u8a00\u514c", "\u2ff0\u8ba0\u5151", "\u8aaa", "\u8bf4")
        assertEquals("MULTIPLE_COMPONENT_REPLACEMENTS", r.classification)
        assertTrue(r.changedComponents.size >= 2)
    }

    @Test
    fun changedRootStructure() {
        val r = IdsStructuralDiffer.compare("\u2ff1\u65e5\u6708", "\u2ff0\u53e3\u65a4", "A", "B")
        assertEquals("STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT", r.classification)
    }

    @Test
    fun missingDecomposition() {
        val r = IdsStructuralDiffer.compare(null, "\u2ff0\u53e3\u65a4", "\u807d", "\u542c")
        assertEquals("UNKNOWN", r.classification)
    }

    @Test
    fun ambiguousMapping() {
        val r = IdsStructuralDiffer.compare("\u2ff1\u767a\u5f13", "x", "\u767c", "\u53d1", ambiguousMapping = true)
        assertEquals("AMBIGUOUS_VARIANT_MAPPING", r.classification)
    }

    @Test
    fun curatedTakesPrecedenceOverDerived() {
        val curated = IdsStructuralDiffer.Result(
            classification = "SINGLE_COMPONENT_REPLACEMENT",
            evidenceType = "curated",
            changedComponents = emptyList(),
        )
        val r = IdsStructuralDiffer.compare(
            "\u2ff1\u65e5\u6708",
            "\u2ff0\u53e3\u65a4",
            "\u8c93",
            "\u732b",
            curated = curated,
        )
        assertEquals("curated", r.evidenceType)
        assertEquals("SINGLE_COMPONENT_REPLACEMENT", r.classification)
    }
}
