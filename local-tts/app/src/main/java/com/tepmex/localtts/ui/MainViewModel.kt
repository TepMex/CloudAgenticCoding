package com.tepmex.localtts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.localtts.R
import com.tepmex.localtts.data.PcmAudioPlayer
import com.tepmex.localtts.data.TtsRepository
import com.tepmex.localtts.tts.VoskTtsEngine
import com.tepmex.localtts.util.DiagnosticsLog
import com.tepmex.localtts.util.MemoryStats
import kotlinx.coroutines.CancellationException
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

    init {
        viewModelScope.launch {
            DiagnosticsLog.lines.collect { lines ->
                _uiState.update { it.copy(diagnosticsText = lines.joinToString("\n")) }
            }
        }
    }

    fun speak(text: String, speakerId: Int) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(statusMessage = app.getString(R.string.error_empty_text)) }
            return
        }
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            DiagnosticsLog.clear()
            DiagnosticsLog.i(TAG, "Speak: ${trimmed.length} chars, speaker=$speakerId")
            DiagnosticsLog.i(TAG, MemoryStats.format(app, label = "app start"))
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
                DiagnosticsLog.d(TAG, "Playing ${result.pcm.size} samples @ ${result.sampleRate} Hz")
                PcmAudioPlayer.play(result.pcm, result.sampleRate)

                _uiState.update {
                    it.copy(
                        loading = false,
                        statusMessage = app.getString(R.string.status_done),
                    )
                }
                DiagnosticsLog.i(TAG, "Done")
            } catch (e: CancellationException) {
                DiagnosticsLog.i(TAG, "Cancelled")
                throw e
            } catch (e: OutOfMemoryError) {
                handleFailure(
                    userMessage = app.getString(R.string.error_oom),
                    throwable = e,
                )
            } catch (e: VoskTtsEngine.SynthesisException) {
                handleFailure(
                    userMessage = app.getString(R.string.error_synthesis, e.message ?: e.javaClass.simpleName),
                    throwable = e,
                )
            } catch (e: Exception) {
                handleFailure(
                    userMessage = app.getString(R.string.error_synthesis, e.message ?: e.javaClass.simpleName),
                    throwable = e,
                )
            } catch (e: Throwable) {
                handleFailure(
                    userMessage = app.getString(R.string.error_synthesis, e.message ?: e.javaClass.simpleName),
                    throwable = e,
                )
            }
        }
    }

    private fun handleFailure(userMessage: String, throwable: Throwable) {
        DiagnosticsLog.e(TAG, userMessage, throwable)
        _uiState.update {
            it.copy(
                loading = false,
                statusMessage = userMessage,
            )
        }
    }

    fun stop() {
        speakJob?.cancel()
        DiagnosticsLog.i(TAG, "Stopped by user")
        _uiState.update {
            it.copy(loading = false, statusMessage = app.getString(R.string.status_stopped))
        }
    }

    fun clearDiagnostics() {
        DiagnosticsLog.clear()
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    data class UiState(
        val loading: Boolean = false,
        val statusMessage: String = "",
        val diagnosticsText: String = "",
    )

    companion object {
        private const val TAG = "MainViewModel"
    }
}
