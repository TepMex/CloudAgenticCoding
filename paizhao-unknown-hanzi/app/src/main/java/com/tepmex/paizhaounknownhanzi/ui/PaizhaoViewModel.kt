package com.tepmex.paizhaounknownhanzi.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paizhaounknownhanzi.PaizhaoApp
import com.tepmex.paizhaounknownhanzi.R
import com.tepmex.paizhaounknownhanzi.domain.Hanzi
import com.tepmex.paizhaounknownhanzi.domain.HanziCard
import com.tepmex.paizhaounknownhanzi.domain.PinyinLookup
import com.tepmex.paizhaounknownhanzi.domain.UnknownHanzi
import com.tepmex.paizhaounknownhanzi.ocr.OcrErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ScanState {
    data object Idle : ScanState()
    data object Processing : ScanState()
    data class Ready(val cards: List<HanziCard>, val uniqueFound: Int) : ScanState()
    data class Failed(val message: String) : ScanState()
}

class PaizhaoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PaizhaoApp

    val knownText: StateFlow<String> = app.knownHanziStore.knownText.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _savedTick = MutableStateFlow(0)
    val savedTick: StateFlow<Int> = _savedTick.asStateFlow()

    fun saveKnownText(text: String) {
        viewModelScope.launch {
            app.knownHanziStore.save(text)
            _savedTick.value = _savedTick.value + 1
        }
    }

    fun onPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _scan.value = ScanState.Processing
            val known = knownText.value
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val lines = app.ocr.recognize(bitmap)
                    val ocrText = lines.joinToString("") { it.text }
                    val uniqueFound = Hanzi.extractUniqueInOrder(ocrText).size
                    val cards = UnknownHanzi.cards(ocrText, known, PinyinLookup::of)
                    uniqueFound to cards
                }.also {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            result.fold(
                onSuccess = { (found, cards) ->
                    _scan.value = ScanState.Ready(cards, found)
                },
                onFailure = { e ->
                    Log.e("PaizhaoViewModel", "OCR failed", e)
                    val message = if (OcrErrors.isEngineFailure(e)) {
                        app.getString(R.string.ocr_engine_failed)
                    } else {
                        app.getString(R.string.ocr_failed, e.message ?: e.javaClass.simpleName)
                    }
                    _scan.value = ScanState.Failed(message)
                },
            )
        }
    }

    fun consumeScanNavigation() {
        val current = _scan.value
        if (current is ScanState.Ready || current is ScanState.Failed) {
            _scan.value = ScanState.Idle
        }
    }
}
