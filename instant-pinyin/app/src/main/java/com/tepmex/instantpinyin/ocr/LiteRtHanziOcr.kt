package com.tepmex.instantpinyin.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import kotlin.math.roundToInt

data class OcrLine(
    val text: String,
    val box: TextBox,
)

class LiteRtHanziOcr(private val context: Context) {
    private val lock = Any()
    private var env: Environment? = null
    private var det: CompiledModel? = null
    private var rec: CompiledModel? = null
    private var detIn: List<TensorBuffer> = emptyList()
    private var detOut: List<TensorBuffer> = emptyList()
    private var recIn: List<TensorBuffer> = emptyList()
    private var recOut: List<TensorBuffer> = emptyList()
    private var charset: List<String> = emptyList()

    /**
     * @param maxBoxes cap on detector boxes so a live frame stays bounded.
     * Returned boxes are in the input [bitmap] pixel space (upright).
     */
    fun recognize(bitmap: Bitmap, maxBoxes: Int = 48): List<OcrLine> = synchronized(lock) {
        ensureLoaded()
        val detector = checkNotNull(det)
        val recognizer = checkNotNull(rec)
        val working = ImageTensors.constrain(bitmap)
        val scaleX = bitmap.width.toFloat() / working.width.toFloat()
        val scaleY = bitmap.height.toFloat() / working.height.toFloat()
        val (boxed, lb) = ImageTensors.letterboxRgb(working)
        try {
            val detIn = ImageTensors.detNchw(boxed)
            val prob = runDet(detector, detIn)
            val boxes640 = ProbMapBoxes.find(prob, maxBoxes = maxBoxes)
            val lines = ArrayList<OcrLine>(boxes640.size)
            for (b in boxes640) {
                val srcBox = ProbMapBoxes.mapToSource(b, lb)
                val crop = ImageTensors.cropLine(working, srcBox)
                try {
                    val text = runRec(recognizer, ImageTensors.recNchw(crop)).trim()
                    if (text.isNotEmpty()) {
                        lines.add(OcrLine(text, scaleBox(srcBox, scaleX, scaleY)))
                    }
                } finally {
                    crop.recycle()
                }
            }
            return lines
        } finally {
            if (boxed !== bitmap) boxed.recycle()
            if (working !== bitmap && working !== boxed) working.recycle()
        }
    }

    private fun scaleBox(box: TextBox, scaleX: Float, scaleY: Float): TextBox {
        if (scaleX == 1f && scaleY == 1f) return box
        return TextBox(
            x = (box.x * scaleX).roundToInt(),
            y = (box.y * scaleY).roundToInt(),
            width = (box.width * scaleX).roundToInt().coerceAtLeast(1),
            height = (box.height * scaleY).roundToInt().coerceAtLeast(1),
            score = box.score,
        )
    }

    fun warmup() {
        ensureLoaded()
    }

    private fun ensureLoaded() {
        synchronized(lock) {
            if (det != null && rec != null) return
            charset = CtcDecoder.charsetFromDictLines(
                context.assets.open("ocr/$DICT_FILE").bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readLines().map { it.trimEnd('\r') }
                },
            )
            try {
                bind(openPair(Accelerator.GPU))
                Log.i(TAG, "LiteRT OCR ready GPU charset=${charset.size}")
            } catch (gpu: Exception) {
                Log.w(TAG, "GPU compile failed, falling back to CPU", gpu)
                closeSession()
                bind(openPair(Accelerator.CPU))
                Log.i(TAG, "LiteRT OCR ready CPU charset=${charset.size}")
            }
        }
    }

    /**
     * Detector and recognizer must share one [Environment]. LiteRT 2.1 GPU
     * fails in `createInputBuffers` / `run` when each model owns its own env
     * (google-ai-edge/LiteRT#5264).
     */
    private fun openPair(accelerator: Accelerator): Session {
        var environment: Environment? = null
        var detector: CompiledModel? = null
        var recognizer: CompiledModel? = null
        try {
            environment = Environment.create()
            val options = CompiledModel.Options(accelerator)
            detector = CompiledModel.create(context.assets, "ocr/$DET_FILE", options, environment)
            recognizer = CompiledModel.create(context.assets, "ocr/$REC_FILE", options, environment)
            return Session(
                env = environment,
                det = detector,
                rec = recognizer,
                detIn = detector.createInputBuffers(),
                detOut = detector.createOutputBuffers(),
                recIn = recognizer.createInputBuffers(),
                recOut = recognizer.createOutputBuffers(),
            )
        } catch (e: Exception) {
            runCatching { detector?.close() }
            runCatching { recognizer?.close() }
            runCatching { environment?.close() }
            throw e
        }
    }

    private fun bind(session: Session) {
        env = session.env
        det = session.det
        rec = session.rec
        detIn = session.detIn
        detOut = session.detOut
        recIn = session.recIn
        recOut = session.recOut
    }

    private fun runDet(model: CompiledModel, input: FloatArray): FloatArray {
        detIn[0].writeFloat(input)
        model.run(detIn, detOut)
        return detOut[0].readFloat()
    }

    private fun runRec(model: CompiledModel, input: FloatArray): String {
        recIn[0].writeFloat(input)
        model.run(recIn, recOut)
        val logits = recOut[0].readFloat()
        val classes = charset.size
        val time = if (classes == 0) 0 else logits.size / classes
        return CtcDecoder.greedyFromLogits(logits, time, classes, charset)
    }

    private fun closeSession() {
        closeBuffers(detIn)
        closeBuffers(detOut)
        closeBuffers(recIn)
        closeBuffers(recOut)
        detIn = emptyList()
        detOut = emptyList()
        recIn = emptyList()
        recOut = emptyList()
        runCatching { det?.close() }
        runCatching { rec?.close() }
        runCatching { env?.close() }
        det = null
        rec = null
        env = null
    }

    private fun closeBuffers(buffers: List<TensorBuffer>) {
        for (b in buffers) {
            try {
                b.close()
            } catch (_: Exception) {
            }
        }
    }

    private data class Session(
        val env: Environment,
        val det: CompiledModel,
        val rec: CompiledModel,
        val detIn: List<TensorBuffer>,
        val detOut: List<TensorBuffer>,
        val recIn: List<TensorBuffer>,
        val recOut: List<TensorBuffer>,
    )

    companion object {
        private const val TAG = "LiteRtHanziOcr"
        const val DET_FILE = "ppocr_det_fp16.tflite"
        const val REC_FILE = "ppocr_rec_fp16.tflite"
        const val DICT_FILE = "ppocrv5_dict.txt"
    }
}
