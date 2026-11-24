package com.tepmex.hanziinfogf14.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.hanziinfogf14.data.CatalogJson
import com.tepmex.hanziinfogf14.data.DeepLinkParser
import com.tepmex.hanziinfogf14.data.EmbeddingModel
import com.tepmex.hanziinfogf14.data.GlyphEmbeddingIndex
import com.tepmex.hanziinfogf14.data.HanziCatalog
import com.tepmex.hanziinfogf14.data.HanziText
import com.tepmex.hanziinfogf14.data.LiteRtGlyphEmbedder
import com.tepmex.hanziinfogf14.data.ModelDownloader
import com.tepmex.hanziinfogf14.domain.HexLayout
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ModelPhase {
    Absent,
    Downloading,
    Ready,
    Failed,
}

sealed class LookalikeNote {
    data object NeedsModel : LookalikeNote()
    data object Computing : LookalikeNote()
    data class Failed(val message: String) : LookalikeNote()
}

class MapViewModel(app: Application) : AndroidViewModel(app) {
    var catalog by mutableStateOf<HanziCatalog?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var embeddingError by mutableStateOf<String?>(null)
        private set
    var center by mutableStateOf("")
        private set
    var query by mutableStateOf("")
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var copyPulse by mutableIntStateOf(0)
        private set
    var settingsOpen by mutableStateOf(false)
        private set
    var lookalikes by mutableStateOf<List<String>>(emptyList())
        private set
    var lookalikeNote by mutableStateOf<LookalikeNote?>(null)
        private set
    var modelPhase by mutableStateOf(ModelPhase.Absent)
        private set
    var downloadFraction by mutableFloatStateOf(0f)
        private set
    var downloadError by mutableStateOf<String?>(null)
        private set

    private val history = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modelFile = File(app.filesDir, "models/${EmbeddingModel.FILE_NAME}")
    private val downloader = ModelDownloader(EmbeddingModel.URL, modelFile, EmbeddingModel.BYTES)
    private val embedder = LiteRtGlyphEmbedder(app, modelFile)
    private var embeddings: GlyphEmbeddingIndex? = null
    private var downloadJob: Job? = null
    private var lookalikeJob: Job? = null
    private var lookalikeToken = 0

    init {
        modelPhase = if (downloader.ready) ModelPhase.Ready else ModelPhase.Absent
        viewModelScope.launch {
            try {
                catalog = withContext(Dispatchers.IO) {
                    val text = app.assets.open(CATALOG_ASSET)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                    CatalogJson.parse(text)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadError = error.message ?: error.javaClass.simpleName
                return@launch
            }
            try {
                embeddings = withContext(Dispatchers.IO) {
                    val vectors = app.assets.open(GlyphEmbeddingIndex.ASSET).use { it.readBytes() }
                    GlyphEmbeddingIndex.read(vectors)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                embeddingError = error.message ?: error.javaClass.simpleName
            }
            refreshLookalikes()
        }
    }

    fun onQueryChange(raw: String) {
        val ideographs = HanziText.ideographs(raw)
        if (ideographs.isEmpty()) {
            query = raw
            return
        }
        open(ideographs.last(), recordHistory = true)
    }

    fun open(hanzi: String, recordHistory: Boolean) {
        if (hanzi.isEmpty()) return
        if (hanzi == center) {
            query = hanzi
            return
        }
        if (recordHistory && center.isNotEmpty()) {
            history.addLast(center)
            canGoBack = true
        }
        center = hanzi
        query = hanzi
        refreshLookalikes()
    }

    fun back() {
        if (history.isEmpty()) return
        val previous = history.removeLast()
        center = previous
        query = previous
        canGoBack = history.isNotEmpty()
        refreshLookalikes()
    }

    fun linkForCenter(): String? {
        if (center.isEmpty()) return null
        return DeepLinkParser.format(center)
    }

    fun noteLinkCopied() {
        copyPulse += 1
    }

    fun openSettings() {
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun downloadModel() {
        if (downloadJob?.isActive == true || downloader.ready) return
        downloadError = null
        downloadFraction = 0f
        modelPhase = ModelPhase.Downloading
        downloadJob = viewModelScope.launch {
            try {
                var shown = -1
                val job = coroutineContext[Job]
                withContext(Dispatchers.IO) {
                    downloader.download(
                        onProgress = { read, total ->
                            val percent = if (total <= 0L) 0 else ((read * 100) / total).toInt()
                            if (percent != shown) {
                                shown = percent
                                val fraction = percent / 100f
                                mainHandler.post { downloadFraction = fraction }
                            }
                        },
                        isCancelled = { job?.isActive == false },
                    )
                }
                downloadFraction = 1f
                modelPhase = ModelPhase.Ready
                refreshLookalikes()
            } catch (error: CancellationException) {
                modelPhase = if (downloader.ready) ModelPhase.Ready else ModelPhase.Absent
                throw error
            } catch (error: Exception) {
                downloadError = error.message ?: error.javaClass.simpleName
                modelPhase = ModelPhase.Failed
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun deleteModel() {
        downloadJob?.cancel()
        embedder.close()
        downloader.delete()
        downloadFraction = 0f
        downloadError = null
        modelPhase = ModelPhase.Absent
        refreshLookalikes()
    }

    override fun onCleared() {
        embedder.close()
        super.onCleared()
    }

    private fun refreshLookalikes() {
        val token = ++lookalikeToken
        lookalikeJob?.cancel()
        val current = center
        val index = embeddings
        val loaded = catalog
        if (current.isEmpty() || index == null || loaded == null) {
            lookalikes = emptyList()
            lookalikeNote = null
            return
        }
        val excluded = charactersOnRays(current, loaded) + current
        if (index.contains(current)) {
            lookalikes = index.nearest(current, HexLayout.MAX_BRANCHES, excluded).map { it.hanzi }
            lookalikeNote = null
            return
        }
        if (!downloader.ready) {
            lookalikes = emptyList()
            lookalikeNote = LookalikeNote.NeedsModel
            return
        }
        lookalikeNote = LookalikeNote.Computing
        lookalikes = emptyList()
        lookalikeJob = viewModelScope.launch {
            try {
                val vector = withContext(Dispatchers.IO) { embedder.embed(current) }
                if (token != lookalikeToken) return@launch
                lookalikes = index.nearest(vector, HexLayout.MAX_BRANCHES, excluded).map { it.hanzi }
                lookalikeNote = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != lookalikeToken) return@launch
                lookalikes = emptyList()
                lookalikeNote = LookalikeNote.Failed(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun charactersOnRays(character: String, loaded: HanziCatalog): Set<String> {
        return HexLayout.visibleBranches(loaded.branchesOf(character))
            .flatMap { it.characters }
            .toSet()
    }

    private companion object {
        const val CATALOG_ASSET = "hanzi_components_gf0014_6152.json"
    }
}
