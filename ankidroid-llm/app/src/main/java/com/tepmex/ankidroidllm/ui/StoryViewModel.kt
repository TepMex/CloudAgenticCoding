package com.tepmex.ankidroidllm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.ankidroidllm.AnkiLlmApp
import com.tepmex.ankidroidllm.R
import com.tepmex.ankidroidllm.data.AnkiVocabularyRepository
import com.tepmex.ankidroidllm.data.AppPreferences
import com.tepmex.ankidroidllm.data.LiteRtStoryGenerator
import com.tepmex.ankidroidllm.data.ModelDownloader
import com.tepmex.ankidroidllm.data.RemoteLlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StoryUiState(
    val statusMessage: String = "",
    val storyText: String = "",
    val loading: Boolean = false,
)

class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val appPrefs: AppPreferences = (application as AnkiLlmApp).preferences
    private val vocabRepo = AnkiVocabularyRepository(application)
    private val downloader = ModelDownloader()
    private val liteRt = LiteRtStoryGenerator(application)
    private val remoteClient = RemoteLlmClient()

    val ankiRepository: AnkiVocabularyRepository get() = vocabRepo

    private val _uiState = MutableStateFlow(StoryUiState(statusMessage = application.getString(R.string.status_idle)))
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    fun generateStory() {
        viewModelScope.launch {
            val settings = appPrefs.settings.first()
            if (settings.useRemoteLlm) {
                if (settings.llmBaseUrl.isBlank() || settings.remoteModelName.isBlank()) {
                    _uiState.update {
                        it.copy(statusMessage = getApplication<Application>().getString(R.string.error_remote_config))
                    }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    loading = true,
                    statusMessage = getApplication<Application>().getString(R.string.status_loading_vocab),
                    storyText = "",
                )
            }
            val vocabResult = vocabRepo.loadStudyQueueVocabulary(settings)
            val words = vocabResult.getOrNull()
            if (words == null) {
                val err = vocabResult.exceptionOrNull()
                val msg = when (err?.message) {
                    "anki_permission" -> getApplication<Application>().getString(R.string.error_anki_permission)
                    "anki_missing" -> getApplication<Application>().getString(R.string.error_anki_missing)
                    "no_vocab" -> getApplication<Application>().getString(R.string.error_no_vocab)
                    else -> err?.message ?: err.toString()
                }
                _uiState.update { it.copy(loading = false, statusMessage = msg) }
                return@launch
            }
            try {
                if (settings.useRemoteLlm) {
                    _uiState.update {
                        it.copy(statusMessage = getApplication<Application>().getString(R.string.status_generating))
                    }
                    val userMsg = liteRtUserMessage(words)
                    val text = remoteClient.chatCompletion(
                        baseUrl = settings.llmBaseUrl,
                        bearerToken = settings.llmToken,
                        model = settings.remoteModelName,
                        systemPrompt = settings.systemPrompt,
                        userMessage = userMsg,
                    )
                    _uiState.update {
                        it.copy(loading = false, storyText = text, statusMessage = getApplication<Application>().getString(R.string.status_idle))
                    }
                } else {
                    _uiState.update {
                        it.copy(statusMessage = getApplication<Application>().getString(R.string.status_downloading_model))
                    }
                    val modelFile = liteRt.localModelFile(getApplication())
                    downloader.ensureModelFile(
                        url = settings.litertModelDownloadUrl,
                        targetFile = modelFile,
                        onProgress = { _, _ -> },
                    )
                    _uiState.update {
                        it.copy(statusMessage = getApplication<Application>().getString(R.string.status_generating))
                    }
                    val sb = StringBuilder()
                    liteRt.generate(
                        modelPath = modelFile.absolutePath,
                        systemPrompt = settings.systemPrompt,
                        vocabulary = words,
                        onToken = { chunk ->
                            sb.append(chunk)
                            withContext(Dispatchers.Main) {
                                _uiState.update { st -> st.copy(storyText = sb.toString()) }
                            }
                        },
                    )
                    _uiState.update {
                        it.copy(loading = false, statusMessage = getApplication<Application>().getString(R.string.status_idle))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, statusMessage = e.message ?: e.toString())
                }
            }
        }
    }

    private fun liteRtUserMessage(vocabulary: List<String>): String {
        return buildString {
            appendLine("Vocabulary from my current Anki study queue (one per line):")
            vocabulary.forEach { appendLine("- $it") }
            appendLine()
            append("Write a short story that naturally uses these words. End with a line: Title: ...")
        }
    }
}
