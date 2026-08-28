package com.tepmex.ankientertainer.data.hanzi

import androidx.room.Dao
import androidx.room.Query

@Dao
interface HanziDao {
    @Query("SELECT * FROM hanzi WHERE character IN (:characters)")
    suspend fun getHanzi(characters: List<String>): List<HanziEntity>

    @Query("SELECT * FROM greedy_composition WHERE character IN (:characters)")
    suspend fun getGreedyCompositions(characters: List<String>): List<GreedyCompositionEntity>

    @Query("SELECT * FROM variant WHERE sourceCharacter IN (:characters)")
    suspend fun getVariants(characters: List<String>): List<VariantEntity>

    @Query("SELECT * FROM simplification WHERE inputCharacter IN (:characters)")
    suspend fun getSimplifications(characters: List<String>): List<SimplificationEntity>

    @Query(
        """
        SELECT * FROM mnemonic
        WHERE character IN (:characters)
        ORDER BY character ASC, normalizedScore DESC, sourcePriority DESC, source ASC, sourceRecordId ASC
        """,
    )
    suspend fun getMnemonics(characters: List<String>): List<MnemonicEntity>

    @Query("SELECT * FROM dataset_metadata WHERE id = 1 LIMIT 1")
    suspend fun getDatasetMetadata(): DatasetMetadataEntity?
}
