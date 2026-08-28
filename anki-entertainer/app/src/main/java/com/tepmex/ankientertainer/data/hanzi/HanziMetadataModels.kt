package com.tepmex.ankientertainer.data.hanzi

data class HanziCharacterMetadata(
    val character: String,
    val decomposition: String?,
    val etymologyType: String?,
    val semanticComponent: String?,
    val phoneticComponent: String?,
    /** Greedy visible parts from the local 3500-character table; empty if unknown. */
    val greedyComponents: List<String> = emptyList(),
    /**
     * Conservative phonetic-semantic flag from the local table.
     * Null when the character is not in that table (MMAH etymology is the fallback).
     */
    val isPhoneticSemantic: Boolean? = null,
    /** Phonetic recorded by the local table when [isPhoneticSemantic] is true. */
    val greedyPhonetic: String? = null,
    /** Targets in deterministic source order for opposite-form lookup. */
    val oppositeTargets: List<OppositeTarget>,
    val simplification: SimplificationInfo?,
    val mnemonics: List<MnemonicInfo>,
)

data class OppositeTarget(
    val character: String,
    val direction: String,
    val isAmbiguous: Boolean,
    val source: String,
    val isPreferred: Boolean,
)

data class SimplificationInfo(
    val simplifiedCharacter: String,
    val traditionalCharacter: String,
    val classification: String,
    val explanation: String,
    val evidenceType: String,
    val confidence: Double,
)

data class MnemonicInfo(
    val story: String,
    val normalizedScore: Double,
    val sourcePriority: Int,
    val source: String,
    val sourceRecordId: String,
)

data class BatchedHanziMetadata(
    val byCharacter: Map<String, HanziCharacterMetadata>,
    val orderedCharacters: List<String>,
    val truncated: Boolean,
    val datasetVersion: String?,
    val schemaVersion: Int?,
)

data class DatasetStatus(
    val available: Boolean,
    val datasetVersion: String?,
    val schemaVersion: Int?,
    val buildTimestamp: String?,
    val message: String,
)
