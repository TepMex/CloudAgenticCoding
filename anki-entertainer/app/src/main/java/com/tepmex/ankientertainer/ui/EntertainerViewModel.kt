package com.tepmex.ankientertainer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.ankientertainer.AnkiEntertainerApp
import com.tepmex.ankientertainer.data.LikedChunksRepository
import com.tepmex.ankientertainer.data.RemoteLlmClient
import com.tepmex.ankientertainer.data.StoredChunk
import com.tepmex.ankientertainer.data.hanzi.CharacterCompositionLoader
import com.tepmex.ankientertainer.data.hanzi.OfflineMnemonicFallback
import com.tepmex.ankientertainer.data.hanzi.PinyinMnemonicPosters
import com.tepmex.ankientertainer.data.hanzi.PromptExpansionResult
import com.tepmex.ankientertainer.data.hanzi.PromptTemplateEngine
import com.tepmex.ankientertainer.data.configuredProviders
import com.tepmex.ankientertainer.data.isLlmConfigured
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TextChunk(
    val id: String,
    val text: String,
    val isLiked: Boolean,
    val modelName: String?,
    val likeable: Boolean = true,
    val imageAsset: String? = null,
)

fun posterChunks(): List<TextChunk> =
    PinyinMnemonicPosters.ALL.map { poster ->
        TextChunk(
            id = poster.id,
            text = poster.title,
            isLiked = false,
            modelName = PinyinMnemonicPosters.MODEL_LABEL,
            likeable = false,
            imageAsset = poster.assetPath,
        )
    }

fun mergeSessionChunks(
    composition: List<TextChunk>,
    posters: List<TextChunk>,
    liked: List<TextChunk>,
    stories: List<TextChunk>,
): List<TextChunk> = composition + posters + liked + stories

data class EntertainerUiState(
    val vocab: String? = null,
    val chunks: List<TextChunk> = emptyList(),
    val loading: Boolean = false,
    val statusMessage: String = "",
)

class EntertainerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AnkiEntertainerApp
    private val prefs = app.preferences
    private val likedRepo = app.likedChunks
    private val promptEngine: PromptTemplateEngine = app.promptTemplateEngine
    private val mnemonicFallback = OfflineMnemonicFallback(app.hanziMetadataRepository)
    private val compositionLoader = CharacterCompositionLoader(app.hanziMetadataRepository)
    private val llmClient = RemoteLlmClient()

    private val _uiState = MutableStateFlow(EntertainerUiState(statusMessage = ""))
    val uiState: StateFlow<EntertainerUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    /** Test seam: number of prompt expansions performed in this VM instance. */
    var promptExpansionCount: Int = 0
        private set

    /** Last expanded prompt reused across chunk requests in a generation session. */
    var lastExpandedPrompt: String? = null
        private set

    fun openVocab(vocab: String) {
        val trimmed = vocab.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.vocab == trimmed && _uiState.value.chunks.isNotEmpty()) return
        generationJob?.cancel()
        _uiState.value = EntertainerUiState(vocab = trimmed)
        viewModelScope.launch {
            loadAndGenerate(trimmed, regenerateOnly = false)
        }
    }

    fun regenerate() {
        val vocab = _uiState.value.vocab ?: return
        generationJob?.cancel()
        viewModelScope.launch {
            loadAndGenerate(vocab, regenerateOnly = true)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            loading = false,
            statusMessage = "Generation stopped.",
        )
    }

    fun toggleLike(chunkId: String) {
        val vocab = _uiState.value.vocab ?: return
        val chunk = _uiState.value.chunks.find { it.id == chunkId } ?: return
        if (!chunk.likeable) return
        viewModelScope.launch {
            if (chunk.isLiked) {
                likedRepo.unlike(vocab, chunkId)
                _uiState.value = _uiState.value.copy(
                    chunks = _uiState.value.chunks.map {
                        if (it.id == chunkId) it.copy(isLiked = false) else it
                    },
                )
            } else {
                likedRepo.like(
                    vocab,
                    StoredChunk(id = chunk.id, text = chunk.text, modelName = chunk.modelName),
                )
                _uiState.value = _uiState.value.copy(
                    chunks = _uiState.value.chunks.map {
                        if (it.id == chunkId) it.copy(isLiked = true) else it
                    },
                )
            }
        }
    }

    private suspend fun loadAndGenerate(vocab: String, regenerateOnly: Boolean) {
        val settings = prefs.settings.first()
        val composition = compositionChunks(vocab)
        val posters = posterChunks()
        val liked = likedRepo.getLiked(vocab).map {
            TextChunk(id = it.id, text = it.text, isLiked = true, modelName = it.modelName)
        }

        if (!settings.isLlmConfigured()) {
            showMnemonicFallback(
                vocab = vocab,
                composition = composition,
                posters = posters,
                liked = liked,
                reason = "LLM not configured — showing local mnemonic stories.",
            )
            return
        }

        val targetCount = settings.chunkCount.coerceAtLeast(liked.size)
        val needed = (targetCount - liked.size).coerceAtLeast(0)

        _uiState.value = _uiState.value.copy(
            vocab = vocab,
            chunks = mergeSessionChunks(composition, posters, liked, emptyList()),
            loading = needed > 0,
            statusMessage = if (needed > 0) "Generating chunks…" else "Ready",
        )

        if (needed == 0) return

        // Expand the prompt once per vocabulary generation session; reuse for every chunk.
        val expansion: PromptExpansionResult = promptEngine.expand(settings.chunkPrompt, vocab)
        promptExpansionCount += 1
        lastExpandedPrompt = expansion.prompt
        val systemPrompt = expansion.prompt
        val warningSuffix = if (expansion.warnings.isNotEmpty()) {
            " (" + expansion.warnings.first() + ")"
        } else {
            ""
        }

        generationJob = viewModelScope.launch {
            val generated = mutableListOf<TextChunk>()
            try {
                val providers = settings.configuredProviders()
                repeat(needed) { index ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Generating chunk ${index + 1} of $needed…$warningSuffix",
                    )
                    val result = llmClient.generateChunkWithFallback(providers, systemPrompt)
                    val chunk = TextChunk(
                        id = LikedChunksRepository.newId(),
                        text = result.text,
                        isLiked = false,
                        modelName = result.modelName,
                    )
                    generated.add(chunk)
                    _uiState.value = _uiState.value.copy(
                        chunks = mergeSessionChunks(composition, posters, liked, generated),
                    )
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = if (expansion.warnings.isNotEmpty()) {
                        "Ready — " + expansion.warnings.first()
                    } else {
                        "Ready"
                    },
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    chunks = mergeSessionChunks(composition, posters, liked, generated),
                    loading = false,
                    statusMessage = "Generation stopped.",
                )
            } catch (e: Exception) {
                if (generated.isEmpty()) {
                    showMnemonicFallback(
                        vocab = vocab,
                        composition = composition,
                        posters = posters,
                        liked = liked,
                        reason = "LLM unavailable — showing local mnemonic stories. " +
                            (e.message ?: "Generation failed."),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        chunks = mergeSessionChunks(composition, posters, liked, generated),
                        loading = false,
                        statusMessage = e.message ?: "Generation failed.",
                    )
                }
            }
        }
    }

    private suspend fun compositionChunks(vocab: String): List<TextChunk> =
        compositionLoader.loadCards(vocab).map { card ->
            TextChunk(
                id = "composition:${card.character}",
                text = card.text,
                isLiked = false,
                modelName = CharacterCompositionLoader.MODEL_LABEL,
                likeable = false,
            )
        }

    private suspend fun showMnemonicFallback(
        vocab: String,
        composition: List<TextChunk>,
        posters: List<TextChunk>,
        liked: List<TextChunk>,
        reason: String,
    ) {
        val stories = mnemonicFallback.loadStories(vocab)
        val offlineChunks = stories.map { story ->
            TextChunk(
                id = LikedChunksRepository.newId(),
                text = story.text,
                isLiked = false,
                modelName = OfflineMnemonicFallback.MODEL_LABEL,
            )
        }
        val status = when {
            composition.isNotEmpty() || offlineChunks.isNotEmpty() -> reason
            liked.isNotEmpty() -> "$reason No mnemonic stories found for this vocabulary."
            else -> "$reason No mnemonic stories found — add Han characters or configure an LLM in Settings."
        }
        _uiState.value = _uiState.value.copy(
            vocab = vocab,
            chunks = mergeSessionChunks(composition, posters, liked, offlineChunks),
            loading = false,
            statusMessage = status,
        )
    }
}
