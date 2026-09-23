package com.tepmex.instantpinyin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tepmex.instantpinyin.InstantPinyinApp
import com.tepmex.instantpinyin.domain.OverlaySession
import com.tepmex.instantpinyin.domain.ReadingGlyph
import com.tepmex.instantpinyin.ocr.OcrLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val session = OverlaySession()
    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    val ocr get() = getApplication<InstantPinyinApp>().ocr

    fun markReady() {
        _ui.update { current ->
            if (current.phase == ReaderPhase.LOADING) current.copy(phase = ReaderPhase.LIVE) else current
        }
    }

    fun markFailed() {
        _ui.update { it.copy(phase = ReaderPhase.FAILED) }
    }

    fun publish(lines: List<OcrLine>, width: Int, height: Int) {
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
}
