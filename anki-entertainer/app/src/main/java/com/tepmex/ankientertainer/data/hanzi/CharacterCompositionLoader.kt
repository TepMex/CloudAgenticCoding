package com.tepmex.ankientertainer.data.hanzi

/**
 * Offline character composition cards shown ahead of mnemonic/LLM stories.
 * Uses the bundled greedy-component table when present, otherwise MMAH fields.
 */
data class CharacterCompositionCard(
    val character: String,
    val text: String,
)

class CharacterCompositionLoader(
    private val repository: HanziMetadataRepository,
) {
    suspend fun loadCards(vocab: String): List<CharacterCompositionCard> {
        val extraction = HanziQuery.extractUniqueHan(vocab)
        if (extraction.characters.isEmpty()) return emptyList()

        val status = repository.datasetStatus()
        if (!status.available) return emptyList()

        val batch = repository.loadForCharacters(
            characters = extraction.characters,
            truncated = extraction.truncated,
            needsHanzi = true,
            needsVariants = false,
            needsSimplifications = false,
            needsMnemonics = false,
        )

        return extraction.characters.mapNotNull { ch ->
            val meta = batch.byCharacter[ch] ?: return@mapNotNull null
            val text = HanziMetadataFormatter.formatCompositionCard(ch, meta) ?: return@mapNotNull null
            CharacterCompositionCard(character = ch, text = text)
        }
    }

    companion object {
        const val MODEL_LABEL = "local composition"
    }
}
