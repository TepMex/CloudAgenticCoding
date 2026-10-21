package com.tepmex.extensivereadingtutor

/**
 * Optional live check of the golden fixtures. Unit tests do not call a model.
 * A run needs both SEMANTIC_EVAL_BASE_URL and SEMANTIC_EVAL_API_KEY, stays outside
 * the app binary, and must not write the key into fixtures or reports.
 */
object SemanticEvalRunner {
    fun status(): String {
        val url = System.getenv("SEMANTIC_EVAL_BASE_URL")
        val key = System.getenv("SEMANTIC_EVAL_API_KEY")
        if (url.isNullOrBlank() || key.isNullOrBlank()) return "semantic evaluation не запускалась"
        return "semantic evaluation настроена, но этот прогон её не вызывает"
    }
}
