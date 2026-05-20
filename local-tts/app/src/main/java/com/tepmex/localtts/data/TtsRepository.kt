package com.tepmex.localtts.data

import android.content.Context
import com.tepmex.localtts.tts.VoskTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TtsRepository(
    context: Context,
    private val downloader: ModelDownloader = ModelDownloader(),
) {
    private val modelsRoot = File(context.filesDir, "vosk-models")
    private val modelDir = File(modelsRoot, VoskTtsEngine.MODEL_NAME)
    private val mutex = Mutex()
    private var engine: VoskTtsEngine? = null

    suspend fun ensureModel(onProgress: (downloaded: Long, total: Long?) -> Unit) {
        downloader.ensureModelExtracted(
            zipUrls = VoskTtsEngine.MODEL_ZIP_URLS,
            modelDir = modelDir,
            onProgress = onProgress,
        )
    }

    suspend fun synthesize(text: String, speakerId: Int): SynthesisResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val tts = engine ?: VoskTtsEngine(modelDir).also { engine = it }
            val pcm = tts.synthesize(text, speakerId = speakerId)
            SynthesisResult(pcm = pcm, sampleRate = tts.sampleRate)
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }

    data class SynthesisResult(
        val pcm: ShortArray,
        val sampleRate: Int,
    )
}
