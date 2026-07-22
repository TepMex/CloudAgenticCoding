package com.tepmex.ankientertainer.data.hanzi

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline batched Hanzi metadata access. Hides Room/SQLite details from the template engine.
 */
interface HanziMetadataRepository {
    suspend fun loadForCharacters(
        characters: List<String>,
        truncated: Boolean,
        needsHanzi: Boolean,
        needsVariants: Boolean,
        needsSimplifications: Boolean,
        needsMnemonics: Boolean,
    ): BatchedHanziMetadata

    suspend fun datasetStatus(): DatasetStatus
}

class RoomHanziMetadataRepository(
    private val databaseProvider: () -> HanziMetadataDatabase?,
    private val cacheSize: Int = 128,
) : HanziMetadataRepository {

    /**
     * Cache entries remember which optional slices were loaded so a later request
     * for mnemonics (or variants, etc.) is not served from a partial row.
     */
    private data class CacheEntry(
        val meta: HanziCharacterMetadata,
        val loadedHanzi: Boolean,
        val loadedVariants: Boolean,
        val loadedSimplifications: Boolean,
        val loadedMnemonics: Boolean,
    )

    private val cache = LruCache<String, CacheEntry>(cacheSize)
    private var openFailed = false
    private var failureMessage: String? = null

    private fun dbOrNull(): HanziMetadataDatabase? {
        if (openFailed) return null
        return try {
            databaseProvider()
        } catch (e: Exception) {
            openFailed = true
            failureMessage = e.message ?: "Failed to open Hanzi metadata database"
            null
        }
    }

    override suspend fun datasetStatus(): DatasetStatus = withContext(Dispatchers.IO) {
        val db = dbOrNull()
            ?: return@withContext DatasetStatus(
                available = false,
                datasetVersion = null,
                schemaVersion = null,
                buildTimestamp = null,
                message = failureMessage
                    ?: "Hanzi metadata database is unavailable. {QUERY} still works; metadata placeholders are empty.",
            )
        try {
            val meta = db.hanziDao().getDatasetMetadata()
            if (meta == null) {
                DatasetStatus(
                    available = false,
                    datasetVersion = null,
                    schemaVersion = null,
                    buildTimestamp = null,
                    message = "Hanzi metadata database has no dataset_metadata row.",
                )
            } else {
                DatasetStatus(
                    available = true,
                    datasetVersion = meta.datasetVersion,
                    schemaVersion = meta.schemaVersion,
                    buildTimestamp = meta.buildTimestamp,
                    message = "Offline Hanzi dataset ${meta.datasetVersion} (schema ${meta.schemaVersion})",
                )
            }
        } catch (e: Exception) {
            openFailed = true
            failureMessage = e.message ?: "Hanzi metadata database error"
            DatasetStatus(
                available = false,
                datasetVersion = null,
                schemaVersion = null,
                buildTimestamp = null,
                message = failureMessage!!,
            )
        }
    }

    override suspend fun loadForCharacters(
        characters: List<String>,
        truncated: Boolean,
        needsHanzi: Boolean,
        needsVariants: Boolean,
        needsSimplifications: Boolean,
        needsMnemonics: Boolean,
    ): BatchedHanziMetadata = withContext(Dispatchers.IO) {
        if (characters.isEmpty()) {
            return@withContext BatchedHanziMetadata(
                byCharacter = emptyMap(),
                orderedCharacters = emptyList(),
                truncated = truncated,
                datasetVersion = null,
                schemaVersion = null,
            )
        }

        val cached = linkedMapOf<String, HanziCharacterMetadata>()
        val missing = mutableListOf<String>()
        for (ch in characters) {
            val hit = cache.get(ch)
            val usable = hit != null &&
                (!needsHanzi || hit.loadedHanzi) &&
                (!needsVariants || hit.loadedVariants) &&
                (!needsSimplifications || hit.loadedSimplifications) &&
                (!needsMnemonics || hit.loadedMnemonics)
            if (hit != null && usable) {
                cached[ch] = hit.meta
            } else {
                missing.add(ch)
            }
        }

        val db = dbOrNull()
        if (db == null) {
            return@withContext BatchedHanziMetadata(
                byCharacter = cached,
                orderedCharacters = characters,
                truncated = truncated,
                datasetVersion = null,
                schemaVersion = null,
            )
        }

        try {
            val dao = db.hanziDao()
            val meta = dao.getDatasetMetadata()
            if (missing.isNotEmpty()) {
                val loadHanzi = needsHanzi || needsMnemonics || needsSimplifications
                val hanziRows = if (loadHanzi) {
                    dao.getHanzi(missing).associateBy { it.character }
                } else {
                    emptyMap()
                }
                val variantRows = if (needsVariants) {
                    dao.getVariants(missing).groupBy { it.sourceCharacter }
                } else {
                    emptyMap()
                }
                val simplRows = if (needsSimplifications) {
                    dao.getSimplifications(missing).associateBy { it.inputCharacter }
                } else {
                    emptyMap()
                }
                val mnemoRows = if (needsMnemonics) {
                    dao.getMnemonics(missing).groupBy { it.character }
                } else {
                    emptyMap()
                }

                for (ch in missing) {
                    val previous = cache.get(ch)
                    val h = if (loadHanzi) hanziRows[ch] else null
                    val opposites = if (needsVariants) {
                        (variantRows[ch].orEmpty())
                            .sortedWith(
                                compareByDescending<VariantEntity> { it.isPreferred }
                                    .thenBy { it.source }
                                    .thenBy { it.sourceRecordId }
                                    .thenBy { it.targetCharacter },
                            )
                            .map {
                                OppositeTarget(
                                    character = it.targetCharacter,
                                    direction = it.direction,
                                    isAmbiguous = it.isAmbiguous,
                                    source = it.source,
                                    isPreferred = it.isPreferred,
                                )
                            }
                    } else {
                        previous?.meta?.oppositeTargets.orEmpty()
                    }
                    val simpl = if (needsSimplifications) {
                        simplRows[ch]?.let {
                            SimplificationInfo(
                                simplifiedCharacter = it.simplifiedCharacter,
                                traditionalCharacter = it.traditionalCharacter,
                                classification = it.classification,
                                explanation = it.explanation,
                                evidenceType = it.evidenceType,
                                confidence = it.confidence,
                            )
                        }
                    } else {
                        previous?.meta?.simplification
                    }
                    val mnemos = if (needsMnemonics) {
                        mnemoRows[ch].orEmpty().map {
                            MnemonicInfo(
                                story = it.story,
                                normalizedScore = it.normalizedScore,
                                sourcePriority = it.sourcePriority,
                                source = it.source,
                                sourceRecordId = it.sourceRecordId,
                            )
                        }
                    } else {
                        previous?.meta?.mnemonics.orEmpty()
                    }
                    val assembled = HanziCharacterMetadata(
                        character = ch,
                        decomposition = h?.decomposition ?: previous?.meta?.decomposition,
                        etymologyType = h?.etymologyType ?: previous?.meta?.etymologyType,
                        semanticComponent = h?.semanticComponent ?: previous?.meta?.semanticComponent,
                        phoneticComponent = h?.phoneticComponent ?: previous?.meta?.phoneticComponent,
                        oppositeTargets = opposites,
                        simplification = simpl,
                        mnemonics = mnemos,
                    )
                    val entry = CacheEntry(
                        meta = assembled,
                        loadedHanzi = (previous?.loadedHanzi == true) || loadHanzi,
                        loadedVariants = (previous?.loadedVariants == true) || needsVariants,
                        loadedSimplifications = (previous?.loadedSimplifications == true) ||
                            needsSimplifications,
                        loadedMnemonics = (previous?.loadedMnemonics == true) || needsMnemonics,
                    )
                    cache.put(ch, entry)
                    cached[ch] = assembled
                }
            }

            BatchedHanziMetadata(
                byCharacter = characters.mapNotNull { ch -> cached[ch]?.let { ch to it } }.toMap(),
                orderedCharacters = characters,
                truncated = truncated,
                datasetVersion = meta?.datasetVersion,
                schemaVersion = meta?.schemaVersion,
            )
        } catch (e: Exception) {
            openFailed = true
            failureMessage = e.message ?: "Hanzi metadata query failed"
            BatchedHanziMetadata(
                byCharacter = cached,
                orderedCharacters = characters,
                truncated = truncated,
                datasetVersion = null,
                schemaVersion = null,
            )
        }
    }
}

/** Test double / failure injection: always empty metadata. */
class UnavailableHanziMetadataRepository(
    private val message: String = "Hanzi metadata database unavailable",
) : HanziMetadataRepository {
    override suspend fun loadForCharacters(
        characters: List<String>,
        truncated: Boolean,
        needsHanzi: Boolean,
        needsVariants: Boolean,
        needsSimplifications: Boolean,
        needsMnemonics: Boolean,
    ): BatchedHanziMetadata = BatchedHanziMetadata(
        byCharacter = emptyMap(),
        orderedCharacters = characters,
        truncated = truncated,
        datasetVersion = null,
        schemaVersion = null,
    )

    override suspend fun datasetStatus(): DatasetStatus = DatasetStatus(
        available = false,
        datasetVersion = null,
        schemaVersion = null,
        buildTimestamp = null,
        message = message,
    )
}
