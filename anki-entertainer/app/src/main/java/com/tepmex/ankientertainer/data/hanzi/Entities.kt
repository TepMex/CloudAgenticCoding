package com.tepmex.ankientertainer.data.hanzi

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "greedy_composition")
data class GreedyCompositionEntity(
    @PrimaryKey val character: String,
    val componentsJson: String,
    val isPhoneticSemantic: Boolean,
    val phonetic: String?,
)

@Entity(tableName = "hanzi")
data class HanziEntity(
    @PrimaryKey val character: String,
    val codePoint: Int,
    val decomposition: String?,
    val etymologyType: String?,
    val etymologyHint: String?,
    val semanticComponent: String?,
    val phoneticComponent: String?,
    val primarySource: String,
    val sourceRecordId: String,
)

@Entity(
    tableName = "variant",
    primaryKeys = ["sourceCharacter", "targetCharacter", "direction", "source"],
)
data class VariantEntity(
    val sourceCharacter: String,
    val targetCharacter: String,
    val direction: String,
    val localeOrStandard: String?,
    val isPreferred: Boolean,
    val isAmbiguous: Boolean,
    val source: String,
    val sourceRecordId: String,
)

@Entity(tableName = "simplification")
data class SimplificationEntity(
    @PrimaryKey val inputCharacter: String,
    val simplifiedCharacter: String,
    val traditionalCharacter: String,
    val classification: String,
    val explanation: String,
    val changedComponentsJson: String?,
    val evidenceType: String,
    val confidence: Double,
    val source: String,
    val sourceRecordId: String,
)

@Entity(
    tableName = "mnemonic",
    indices = [
        Index(value = ["character", "normalizedScore"]),
    ],
)
data class MnemonicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val story: String,
    val language: String,
    val rawScore: Double?,
    val normalizedScore: Double,
    val sourcePriority: Int,
    val source: String,
    val sourceRecordId: String,
    val attribution: String,
    val license: String,
    val contentHash: String,
)

@Entity(tableName = "dataset_metadata")
data class DatasetMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int,
    val datasetVersion: String,
    val buildTimestamp: String,
    val sourceVersionsJson: String,
    val sourceChecksumsJson: String,
    val recordCountsJson: String,
    val licenseIdentifiersJson: String,
    val roomIdentityHash: String?,
)
