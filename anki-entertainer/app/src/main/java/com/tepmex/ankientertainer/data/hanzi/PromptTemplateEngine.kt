package com.tepmex.ankientertainer.data.hanzi

data class PromptExpansionResult(
    val prompt: String,
    val warnings: List<String>,
)

interface PromptTemplateEngine {
    suspend fun expand(
        template: String,
        query: String,
    ): PromptExpansionResult
}

object PromptPlaceholders {
    const val QUERY = "QUERY"
    const val OPPOSITE = "OPPOSITE"
    const val SIMPL_HISTORY = "SIMPL_HISTORY"
    const val MNEMO_EXAMPLES = "MNEMO_EXAMPLES"
    const val SEMANTIC = "SEMANTIC"
    const val PHONETIC = "PHONETIC"

    val SUPPORTED: Set<String> = setOf(
        QUERY,
        OPPOSITE,
        SIMPL_HISTORY,
        MNEMO_EXAMPLES,
        SEMANTIC,
        PHONETIC,
    )

    fun token(name: String): String = "{$name}"
}

class DefaultPromptTemplateEngine(
    private val repository: HanziMetadataRepository,
    private val maxUniqueHan: Int = HanziQuery.DEFAULT_MAX_UNIQUE,
) : PromptTemplateEngine {

    override suspend fun expand(template: String, query: String): PromptExpansionResult {
        val tokens = findBraceTokens(template)
        val warnings = mutableListOf<String>()
        val unknown = tokens.filter { it !in PromptPlaceholders.SUPPORTED }.distinct()
        if (unknown.isNotEmpty()) {
            warnings.add(
                "Unknown placeholders left unchanged: " +
                    unknown.joinToString(", ") { PromptPlaceholders.token(it) },
            )
        }

        val needed = tokens.filter { it in PromptPlaceholders.SUPPORTED }.toSet()
        val needsMetadata = needed.any { it != PromptPlaceholders.QUERY }

        val resolved = linkedMapOf<String, String>()
        resolved[PromptPlaceholders.QUERY] = query

        if (needsMetadata) {
            val charExtraction = HanziQuery.extractUniqueHan(query, maxUniqueHan)
            val mnemonicExtraction = if (PromptPlaceholders.MNEMO_EXAMPLES in needed) {
                HanziQuery.extractMnemonicLookupKeys(query, maxUniqueHan)
            } else {
                null
            }
            // Prefer mnemonic key order (compounds first) when loading the batch.
            val loadKeys = LinkedHashSet<String>().apply {
                mnemonicExtraction?.characters?.let { addAll(it) }
                addAll(charExtraction.characters)
            }.toList()
            val truncated = charExtraction.truncated || (mnemonicExtraction?.truncated == true)

            val status = repository.datasetStatus()
            if (!status.available) {
                warnings.add(status.message)
                for (name in needed) {
                    if (name != PromptPlaceholders.QUERY) {
                        resolved[name] = ""
                    }
                }
            } else {
                val batch = try {
                    repository.loadForCharacters(
                        characters = loadKeys,
                        truncated = truncated,
                        needsHanzi = PromptPlaceholders.SEMANTIC in needed ||
                            PromptPlaceholders.PHONETIC in needed,
                        needsVariants = PromptPlaceholders.OPPOSITE in needed,
                        needsSimplifications = PromptPlaceholders.SIMPL_HISTORY in needed,
                        needsMnemonics = PromptPlaceholders.MNEMO_EXAMPLES in needed,
                    )
                } catch (e: Exception) {
                    warnings.add(
                        "Hanzi metadata lookup failed (${e.message ?: "error"}). " +
                            "Metadata placeholders are empty; {QUERY} is unchanged.",
                    )
                    null
                }

                if (batch == null) {
                    for (name in needed) {
                        if (name != PromptPlaceholders.QUERY) {
                            resolved[name] = ""
                        }
                    }
                } else {
                    val meta = batch.byCharacter
                    val orderedChars = charExtraction.characters
                    val orderedMnemo = mnemonicExtraction?.characters.orEmpty()

                    if (PromptPlaceholders.OPPOSITE in needed) {
                        resolved[PromptPlaceholders.OPPOSITE] =
                            HanziMetadataFormatter.formatOpposite(
                                orderedChars,
                                meta,
                                charExtraction.truncated,
                            )
                    }
                    if (PromptPlaceholders.SIMPL_HISTORY in needed) {
                        resolved[PromptPlaceholders.SIMPL_HISTORY] =
                            HanziMetadataFormatter.formatSimplHistory(
                                orderedChars,
                                meta,
                                charExtraction.truncated,
                            )
                    }
                    if (PromptPlaceholders.MNEMO_EXAMPLES in needed) {
                        resolved[PromptPlaceholders.MNEMO_EXAMPLES] =
                            HanziMetadataFormatter.formatMnemonics(
                                orderedMnemo,
                                meta,
                                mnemonicExtraction!!.truncated,
                            )
                    }
                    if (PromptPlaceholders.SEMANTIC in needed) {
                        resolved[PromptPlaceholders.SEMANTIC] =
                            HanziMetadataFormatter.formatSemantic(
                                orderedChars,
                                meta,
                                charExtraction.truncated,
                            )
                    }
                    if (PromptPlaceholders.PHONETIC in needed) {
                        resolved[PromptPlaceholders.PHONETIC] =
                            HanziMetadataFormatter.formatPhonetic(
                                orderedChars,
                                meta,
                                charExtraction.truncated,
                            )
                    }
                }
            }
        }

        // If metadata placeholders were needed but DB failed earlier with empty meta,
        // ensure keys exist as empty strings.
        for (name in needed) {
            if (name !in resolved) {
                resolved[name] = if (name == PromptPlaceholders.QUERY) query else ""
            }
        }

        val expanded = substitute(template, resolved)
        return PromptExpansionResult(
            prompt = normalizeBlankLines(expanded),
            warnings = warnings,
        )
    }

    companion object {
        private val TOKEN_PATTERN = Regex("""\{([A-Za-z0-9_]+)\}""")

        fun findBraceTokens(template: String): List<String> =
            TOKEN_PATTERN.findAll(template).map { it.groupValues[1] }.toList()

        fun findUnknownPlaceholders(template: String): List<String> =
            findBraceTokens(template).filter { it !in PromptPlaceholders.SUPPORTED }.distinct()

        /**
         * Replace known placeholders without interpreting replacement text as regex.
         * Unknown `{...}` tokens are left unchanged.
         */
        private val PLACEHOLDER_NAME = Regex("^[A-Za-z0-9_]+$")

        fun substitute(template: String, values: Map<String, String>): String {
            val sb = StringBuilder()
            var i = 0
            while (i < template.length) {
                val open = template.indexOf('{', i)
                if (open < 0) {
                    sb.append(template, i, template.length)
                    break
                }
                sb.append(template, i, open)
                val close = template.indexOf('}', open + 1)
                if (close < 0) {
                    sb.append(template, open, template.length)
                    break
                }
                val name = template.substring(open + 1, close)
                if (PLACEHOLDER_NAME.matches(name)) {
                    if (name in values) {
                        sb.append(values.getValue(name))
                    } else {
                        // Unknown brace-style placeholder — leave unchanged.
                        sb.append('{').append(name).append('}')
                    }
                    i = close + 1
                } else {
                    // Literal '{' not forming a placeholder token.
                    sb.append('{')
                    i = open + 1
                }
            }
            return sb.toString()
        }

        fun normalizeBlankLines(text: String): String {
            // Collapse runs of 3+ newlines to a double newline; trim edges.
            return text.replace(Regex("\n{3,}"), "\n\n").trim()
        }
    }
}
