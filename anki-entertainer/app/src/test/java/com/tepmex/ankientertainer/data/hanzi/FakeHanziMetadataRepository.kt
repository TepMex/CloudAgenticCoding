package com.tepmex.ankientertainer.data.hanzi

class FakeHanziMetadataRepository(
    private val data: Map<String, HanziCharacterMetadata> = emptyMap(),
    private val available: Boolean = true,
    private val statusMessage: String = "ok",
) : HanziMetadataRepository {

    var loadCount: Int = 0
        private set

    var lastLoadedCharacters: List<String> = emptyList()
        private set

    override suspend fun loadForCharacters(
        characters: List<String>,
        truncated: Boolean,
        needsHanzi: Boolean,
        needsVariants: Boolean,
        needsSimplifications: Boolean,
        needsMnemonics: Boolean,
    ): BatchedHanziMetadata {
        loadCount++
        lastLoadedCharacters = characters
        return BatchedHanziMetadata(
            byCharacter = characters.mapNotNull { ch -> data[ch]?.let { ch to it } }.toMap(),
            orderedCharacters = characters,
            truncated = truncated,
            datasetVersion = "test",
            schemaVersion = 1,
        )
    }

    override suspend fun datasetStatus(): DatasetStatus = DatasetStatus(
        available = available,
        datasetVersion = if (available) "test" else null,
        schemaVersion = if (available) 1 else null,
        buildTimestamp = if (available) "2026-01-01T00:00:00Z" else null,
        message = statusMessage,
    )
}

fun meta(
    character: String,
    etymologyType: String? = null,
    semantic: String? = null,
    phonetic: String? = null,
    opposites: List<OppositeTarget> = emptyList(),
    simplification: SimplificationInfo? = null,
    mnemonics: List<MnemonicInfo> = emptyList(),
) = HanziCharacterMetadata(
    character = character,
    decomposition = null,
    etymologyType = etymologyType,
    semanticComponent = semantic,
    phoneticComponent = phonetic,
    oppositeTargets = opposites,
    simplification = simplification,
    mnemonics = mnemonics,
)
