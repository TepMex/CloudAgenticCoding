package com.tepmex.byokassistedreader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.byokassistedreader.data.LlmClient
import com.tepmex.byokassistedreader.data.ReaderSettings
import com.tepmex.byokassistedreader.data.SettingsStore
import com.tepmex.byokassistedreader.domain.ColoredSpan
import com.tepmex.byokassistedreader.domain.EpubBook
import com.tepmex.byokassistedreader.domain.GlossPage
import com.tepmex.byokassistedreader.domain.KnownLexicon
import com.tepmex.byokassistedreader.domain.Prompts
import com.tepmex.byokassistedreader.domain.ReadingLayer
import com.tepmex.byokassistedreader.domain.Sentence
import com.tepmex.byokassistedreader.domain.alignParts
import com.tepmex.byokassistedreader.domain.packPages
import com.tepmex.byokassistedreader.domain.parseEpub
import com.tepmex.byokassistedreader.domain.parseGloss
import com.tepmex.byokassistedreader.domain.parseStpvo
import com.tepmex.byokassistedreader.domain.rubyRows
import com.tepmex.byokassistedreader.domain.splitSentences
import com.tepmex.byokassistedreader.domain.visibleGloss
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class Route { SHELF, READER, SETTINGS }

sealed interface AssistState {
    data object Idle : AssistState
    data object Loading : AssistState
    data class Structure(val spans: List<ColoredSpan>) : AssistState
    data class Gloss(val page: GlossPage) : AssistState
    data class Failed(val message: String) : AssistState
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val llm = LlmClient()
    private val cache = ConcurrentHashMap<String, AssistState>()

    val settings: StateFlow<ReaderSettings> = store.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReaderSettings(),
    )

    val route = MutableStateFlow(Route.SHELF)
    val book = MutableStateFlow<EpubBook?>(null)
    val pages = MutableStateFlow<List<List<Sentence>>>(emptyList())
    val pageIndex = MutableStateFlow(0)
    val layer = MutableStateFlow(ReadingLayer.TEXT)
    val status = MutableStateFlow<String?>(null)
    val assist = MutableStateFlow<AssistState>(AssistState.Idle)
    private var settingsReturn = Route.SHELF
    private var assistJob: Job? = null
    private var layoutKey = ""
    private var sentences: List<Sentence> = emptyList()

    val pageText: String
        get() = pages.value.getOrNull(pageIndex.value).orEmpty().joinToString("") { it.text }

    fun openSettings() {
        settingsReturn = route.value
        route.value = Route.SETTINGS
    }

    fun closeSettings() {
        route.value = if (settingsReturn == Route.READER && book.value != null) Route.READER else Route.SHELF
    }

    fun saveSettings(baseUrl: String, token: String, model: String, knownWords: String, volumeKeys: Boolean) {
        viewModelScope.launch {
            store.saveConnection(baseUrl, token, model, knownWords, volumeKeys)
            cache.clear()
            closeSettings()
            refreshAssist()
        }
    }

    fun openBook(uri: Uri) {
        viewModelScope.launch {
            status.value = "Открываю…"
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("Файл недоступен")
                }
                val parsed = withContext(Dispatchers.IO) { parseEpub(bytes) }
                book.value = parsed
                sentences = splitSentences(parsed.plainText)
                pages.value = listOf(sentences)
                val saved = settings.value
                val index = if (saved.bookUri == uri.toString()) saved.pageIndex else 0
                pageIndex.value = index.coerceAtLeast(0)
                layer.value = ReadingLayer.TEXT
                layoutKey = ""
                cache.clear()
                assist.value = AssistState.Idle
                status.value = null
                route.value = Route.READER
                store.saveBook(uri.toString(), pageIndex.value)
            } catch (e: Exception) {
                status.value = e.message ?: "Не удалось открыть EPUB"
            }
        }
    }

    fun continueReading() {
        if (book.value != null) route.value = Route.READER
    }

    fun relayout(columns: Int, rowHeightPx: Int, viewportPx: Int) {
        if (sentences.isEmpty() || columns < 1 || rowHeightPx < 1 || viewportPx < 1) return
        val key = "$columns:$rowHeightPx:$viewportPx:${sentences.size}"
        if (key == layoutKey && pages.value.isNotEmpty()) return
        layoutKey = key
        val packed = packPages(sentences, { sentence ->
            val rows = rubyRows(sentence.text, columns) { "" }.size.coerceAtLeast(1)
            rows * rowHeightPx
        }, viewportPx)
        pages.value = packed
        val index = pageIndex.value.coerceIn(0, (packed.size - 1).coerceAtLeast(0))
        pageIndex.value = index
        refreshAssist()
    }

    fun turnPage(forward: Boolean) {
        val count = pages.value.size
        if (count == 0) return
        val next = (pageIndex.value + if (forward) 1 else -1).coerceIn(0, count - 1)
        if (next == pageIndex.value) return
        pageIndex.value = next
        viewModelScope.launch { store.saveBook(settings.value.bookUri, next) }
        refreshAssist()
    }

    fun stepLayer(forward: Boolean) {
        if (route.value != Route.READER) return
        layer.value = layer.value.step(forward)
        refreshAssist()
    }

    fun refreshAssist() {
        assistJob?.cancel()
        val currentLayer = layer.value
        val text = pageText
        if (currentLayer == ReadingLayer.TEXT || currentLayer == ReadingLayer.PINYIN || text.isBlank()) {
            assist.value = AssistState.Idle
            return
        }
        val prefs = settings.value
        if (!prefs.endpointReady) {
            assist.value = AssistState.Failed("Укажите base URL и модель в настройках.")
            return
        }
        val key = listOf(currentLayer.name, prefs.baseUrl, prefs.model, prefs.knownWords, text).joinToString("|")
        cache[key]?.let {
            assist.value = it
            return
        }
        assist.value = AssistState.Loading
        assistJob = viewModelScope.launch {
            try {
                val state = when (currentLayer) {
                    ReadingLayer.STRUCTURE -> requestStructure(prefs, text)
                    ReadingLayer.GLOSS_ZH -> requestGloss(prefs, text, russian = false)
                    ReadingLayer.GLOSS_RU -> requestGloss(prefs, text, russian = true)
                    else -> AssistState.Idle
                }
                cache[key] = state
                if (pageText == text && layer.value == currentLayer) assist.value = state
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pageText == text && layer.value == currentLayer) {
                    assist.value = AssistState.Failed(e.message ?: "Ошибка запроса")
                }
            }
        }
    }

    private suspend fun requestStructure(prefs: ReaderSettings, text: String): AssistState {
        val complete = splitSentences(text).filter { it.complete }
        if (complete.isEmpty()) return AssistState.Structure(emptyList())
        val raw = llm.complete(
            prefs.baseUrl,
            prefs.token,
            prefs.model,
            Prompts.stpvoSystem,
            Prompts.stpvoUser(complete.map { it.text }),
        )
        val parsed = parseStpvo(raw)
        val spans = ArrayList<ColoredSpan>()
        var cursor = 0
        for (sentence in complete) {
            val at = text.indexOf(sentence.text, cursor).takeIf { it >= 0 } ?: cursor
            val parts = parsed.firstOrNull { it.text == sentence.text }?.parts
                ?: parsed.getOrNull(complete.indexOf(sentence))?.parts
                ?: emptyList()
            for (span in alignParts(sentence.text, parts)) {
                spans.add(ColoredSpan(at + span.start, at + span.end, span.role))
            }
            cursor = at + sentence.text.length
        }
        return AssistState.Structure(spans)
    }

    private suspend fun requestGloss(prefs: ReaderSettings, text: String, russian: Boolean): AssistState {
        val known = KnownLexicon.words(prefs.knownWords)
        val raw = llm.complete(
            prefs.baseUrl,
            prefs.token,
            prefs.model,
            Prompts.glossSystem(russian),
            Prompts.glossUser(text, known),
        )
        val page = visibleGloss(text, known.toSet(), parseGloss(raw))
        return AssistState.Gloss(page)
    }
}
