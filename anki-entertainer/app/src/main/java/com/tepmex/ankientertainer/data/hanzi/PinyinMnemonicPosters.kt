package com.tepmex.ankientertainer.data.hanzi

/**
 * Bundled pinyin mnemonic posters shown after composition cards and before stories.
 */
data class PinyinMnemonicPoster(
    val id: String,
    val title: String,
    val assetPath: String,
)

object PinyinMnemonicPosters {
    const val MODEL_LABEL = "local poster"

    val FINALS = PinyinMnemonicPoster(
        id = "poster:pinyin-finals",
        title = "Pinyin finals",
        assetPath = "posters/pinyin_finals.jpg",
    )

    val INITIALS = PinyinMnemonicPoster(
        id = "poster:pinyin-initials",
        title = "Pinyin initials",
        assetPath = "posters/pinyin_initials.jpg",
    )

    /** Finals first, then initials — the order they appear in the session list. */
    val ALL: List<PinyinMnemonicPoster> = listOf(FINALS, INITIALS)
}
