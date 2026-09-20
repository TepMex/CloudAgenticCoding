package com.tepmex.paizhaounknownhanzi.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
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
import com.tepmex.paizhaounknownhanzi.ocr.ScanImages
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
    data class Ready(
        val ocrText: String,
        val cards: List<HanziCard>,
        val uniqueFound: Int,
    ) : ScanState() {
        val shareText: String get() = UnknownHanzi.shareText(ocrText)
    }
    data class Failed(val message: String) : ScanState()
}

class PaizhaoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PaizhaoApp

    val knownText: StateFlow<String> = app.knownHanziStore.knownText.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )

    private val _showKnownHanzi = MutableStateFlow(true)
    val showKnownHanzi: StateFlow<Boolean> = _showKnownHanzi.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _savedTick = MutableStateFlow(0)
    val savedTick: StateFlow<Int> = _savedTick.asStateFlow()

    init {
        viewModelScope.launch {
            app.knownHanziStore.showKnownHanzi.collect { stored ->
                if (_showKnownHanzi.value != stored) {
                    _showKnownHanzi.value = stored
                    refreshCards(includeKnown = stored)
                }
            }
        }
    }

    fun saveKnownText(text: String) {
        viewModelScope.launch {
            app.knownHanziStore.save(text)
            _savedTick.value = _savedTick.value + 1
        }
    }

    fun setShowKnownHanzi(value: Boolean) {
        _showKnownHanzi.value = value
        viewModelScope.launch {
            app.knownHanziStore.setShowKnownHanzi(value)
        }
        refreshCards(includeKnown = value)
    }

    fun onPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            recognize(bitmap)
        }
    }

    fun onGallery(uri: Uri) {
        viewModelScope.launch {
            _scan.value = ScanState.Processing
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ScanImages.decode(app.contentResolver, uri) }.getOrNull()
            }
            if (bitmap == null) {
                _scan.value = ScanState.Failed(app.getString(R.string.gallery_failed))
                return@launch
            }
            recognize(bitmap)
        }
    }

    fun consumeScanNavigation() {
        val current = _scan.value
        if (current is ScanState.Ready || current is ScanState.Failed) {
            _scan.value = ScanState.Idle
        }
    }

    private fun refreshCards(includeKnown: Boolean) {
        val current = _scan.value as? ScanState.Ready ?: return
        _scan.value = current.copy(
            cards = UnknownHanzi.cards(
                ocrText = current.ocrText,
                knownText = knownText.value,
                pinyinOf = PinyinLookup::of,
                includeKnown = includeKnown,
            ),
        )
    }

    private suspend fun recognize(bitmap: Bitmap) {
        _scan.value = ScanState.Processing
        val known = knownText.value
        val includeKnown = showKnownHanzi.value
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val lines = app.ocr.recognize(bitmap)
                val ocrText = lines.joinToString("") { it.text }
                val uniqueFound = Hanzi.extractUniqueInOrder(ocrText).size
                val cards = UnknownHanzi.cards(
                    ocrText = ocrText,
                    knownText = known,
                    pinyinOf = PinyinLookup::of,
                    includeKnown = includeKnown,
                )
                Triple(ocrText, uniqueFound, cards)
            }.also {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        result.fold(
            onSuccess = { (ocrText, found, cards) ->
                _scan.value = ScanState.Ready(ocrText, cards, found)
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
