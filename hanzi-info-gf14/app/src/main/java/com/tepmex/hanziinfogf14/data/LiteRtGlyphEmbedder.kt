package com.tepmex.hanziinfogf14.data

import android.content.Context
import android.graphics.Typeface
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import java.io.File

/**
 * Embeds a glyph PNG with the user-downloaded EmbeddingGemma 2 LiteRT bundle.
 * Settings match the precomputed catalog: CPU, 70 vision tokens, 768-d, L2-normalized.
 */
class LiteRtGlyphEmbedder(
    context: Context,
    private val modelFile: File,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var engine: EmbeddingEngine? = null
    private val typeface: Typeface by lazy {
        Typeface.createFromAsset(appContext.assets, EmbeddingModel.FONT_ASSET)
    }

    fun embed(character: String): FloatArray {
        val png = GlyphImage.png(character, typeface)
        val response = engine().computeEmbedding(
            listOf(InputData.Image(png)),
            EmbeddingOptions(
                normalize = true,
                outputSize = EmbeddingModel.DIM,
                visionTokensPerImage = EmbeddingModel.VISION_TOKENS,
            ),
        )
        val vector = response.embedding
        require(vector.size == EmbeddingModel.DIM) { "dim ${vector.size}" }
        return vector
    }

    override fun close() {
        synchronized(lock) {
            engine?.close()
            engine = null
        }
    }

    private fun engine(): EmbeddingEngine {
        synchronized(lock) {
            engine?.let { return it }
            val cache = File(appContext.cacheDir, "embeddinggemma").apply { mkdirs() }
            val created = EmbeddingEngine(
                EmbeddingEngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    cacheDir = cache.absolutePath,
                    maxInputLength = EmbeddingModel.MAX_INPUT_TOKENS,
                    visionTokensPerImage = EmbeddingModel.VISION_TOKENS,
                ),
            )
            created.initialize()
            engine = created
            return created
        }
    }
}
