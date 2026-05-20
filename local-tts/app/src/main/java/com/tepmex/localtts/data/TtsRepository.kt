package com.tepmex.localtts.data

import android.content.Context
import com.tepmex.localtts.tts.VoskTtsEngine
import com.tepmex.localtts.util.DiagnosticsLog
import com.tepmex.localtts.util.MemoryStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TtsRepository(
    private val context: Context,
    private val downloader: ModelDownloader = ModelDownloader(),
) {
    private val modelsRoot = File(context.filesDir, "vosk-models")
    private val modelDir = File(modelsRoot, VoskTtsEngine.MODEL_NAME)
    private val mutex = Mutex()
    private var engine: VoskTtsEngine? = null

    suspend fun ensureModel(onProgress: (downloaded: Long, total: Long?) -> Unit) {
        DiagnosticsLog.i(TAG, "ensureModel: ${modelDir.absolutePath}")
        try {
            downloader.ensureModelExtracted(
                zipUrls = VoskTtsEngine.MODEL_ZIP_URLS,
                modelDir = modelDir,
                onProgress = onProgress,
            )
            DiagnosticsLog.i(TAG, "Model ready")
        } catch (e: Throwable) {
            DiagnosticsLog.e(TAG, "Model download/extract failed", e)
            throw e
        }
    }

    suspend fun synthesize(text: String, speakerId: Int): SynthesisResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            DiagnosticsLog.i(TAG, "synthesize requested (${text.length} chars, speaker=$speakerId)")
            DiagnosticsLog.i(TAG, MemoryStats.format(context, label = "repository start"))
            try {
                val tts = engine ?: run {
                    DiagnosticsLog.i(TAG, "Creating VoskTtsEngine…")
                    VoskTtsEngine(modelDir).also { engine = it }
                }
                val pcm = tts.synthesize(text, speakerId = speakerId)
                DiagnosticsLog.i(
                    TAG,
                    "synthesize OK: ${pcm.size} samples @ ${tts.sampleRate} Hz; " +
                        MemoryStats.format(context, label = "repository end"),
                )
                SynthesisResult(pcm = pcm, sampleRate = tts.sampleRate)
            } catch (e: OutOfMemoryError) {
                DiagnosticsLog.e(TAG, "OutOfMemoryError in repository — releasing engine", e)
                releaseEngine()
                throw e
            } catch (e: Throwable) {
                DiagnosticsLog.e(TAG, "synthesize failed in repository", e)
                throw e
            }
        }
    }

    fun close() {
        releaseEngine()
    }

    private fun releaseEngine() {
        try {
            engine?.close()
        } catch (e: Throwable) {
            DiagnosticsLog.w(TAG, "Error closing engine", e)
        }
        engine = null
    }

    data class SynthesisResult(
        val pcm: ShortArray,
        val sampleRate: Int,
    )

    companion object {
        private const val TAG = "TtsRepository"
    }
}
