package com.tepmex.ankientertainer.data.hanzi

/**
 * Offline fallback content when a remote LLM is unavailable or misconfigured.
 * Loads up to [limit] mnemonic stories from the local Hanzi metadata database
 * for Han compounds and characters in the vocabulary query.
 *
 * Lookup order: contiguous multi-character Han runs (compound words) first,
 * then individual Han characters (first-occurrence order, ranked score within each key).
 */
data class OfflineMnemonicStory(
    val character: String,
    val text: String,
)

class OfflineMnemonicFallback(
    private val repository: HanziMetadataRepository,
    private val limit: Int = DEFAULT_LIMIT,
) {
    suspend fun loadStories(vocab: String): List<OfflineMnemonicStory> {
        val extraction = HanziQuery.extractMnemonicLookupKeys(vocab)
        if (extraction.characters.isEmpty()) return emptyList()

        val status = repository.datasetStatus()
        if (!status.available) return emptyList()

        val batch = repository.loadForCharacters(
            characters = extraction.characters,
            truncated = extraction.truncated,
            needsHanzi = false,
            needsVariants = false,
            needsSimplifications = false,
            needsMnemonics = true,
        )

        val out = ArrayList<OfflineMnemonicStory>(limit)
        for (key in batch.orderedCharacters) {
            val mnemonics = batch.byCharacter[key]?.mnemonics.orEmpty()
            for (m in mnemonics) {
                if (out.size >= limit) return out
                out.add(
                    OfflineMnemonicStory(
                        character = key,
                        text = "$key — ${m.story}",
                    ),
                )
            }
        }
        return out
    }

    companion object {
        const val DEFAULT_LIMIT = 5
        const val MODEL_LABEL = "local mnemonic"
    }
}
