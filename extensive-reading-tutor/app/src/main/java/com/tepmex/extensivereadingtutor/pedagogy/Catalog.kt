package com.tepmex.extensivereadingtutor.pedagogy

import com.tepmex.extensivereadingtutor.books.nfc
import com.tepmex.extensivereadingtutor.domain.ConceptKind
import com.tepmex.extensivereadingtutor.domain.SkillType

data class GrammarEntry(val id: String, val language: String, val label: String, val aliases: List<String>)

object Catalog {
    val diagnostics = setOf(
        "reading.unsupported_inference",
        "reading.missed_explicit_detail",
        "reading.local_expression",
        "reading.textual_accuracy",
        "reading.main_idea",
    )

    val grammar = listOf(
        GrammarEntry("zh.grammar.ba_disposal", "zh", "把", listOf("把")),
        GrammarEntry("zh.grammar.bei_passive", "zh", "被", listOf("被")),
        GrammarEntry("zh.grammar.bi_comparison", "zh", "比", listOf("比")),
        GrammarEntry("zh.grammar.de_complement", "zh", "得", listOf("得")),
        GrammarEntry("zh.grammar.le_perfective", "zh", "了", listOf("了")),
        GrammarEntry("zh.grammar.guo_experiential", "zh", "过", listOf("过", "過")),
        GrammarEntry("zh.grammar.zhe_durative", "zh", "着", listOf("着", "著")),
        GrammarEntry("zh.grammar.shi_de", "zh", "是…的", listOf("是…的")),
    )

    fun grammarById(id: String): GrammarEntry? = grammar.firstOrNull { it.id == id }

    fun grammarByAlias(language: String, label: String): GrammarEntry? {
        val normalized = nfc(label)
        return grammar.firstOrNull { entry ->
            entry.language == language && (entry.label == normalized || entry.aliases.any { nfc(it) == normalized })
        }
    }

    fun surfaceId(language: String, kind: ConceptKind, label: String): String {
        val token = when (kind) {
            ConceptKind.LEXICAL -> "lexical"
            ConceptKind.IDIOM -> "idiom"
            ConceptKind.GRAMMAR -> "grammar-candidate"
            ConceptKind.DIAGNOSTIC -> "diagnostic"
        }
        return "$language:$token:${nfc(label)}"
    }

    fun kindForSkill(skill: SkillType): ConceptKind = when (skill) {
        SkillType.IDIOM_COMPREHENSION -> ConceptKind.IDIOM
        SkillType.GRAMMAR_COMPREHENSION -> ConceptKind.GRAMMAR
        else -> ConceptKind.LEXICAL
    }

    fun knownConceptId(language: String, id: String): Boolean {
        if (grammarById(id)?.language == language) return true
        val prefix = "$language:"
        return id.startsWith(prefix) && id.removePrefix(prefix).substringBefore(':') in setOf("lexical", "idiom", "grammar-candidate")
    }
}

fun skillsCompatible(target: SkillType, proposed: SkillType, evidence: String, studyLanguage: String): Boolean {
    if (proposed == SkillType.ACTIVE_PRODUCTION) {
        return target == SkillType.ACTIVE_PRODUCTION && producesStudyLanguage(evidence, studyLanguage)
    }
    val allowed = when (target) {
        SkillType.ACTIVE_PRODUCTION -> setOf(SkillType.ACTIVE_PRODUCTION)
        SkillType.GRAMMAR_COMPREHENSION -> setOf(
            SkillType.GRAMMAR_COMPREHENSION,
            SkillType.READING_COMPREHENSION,
            SkillType.TEXTUAL_ACCURACY,
        )
        SkillType.VOCABULARY_RECOGNITION -> setOf(
            SkillType.VOCABULARY_RECOGNITION,
            SkillType.READING_COMPREHENSION,
            SkillType.TEXTUAL_ACCURACY,
            SkillType.CONTEXTUAL_INFERENCE,
        )
        SkillType.IDIOM_COMPREHENSION -> setOf(
            SkillType.IDIOM_COMPREHENSION,
            SkillType.VOCABULARY_RECOGNITION,
            SkillType.READING_COMPREHENSION,
            SkillType.TEXTUAL_ACCURACY,
            SkillType.CONTEXTUAL_INFERENCE,
        )
        SkillType.CONTEXTUAL_INFERENCE -> setOf(
            SkillType.CONTEXTUAL_INFERENCE,
            SkillType.READING_COMPREHENSION,
            SkillType.TEXTUAL_ACCURACY,
        )
        SkillType.TEXTUAL_ACCURACY -> setOf(
            SkillType.TEXTUAL_ACCURACY,
            SkillType.READING_COMPREHENSION,
            SkillType.CONTEXTUAL_INFERENCE,
        )
        SkillType.READING_COMPREHENSION -> setOf(
            SkillType.READING_COMPREHENSION,
            SkillType.TEXTUAL_ACCURACY,
            SkillType.CONTEXTUAL_INFERENCE,
            SkillType.VOCABULARY_RECOGNITION,
            SkillType.IDIOM_COMPREHENSION,
            SkillType.GRAMMAR_COMPREHENSION,
        )
    }
    return proposed in allowed
}

fun producesStudyLanguage(evidence: String, studyLanguage: String): Boolean {
    if (studyLanguage != "zh") return evidence.isNotBlank()
    var index = 0
    while (index < evidence.length) {
        val code = evidence.codePointAt(index)
        if (Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN) return true
        index += Character.charCount(code)
    }
    return false
}

fun labelInText(language: String, text: String, label: String): Boolean {
    val normalizedText = nfc(text)
    val needle = nfc(label)
    if (needle.isEmpty() || !normalizedText.contains(needle)) return false
    if (language == "zh") return true
    val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(needle)}(?![\\p{L}\\p{N}])")
    return pattern.containsMatchIn(normalizedText)
}

object OutputLimits {
    const val PREPARE_MAX_TOKENS = 2200
    const val EVALUATE_MAX_TOKENS = 4096
    const val EXPLAIN_MAX_TOKENS = 1200
    const val EXPLANATION_MAX_CHARS = 4000
    const val FIELD_MAX_CHARS = 1200
}

data class LessonPolicyConfig(
    val minQuestions: Int = 1,
    val maxQuestions: Int = 3,
    val maxQuestionsWhenDeeper: Int = 4,
    val maxHints: Int = 2,
    val memoryBudgetBytes: Int = 8 * 1024,
    val precedingCodePoints: Int = 400,
    val safetyMarginTokens: Int = 512,
    val defaultContextWindowTokens: Int = 16384,
    val stableSpanDays: Long = 7,
    val staleAfterDays: Long = 30,
    val tendencyUnits: Int = 3,
    val tendencySessions: Int = 2,
    val maxSelectionCodePoints: Int = 480,
    val maxContinuityNotes: Int = 3,
    val maxRecommendations: Int = 2,
    val maxObservationsPerQuestion: Int = 2,
    val maxSelfReportObservations: Int = 2,
    val maxContinuityInReview: Int = 2,
    val contextCodePoints: Int = 48,
    val prepareMaxTokens: Int = OutputLimits.PREPARE_MAX_TOKENS,
    val evaluateMaxTokens: Int = OutputLimits.EVALUATE_MAX_TOKENS,
    val explainMaxTokens: Int = OutputLimits.EXPLAIN_MAX_TOKENS,
    val continuityNotesEnabled: Boolean = false,
    val allowActiveProduction: Boolean = false,
)
