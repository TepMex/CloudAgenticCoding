package com.tepmex.localtts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.localtts.R
import com.tepmex.localtts.data.PcmAudioPlayer
import com.tepmex.localtts.data.TtsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val repository = TtsRepository(application)
    private var speakJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun speak(text: String, speakerId: Int) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(statusMessage = app.getString(R.string.error_empty_text)) }
            return
        }
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(loading = true, statusMessage = app.getString(R.string.status_downloading_model))
                }
                repository.ensureModel { downloaded, total ->
                    val msg = if (total != null && total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        app.getString(R.string.status_downloading_model_progress, pct)
                    } else {
                        app.getString(R.string.status_downloading_model)
                    }
                    _uiState.update { s -> s.copy(statusMessage = msg) }
                }

                _uiState.update {
                    it.copy(statusMessage = app.getString(R.string.status_synthesizing))
                }
                val result = repository.synthesize(trimmed, speakerId)

                _uiState.update {
                    it.copy(statusMessage = app.getString(R.string.status_playing))
                }
                PcmAudioPlayer.play(result.pcm, result.sampleRate)

                _uiState.update {
                    it.copy(
                        loading = false,
                        statusMessage = app.getString(R.string.status_done),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        statusMessage = app.getString(R.string.error_synthesis, e.message ?: e.javaClass.simpleName),
                    )
                }
            }
        }
    }

    fun stop() {
        speakJob?.cancel()
        _uiState.update {
            it.copy(loading = false, statusMessage = app.getString(R.string.status_stopped))
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    data class UiState(
        val loading: Boolean = false,
        val statusMessage: String = "",
    )
}
