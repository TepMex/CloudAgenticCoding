package com.tepmex.paizhaounknownhanzi.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File
import java.io.FileOutputStream

data class OcrLine(
    val text: String,
    val box: TextBox,
)

class LiteRtHanziOcr(private val context: Context) {
    private val lock = Any()
    private var det: CompiledModel? = null
    private var rec: CompiledModel? = null
    private var charset: List<String> = emptyList()

    fun recognize(bitmap: Bitmap): List<OcrLine> {
        ensureLoaded()
        val detector = checkNotNull(det)
        val recognizer = checkNotNull(rec)
        val working = ImageTensors.constrain(bitmap)
        val (boxed, lb) = ImageTensors.letterboxRgb(working)
        try {
            val detIn = ImageTensors.detNchw(boxed)
            val prob = runDet(detector, detIn)
            val boxes640 = ProbMapBoxes.find(prob)
            val lines = ArrayList<OcrLine>(boxes640.size)
            for (b in boxes640) {
                val srcBox = ProbMapBoxes.mapToSource(b, lb)
                val crop = ImageTensors.cropLine(working, srcBox)
                val text = runRec(recognizer, ImageTensors.recNchw(crop)).trim()
                if (text.isNotEmpty()) {
                    lines.add(OcrLine(text, srcBox))
                }
            }
            return lines
        } finally {
            if (boxed !== bitmap) boxed.recycle()
            if (working !== bitmap && working !== boxed) working.recycle()
        }
    }

    fun warmup() {
        ensureLoaded()
    }

    private fun ensureLoaded() {
        synchronized(lock) {
            if (det != null && rec != null) return
            copyAsset(DET_FILE)
            copyAsset(REC_FILE)
            copyAsset(DICT_FILE)
            charset = CtcDecoder.charsetFromDictLines(
                File(modelDir(), DICT_FILE).readLines(Charsets.UTF_8).map { it.trimEnd('\r') },
            )
            det = openModel(DET_FILE)
            rec = openModel(REC_FILE)
            Log.i(TAG, "LiteRT OCR ready charset=${charset.size}")
        }
    }

    private fun runDet(model: CompiledModel, input: FloatArray): FloatArray {
        val ins = model.createInputBuffers()
        val outs = model.createOutputBuffers()
        try {
            ins[0].writeFloat(input)
            model.run(ins, outs)
            return outs[0].readFloat()
        } finally {
            closeBuffers(ins)
            closeBuffers(outs)
        }
    }

    private fun runRec(model: CompiledModel, input: FloatArray): String {
        val ins = model.createInputBuffers()
        val outs = model.createOutputBuffers()
        try {
            ins[0].writeFloat(input)
            model.run(ins, outs)
            val logits = outs[0].readFloat()
            val classes = charset.size
            val time = if (classes == 0) 0 else logits.size / classes
            return CtcDecoder.greedyFromLogits(logits, time, classes, charset)
        } finally {
            closeBuffers(ins)
            closeBuffers(outs)
        }
    }

    private fun openModel(fileName: String): CompiledModel {
        val path = File(modelDir(), fileName).absolutePath
        return try {
            CompiledModel.create(path, CompiledModel.Options(Accelerator.GPU))
        } catch (gpu: Exception) {
            Log.w(TAG, "GPU compile failed for $fileName, falling back to CPU", gpu)
            CompiledModel.create(path, CompiledModel.Options(Accelerator.CPU))
        }
    }

    private fun copyAsset(name: String) {
        val dest = File(modelDir(), name)
        if (dest.exists() && dest.length() > 0) return
        dest.parentFile?.mkdirs()
        context.assets.open("ocr/$name").use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
    }

    private fun modelDir(): File = File(context.filesDir, "ocr")

    private fun closeBuffers(buffers: List<*>) {
        for (b in buffers) {
            if (b is AutoCloseable) {
                try {
                    b.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val TAG = "LiteRtHanziOcr"
        const val DET_FILE = "ppocr_det_fp16.tflite"
        const val REC_FILE = "ppocr_rec_fp16.tflite"
        const val DICT_FILE = "ppocrv5_dict.txt"
    }
}
