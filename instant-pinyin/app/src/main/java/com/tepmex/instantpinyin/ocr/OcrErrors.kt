package com.tepmex.instantpinyin.ocr

object OcrErrors {
    private val ENGINE_MARKERS = listOf(
        "litert",
        "third_party/odml",
        "compiled_model",
        "tensor_buffer",
        "LiteRtException",
    )

    fun isEngineFailure(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
        return ENGINE_MARKERS.any { marker -> text.contains(marker, ignoreCase = true) }
    }
}
