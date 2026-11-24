package com.tepmex.hanziinfogf14.data

/**
 * LiteRT EmbeddingGemma 2 text-vision bundle. The file is not in the APK;
 * [URL] is the revision the bundled glyph database was embedded with.
 */
object EmbeddingModel {
    const val FILE_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
    const val URL =
        "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/" +
            "resolve/538e6574db281b4fe17993a3212f10c73bd678c6/" +
            "embeddinggemma-2-text-vision-440m.litertlm"
    const val BYTES = 387_710_976L
    const val VISION_TOKENS = 70
    const val DIM = 768
    const val MAX_INPUT_TOKENS = 128
    const val IMAGE_SIZE = 224
    const val FONT_PX = 156
    const val FONT_ASSET = "fonts/NotoSansCJKsc-Regular.otf"
}
