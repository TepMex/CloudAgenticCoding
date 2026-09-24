package com.tepmex.instantpinyin.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.instantpinyin.InstantPinyinApp
import com.tepmex.instantpinyin.R
import com.tepmex.instantpinyin.domain.OverlaySession
import com.tepmex.instantpinyin.domain.PinyinLookup
import com.tepmex.instantpinyin.domain.RuGlossLexicon
import com.tepmex.instantpinyin.domain.ReadingGlyph
import com.tepmex.instantpinyin.domain.TextToken
import com.tepmex.instantpinyin.domain.WordSegmenter
import com.tepmex.instantpinyin.ocr.OcrErrors
import com.tepmex.instantpinyin.ocr.OcrLine
import com.tepmex.instantpinyin.ocr.ScanImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReaderPhase {
    LOADING,
    LIVE,
    FAILED,
}

data class ReaderUi(
    val phase: ReaderPhase = ReaderPhase.LOADING,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val glyphs: List<ReadingGlyph> = emptyList(),
)

sealed class StillState {
    data object Idle : StillState()
    data object Processing : StillState()
    data class Ready(val lines: List<List<TextToken>>) : StillState()
    data class Failed(val message: String) : StillState()
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val session = OverlaySession()
    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    private val _still = MutableStateFlow<StillState>(StillState.Idle)
    val still: StateFlow<StillState> = _still.asStateFlow()

    val ocr get() = getApplication<InstantPinyinApp>().ocr

    val lexicon: RuGlossLexicon get() = getApplication<InstantPinyinApp>().lexicon

    fun markReady() {
        _ui.update { current ->
            if (current.phase == ReaderPhase.LOADING) current.copy(phase = ReaderPhase.LIVE) else current
        }
    }

    fun markFailed() {
        _ui.update { it.copy(phase = ReaderPhase.FAILED) }
    }

    fun publish(lines: List<OcrLine>, width: Int, height: Int) {
        if (_still.value !is StillState.Idle) return
        val glyphs = session.observe(lines, width, height)
        _ui.update {
            it.copy(
                phase = ReaderPhase.LIVE,
                imageWidth = width,
                imageHeight = height,
                glyphs = glyphs,
            )
        }
    }

    fun onPhoto(bitmap: Bitmap) {
        viewModelScope.launch { recognize(bitmap) }
    }

    fun onGallery(uri: Uri) {
        viewModelScope.launch {
            _still.value = StillState.Processing
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    ScanImages.decode(getApplication<InstantPinyinApp>().contentResolver, uri)
                }.getOrNull()
            }
            if (bitmap == null) {
                _still.value = StillState.Failed(getApplication<InstantPinyinApp>().getString(R.string.gallery_failed))
                return@launch
            }
            recognize(bitmap)
        }
    }

    fun closeStill() {
        _still.value = StillState.Idle
    }

    private suspend fun recognize(bitmap: Bitmap) {
        _still.value = StillState.Processing
        val app = getApplication<InstantPinyinApp>()
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val lines = app.ocr.recognize(bitmap, maxBoxes = 64)
                WordSegmenter.lines(lines, app.lexicon, PinyinLookup::of)
            }.also {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        result.fold(
            onSuccess = { lines -> _still.value = StillState.Ready(lines) },
            onFailure = { error ->
                Log.e(TAG, "still OCR failed", error)
                val message = if (OcrErrors.isEngineFailure(error)) {
                    app.getString(R.string.ocr_engine_failed)
                } else {
                    app.getString(R.string.ocr_failed, error.message ?: error.javaClass.simpleName)
                }
                _still.value = StillState.Failed(message)
            },
        )
    }

    private companion object {
        const val TAG = "InstantPinyin"
    }
}
