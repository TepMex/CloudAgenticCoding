package com.tepmex.ankientertainer.data.hanzi

/**
 * Deterministic plain-text formatting for Hanzi prompt placeholders.
 * No Android UI dependencies.
 */
object HanziMetadataFormatter {
    const val MAX_MNEMONICS_PER_CHAR = 5
    const val TRUNCATION_LINE = "[Metadata truncated after 20 unique Han characters.]"

    fun formatOpposite(
        ordered: List<String>,
        meta: Map<String, HanziCharacterMetadata>,
        truncated: Boolean = false,
    ): String {
        if (ordered.isEmpty()) return appendTruncation("", truncated)
        val lines = ordered.map { ch ->
            val targets = meta[ch]?.oppositeTargets.orEmpty()
            when {
                targets.isEmpty() -> "$ch → $ch (same in both)"
                targets.size == 1 && targets[0].character == ch -> "$ch → $ch (same in both)"
                else -> {
                    val uniqueTargets = targets.map { it.character }.distinct()
                    val joined = uniqueTargets.joinToString(" / ")
                    val ambiguous = targets.any { it.isAmbiguous } || uniqueTargets.size > 1
                    if (ambiguous && uniqueTargets.size > 1) {
                        "$ch → $joined (context-dependent)"
                    } else if (uniqueTargets.size == 1 && uniqueTargets[0] == ch) {
                        "$ch → $ch (same in both)"
                    } else {
                        "$ch → $joined"
                    }
                }
            }
        }
        return appendTruncation(lines.joinToString("\n"), truncated)
    }

    fun formatSemantic(ordered: List<String>, meta: Map<String, HanziCharacterMetadata>, truncated: Boolean): String {
        val lines = ordered.mapNotNull { ch ->
            val m = meta[ch] ?: return@mapNotNull null
            if (m.etymologyType != "pictophonetic") return@mapNotNull null
            val semantic = m.semanticComponent ?: return@mapNotNull null
            "$ch: semantic component $semantic"
        }
        return appendTruncation(lines.joinToString("\n"), truncated)
    }

    fun formatPhonetic(ordered: List<String>, meta: Map<String, HanziCharacterMetadata>, truncated: Boolean): String {
        val lines = ordered.mapNotNull { ch ->
            val m = meta[ch] ?: return@mapNotNull null
            if (m.etymologyType != "pictophonetic") return@mapNotNull null
            val phonetic = m.phoneticComponent ?: return@mapNotNull null
            "$ch: phonetic component $phonetic"
        }
        return appendTruncation(lines.joinToString("\n"), truncated)
    }

    fun formatMnemonics(ordered: List<String>, meta: Map<String, HanziCharacterMetadata>, truncated: Boolean): String {
        val blocks = ordered.mapNotNull { ch ->
            val stories = meta[ch]?.mnemonics.orEmpty().take(MAX_MNEMONICS_PER_CHAR)
            if (stories.isEmpty()) return@mapNotNull null
            buildString {
                append(ch)
                append(":\n")
                stories.forEachIndexed { index, m ->
                    append(index + 1)
                    append(". ")
                    append(m.story)
                    if (index != stories.lastIndex) append('\n')
                }
            }
        }
        return appendTruncation(blocks.joinToString("\n\n"), truncated)
    }

    fun formatSimplHistory(ordered: List<String>, meta: Map<String, HanziCharacterMetadata>, truncated: Boolean): String {
        val blocks = ordered.map { ch ->
            val simpl = meta[ch]?.simplification
            if (simpl == null) {
                "$ch → $ch\nNo standard simplified/traditional difference found."
            } else {
                simpl.explanation.trim()
            }
        }
        return appendTruncation(blocks.joinToString("\n\n"), truncated)
    }

    /**
     * One local card for a single Han character: greedy components first,
     * then whether it is phonetic-semantic and which phonetic if so.
     * Returns null when neither the greedy table nor MMAH decomposition is present.
     */
    fun formatCompositionCard(character: String, meta: HanziCharacterMetadata): String? {
        val components = meta.greedyComponents.ifEmpty {
            listOfNotNull(meta.decomposition?.takeIf { it.isNotBlank() })
        }
        if (components.isEmpty()) return null

        val pictophonetic = meta.isPhoneticSemantic
            ?: (meta.etymologyType == "pictophonetic")
        val phonetic = when (meta.isPhoneticSemantic) {
            true -> meta.greedyPhonetic?.takeIf { it.isNotBlank() }
            false -> null
            null -> meta.phoneticComponent.takeIf { pictophonetic && !it.isNullOrBlank() }
        }

        return buildString {
            append(character)
            append('\n')
            append("Composition: ")
            append(components.joinToString(" + "))
            append('\n')
            if (pictophonetic) {
                append("Phonetic-semantic: yes")
                if (!phonetic.isNullOrBlank()) {
                    append('\n')
                    append("Phonetic: ")
                    append(phonetic)
                }
            } else {
                append("Phonetic-semantic: no")
            }
        }
    }

    private fun appendTruncation(body: String, truncated: Boolean): String {
        if (!truncated) return body
        return if (body.isEmpty()) TRUNCATION_LINE else body + "\n" + TRUNCATION_LINE
    }
}
