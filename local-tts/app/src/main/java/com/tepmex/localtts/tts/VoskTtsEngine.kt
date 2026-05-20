package com.tepmex.localtts.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.tepmex.localtts.util.DiagnosticsLog
import com.tepmex.localtts.util.MemoryStats
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.min

/**
 * On-device Vosk TTS synthesis (multistream_v1) using ONNX Runtime, ported from vosk-tts Python.
 */
class VoskTtsEngine(modelDir: File) : AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val config: VoskTtsConfig = VoskTtsConfig.load(File(modelDir, "config.json"))
    private val dictionary: Map<String, String> = loadDictionary(File(modelDir, "dictionary"))
    private val tokenizer: BertWordPieceTokenizer = BertWordPieceTokenizer(
        vocabFile = File(modelDir, "bert/vocab.txt"),
        unkToken = "[UNK]",
        lowercase = true,
    )
    private val ttsSession: OrtSession
    private val bertSession: OrtSession

    init {
        try {
            val opts = OrtSession.SessionOptions()
            DiagnosticsLog.i(TAG, "Loading ONNX sessions from ${modelDir.absolutePath}")
            ttsSession = env.createSession(File(modelDir, "model.onnx").absolutePath, opts)
            bertSession = env.createSession(File(modelDir, "bert/model.onnx").absolutePath, opts)
            DiagnosticsLog.i(TAG, "Loaded Vosk TTS (${config.modelType}, sr=${config.sampleRate})")
            DiagnosticsLog.i(TAG, MemoryStats.format(label = "after model load"))
        } catch (e: Throwable) {
            DiagnosticsLog.e(TAG, "Failed to load ONNX models", e)
            throw e
        }
    }

    val sampleRate: Int get() = config.sampleRate
    val numSpeakers: Int get() = config.numSpeakers

    fun synthesize(
        text: String,
        speakerId: Int = 0,
        noiseLevel: Float? = null,
        speechRate: Float? = null,
        durationNoiseLevel: Float? = null,
        scale: Float? = null,
    ): ShortArray {
        require(config.modelType == "multistream_v1") {
            "PoC supports multistream_v1 models only (got ${config.modelType})"
        }

        val nl = noiseLevel ?: config.inference.noiseLevel
        val sr = speechRate ?: config.inference.speechRate
        val dnl = durationNoiseLevel ?: config.inference.durationNoiseLevel
        val sc = scale ?: config.inference.scale

        DiagnosticsLog.i(TAG, "synthesize start: chars=${text.length}, speaker=$speakerId")
        DiagnosticsLog.i(TAG, MemoryStats.format(label = "before tokenize"))

        return try {
            var normalized = text.trim().replace('—', '-')
            DiagnosticsLog.d(TAG, "normalized preview: ${preview(normalized)}")

            DiagnosticsLog.d(TAG, "BERT encode + inference…")
            val bertEmbeddings = getWordBert(normalized, nopunc = true)
            DiagnosticsLog.i(
                TAG,
                "BERT words=${bertEmbeddings.size}; ${MemoryStats.format(label = "after BERT")}",
            )

            DiagnosticsLog.d(TAG, "G2P multistream…")
            val (phonemeIds, phoneBert) = g2pMultistream(normalized, bertEmbeddings)
            val timeSteps = phonemeIds.size
            DiagnosticsLog.i(
                TAG,
                "phoneme timeSteps=$timeSteps; bert dim=768; " +
                    "input tensor est ${MemoryStats.estimateTensorMb(timeSteps * 5L, 8)}; " +
                    "bert tensor est ${MemoryStats.estimateTensorMb(timeSteps * 768L, 4)}",
            )
            if (timeSteps > WARN_TIME_STEPS) {
                DiagnosticsLog.w(
                    TAG,
                    "Long input ($timeSteps phoneme steps) may cause high RAM use or OOM; try shorter text.",
                )
            }

            val input = Array(1) { Array(5) { LongArray(timeSteps) } }
            for (t in 0 until timeSteps) {
                val stream = phonemeIds[t]
                for (s in 0 until 5) {
                    input[0][s][t] = stream[s]
                }
            }

            val bert = Array(1) { Array(768) { FloatArray(timeSteps) } }
            for (t in 0 until timeSteps) {
                val emb = phoneBert[t]
                for (d in 0 until 768) {
                    bert[0][d][t] = emb[d]
                }
            }

            val scales = floatArrayOf(nl, 1.0f / sr, dnl)
            val sid = longArrayOf(speakerId.toLong())
            val inputLengths = longArrayOf(timeSteps.toLong())

            DiagnosticsLog.d(TAG, "ONNX TTS inference…")
            DiagnosticsLog.i(TAG, MemoryStats.format(label = "before ONNX run"))
            val audio = runTts(input, inputLengths, scales, sid, bert)
            DiagnosticsLog.i(
                TAG,
                "ONNX output samples=${audio.size}; ${MemoryStats.format(label = "after ONNX run")}",
            )

            val pcm = audioFloatToInt16(audio, sc)
            DiagnosticsLog.i(TAG, "PCM int16 samples=${pcm.size} (${pcm.size * 2} bytes)")
            pcm
        } catch (e: OutOfMemoryError) {
            DiagnosticsLog.e(TAG, "OutOfMemoryError during synthesis — try shorter text", e)
            throw SynthesisException("Out of memory during synthesis. Try shorter text.", e)
        } catch (e: Throwable) {
            DiagnosticsLog.e(TAG, "Synthesis failed", e)
            throw if (e is SynthesisException) e else SynthesisException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun runTts(
        input: Array<Array<LongArray>>,
        inputLengths: LongArray,
        scales: FloatArray,
        sid: LongArray,
        bert: Array<Array<FloatArray>>,
    ): FloatArray {
        val batch = input.size
        val streams = input[0].size
        val time = input[0][0].size

        try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(flattenInput(input)), longArrayOf(batch.toLong(), streams.toLong(), time.toLong())).use { inputTensor ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(inputLengths), longArrayOf(batch.toLong())).use { lenTensor ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3)).use { scalesTensor ->
                        OnnxTensor.createTensor(env, LongBuffer.wrap(sid), longArrayOf(batch.toLong())).use { sidTensor ->
                            OnnxTensor.createTensor(
                                env,
                                FloatBuffer.wrap(flattenBert(bert)),
                                longArrayOf(batch.toLong(), 768, time.toLong()),
                            ).use { bertTensor ->
                                DiagnosticsLog.d(TAG, "ttsSession.run (time=$time)…")
                                val results = ttsSession.run(
                                    mapOf(
                                        "input" to inputTensor,
                                        "input_lengths" to lenTensor,
                                        "scales" to scalesTensor,
                                        "sid" to sidTensor,
                                        "bert" to bertTensor,
                                    ),
                                )
                                val floats = extractAudio(raw = results[0].value)
                                results.close()
                                if (floats.isEmpty()) {
                                    DiagnosticsLog.w(TAG, "ONNX returned empty audio buffer")
                                }
                                return floats
                            }
                        }
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            DiagnosticsLog.e(TAG, "OutOfMemoryError building ONNX tensors or running session", e)
            throw e
        }
    }

    private fun flattenInput(input: Array<Array<LongArray>>): LongArray {
        val batch = input.size
        val streams = input[0].size
        val time = input[0][0].size
        val flat = LongArray(batch * streams * time)
        var idx = 0
        for (b in 0 until batch) {
            for (s in 0 until streams) {
                for (t in 0 until time) {
                    flat[idx++] = input[b][s][t]
                }
            }
        }
        return flat
    }

    private fun flattenBert(bert: Array<Array<FloatArray>>): FloatArray {
        val batch = bert.size
        val dim = bert[0].size
        val time = bert[0][0].size
        val flat = FloatArray(batch * dim * time)
        var idx = 0
        for (b in 0 until batch) {
            for (d in 0 until dim) {
                for (t in 0 until time) {
                    flat[idx++] = bert[b][d][t]
                }
            }
        }
        return flat
    }

    private fun getWordBert(text: String, nopunc: Boolean): List<FloatArray> {
        val encoding = tokenizer.encode(text.replace("+", "").replace("_", ""))
        val inputIds = encoding.ids
        val attention = encoding.attentionMask
        val typeIds = encoding.typeIds
        DiagnosticsLog.d(TAG, "tokenizer tokens=${encoding.tokens.size}, ids=${inputIds.size}")

        try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong())).use { idsTensor ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(attention), longArrayOf(1, attention.size.toLong())).use { maskTensor ->
                    OnnxTensor.createTensor(env, LongBuffer.wrap(typeIds), longArrayOf(1, typeIds.size.toLong())).use { typeTensor ->
                        val results = bertSession.run(
                            mapOf(
                                "input_ids" to idsTensor,
                                "attention_mask" to maskTensor,
                                "token_type_ids" to typeTensor,
                            ),
                        )
                        @Suppress("UNCHECKED_CAST")
                        val output = results[0].value as Array<Array<FloatArray>>
                        results.close()
                        val seq = output[0]
                        val puncPattern = Regex("""[-,.?!;:"]""")
                        val selected = mutableListOf<FloatArray>()
                        for (i in encoding.tokens.indices) {
                            val token = encoding.tokens[i]
                            if (token.isNotEmpty() && token[0] != '#') {
                                if (!(nopunc && puncPattern.matches(token))) {
                                    selected.add(seq[i])
                                }
                            }
                        }
                        return selected
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            DiagnosticsLog.e(TAG, "OutOfMemoryError in BERT session", e)
            throw e
        }
    }

    private fun g2pMultistream(text: String, bertEmbeddings: List<FloatArray>?): Pair<List<LongArray>, List<FloatArray>> {
        val phonemes = mutableListOf<PhonemeNode>()
        phonemes.add(PhonemeNode("^", emptyList(), 0, 0))

        val pattern = Regex("""(\.\.\.|- |[ ,.?!;:"()])""")
        var normalized = text.replace(" -", "- ")
        var inQuote = 0
        var curPunc = mutableListOf<String>()
        var bertWordIndex = 1

        for (word in pattern.split(normalized.lowercase())) {
            if (word.isEmpty()) continue
            if (word == "\"") {
                inQuote = if (inQuote == 1) 0 else 1
                continue
            }
            if (word == "- " || word == "-") {
                curPunc.add("-")
                continue
            }
            if (pattern.matches(word) && word != " ") {
                curPunc.add(word)
                continue
            }
            if (word == " ") {
                phonemes.add(PhonemeNode(" ", curPunc.toList(), inQuote, bertWordIndex))
                curPunc = mutableListOf()
                continue
            }

            val wordPhonemes = dictionary[word]?.split(" ")
                ?: RussianG2p.convert(word).split(" ")

            for (p in wordPhonemes) {
                phonemes.add(PhonemeNode(p, emptyList(), inQuote, bertWordIndex))
            }
            curPunc = mutableListOf()
            bertWordIndex++
        }

        phonemes.add(PhonemeNode(" ", curPunc.toList(), inQuote, bertWordIndex))
        phonemes.add(PhonemeNode("$", emptyList(), 0, bertWordIndex))

        var lastPunc = " "
        var lastSentencePunc = " "
        val lpPhonemes = mutableListOf<LongArray>()
        val phoneBert = mutableListOf<FloatArray>()

        for (p in phonemes.asReversed()) {
            if ("..." in p.punc) lastSentencePunc = "..."
            if ("." in p.punc) lastSentencePunc = "."
            if ("!" in p.punc) lastSentencePunc = "!"
            if ("?" in p.punc) lastSentencePunc = "?"
            if ("-" in p.punc) lastSentencePunc = "-"

            if (p.punc.isNotEmpty()) {
                lastPunc = p.punc[0]
            }

            val curPuncToken = if (p.punc.isNotEmpty()) p.punc[0] else "_"
            val map = config.phonemeIdMap
            lpPhonemes.add(
                longArrayOf(
                    map[p.phone]!!.toLong(),
                    map[curPuncToken]!!.toLong(),
                    p.inQuote.toLong(),
                    map[lastPunc]!!.toLong(),
                    map[lastSentencePunc]!!.toLong(),
                ),
            )
            if (bertEmbeddings != null) {
                phoneBert.add(bertEmbeddings[min(p.bertIndex, bertEmbeddings.lastIndex)])
            }
        }

        return lpPhonemes.asReversed() to phoneBert.asReversed()
    }

    private fun audioFloatToInt16(audio: FloatArray, scale: Float): ShortArray {
        val max = 32767.0f
        return ShortArray(audio.size) { i ->
            val v = (audio[i] * scale * max).coerceIn(-max, max)
            v.toInt().toShort()
        }
    }

    private fun loadDictionary(file: File): Map<String, String> {
        val probs = mutableMapOf<String, Float>()
        val dic = mutableMapOf<String, String>()
        file.forEachLine { line ->
            val parts = line.split(" ", limit = 3)
            if (parts.size < 3) return@forEachLine
            val word = parts[0]
            val prob = parts[1].toFloatOrNull() ?: return@forEachLine
            if ((probs[word] ?: 0f) < prob) {
                dic[word] = parts[2]
                probs[word] = prob
            }
        }
        DiagnosticsLog.d(TAG, "dictionary entries=${dic.size}")
        return dic
    }

    override fun close() {
        DiagnosticsLog.d(TAG, "Closing ONNX sessions")
        ttsSession.close()
        bertSession.close()
    }

    private data class PhonemeNode(
        val phone: String,
        val punc: List<String>,
        val inQuote: Int,
        val bertIndex: Int,
    )

    private fun extractAudio(raw: Any?): FloatArray {
        return when (raw) {
            is FloatArray -> raw
            is Array<*> -> {
                val first = raw.firstOrNull() ?: return floatArrayOf()
                extractAudio(first)
            }
            else -> floatArrayOf()
        }
    }

    private fun preview(text: String, max: Int = 80): String =
        if (text.length <= max) text else text.take(max) + "…"

    class SynthesisException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val TAG = "VoskTtsEngine"
        private const val WARN_TIME_STEPS = 400
        const val MODEL_NAME = "vosk-model-tts-ru-0.9-multi"

        /** Official Vosk model archive (same layout as the zip from alphacephei.com). */
        val MODEL_ZIP_URLS = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-tts-ru-0.9-multi.zip",
        )

        /** Hugging Face mirror (file-by-file) when zip hosts are unreachable. */
        const val HUGGING_FACE_REPO =
            "https://huggingface.co/drakulavich/vosk-tts-ru-0.9-multi/resolve/main"

        val HUGGING_FACE_FILES = listOf(
            "config.json",
            "dictionary",
            "model.onnx",
            "bert/model.onnx",
            "bert/vocab.txt",
        )

        /** IPv4 for alphacephei.com when system DNS cannot resolve the hostname. */
        val ALPHACEPHEI_FALLBACK_IPV4 = byteArrayOf(
            188.toByte(),
            40,
            21,
            16,
        )
    }
}
