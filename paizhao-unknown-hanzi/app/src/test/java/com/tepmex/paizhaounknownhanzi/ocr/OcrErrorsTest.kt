package com.tepmex.paizhaounknownhanzi.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrErrorsTest {
    @Test
    fun flagsLiteRtNativeDump() {
        val dump = Exception(
            "ERROR:[third_party/odml/litert/litert/cc/litert_compiled_model.cc:139] " +
                "ERROR:[third_party/odml/litert/litert/cc/litert_tensor_buffer.cc:53]",
        )
        assertTrue(OcrErrors.isEngineFailure(dump))
    }

    @Test
    fun flagsCauseChain() {
        val root = RuntimeException("LiteRtException: Failed to create input buffers")
        assertTrue(OcrErrors.isEngineFailure(IllegalStateException("wrap", root)))
    }

    @Test
    fun leavesOrdinaryMessagesAlone() {
        assertFalse(OcrErrors.isEngineFailure(IllegalArgumentException("empty bitmap")))
        assertFalse(OcrErrors.isEngineFailure(RuntimeException("camera closed")))
    }
}
