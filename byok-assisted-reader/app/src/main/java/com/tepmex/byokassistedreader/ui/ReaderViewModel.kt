package com.tepmex.byokassistedreader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.byokassistedreader.data.LlmClient
import com.tepmex.byokassistedreader.data.ReaderSettings
import com.tepmex.byokassistedreader.data.SettingsStore
import com.tepmex.byokassistedreader.domain.AssistCard
import com.tepmex.byokassistedreader.domain.ColoredSpan
import com.tepmex.byokassistedreader.domain.EpubBook
import com.tepmex.byokassistedreader.domain.GlossPage
import com.tepmex.byokassistedreader.domain.KnownLexicon
import com.tepmex.byokassistedreader.domain.OverlaySwipe
import com.tepmex.byokassistedreader.domain.PinyinScope
import com.tepmex.byokassistedreader.domain.Prompts
import com.tepmex.byokassistedreader.domain.ReadingLayer
import com.tepmex.byokassistedreader.domain.ReferencePage
import com.tepmex.byokassistedreader.domain.ReferenceSpan
import com.tepmex.byokassistedreader.domain.Sentence
import com.tepmex.byokassistedreader.domain.alignParts
import com.tepmex.byokassistedreader.domain.alignReference
import com.tepmex.byokassistedreader.domain.overlaySwipe
import com.tepmex.byokassistedreader.domain.packPages
import com.tepmex.byokassistedreader.domain.parseEpub
import com.tepmex.byokassistedreader.domain.parseGloss
import com.tepmex.byokassistedreader.domain.parsePageReading
import com.tepmex.byokassistedreader.domain.parseReference
import com.tepmex.byokassistedreader.domain.parseStpvo
import com.tepmex.byokassistedreader.domain.rubyRows
import com.tepmex.byokassistedreader.domain.splitSentences
import com.tepmex.byokassistedreader.domain.visibleGloss
import com.tepmex.byokassistedreader.domain.visibleReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    data class Notes(
        val glossZh: GlossPage,
        val glossRu: GlossPage,
        val reference: ReferencePage,
        val spans: List<ReferenceSpan>,
    ) : AssistState
    data class Failed(val message: String) : AssistState
}

/** In-flight state of the Russian window that is open now. */
sealed interface ReadingStatus {
    data object Idle : ReadingStatus
    data object Loading : ReadingStatus
    data class Failed(val message: String) : ReadingStatus
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
    val assistCard = MutableStateFlow(AssistCard.NONE)
    val structureLegend = MutableStateFlow(false)
    val pinyinScope = MutableStateFlow(PinyinScope.UNKNOWN)
    val literalText = MutableStateFlow<String?>(null)
    val retellingText = MutableStateFlow<String?>(null)
    val readingStatus = MutableStateFlow<ReadingStatus>(ReadingStatus.Idle)
    val status = MutableStateFlow<String?>(null)
    val assist = MutableStateFlow<AssistState>(AssistState.Idle)
    private var settingsReturn = Route.SHELF
    private var assistJob: Job? = null
    private val readingJobs = HashMap<AssistCard, Job>()
    private val readingText = HashMap<String, String>()
    private var readingPage = ""
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

    fun saveSettings(
        baseUrl: String,
        token: String,
        model: String,
        knownWords: String,
        volumeKeys: Boolean,
        charsPerLine: Int,
    ) {
        viewModelScope.launch {
            store.saveConnection(baseUrl, token, model, knownWords, volumeKeys, charsPerLine)
            cache.clear()
            cancelReadings()
            readingText.clear()
            readingPage = ""
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
                assistCard.value = AssistCard.NONE
                structureLegend.value = false
                pinyinScope.value = PinyinScope.UNKNOWN
                layoutKey = ""
                cache.clear()
                cancelReadings()
                readingText.clear()
                readingPage = ""
                literalText.value = null
                retellingText.value = null
                readingStatus.value = ReadingStatus.Idle
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

    /** Horizontal drag on the passage. Does not turn the page. */
    fun applyOverlaySwipe(dragPx: Float) {
        when (val swipe = overlaySwipe(layer.value, dragPx)) {
            null -> Unit
            OverlaySwipe.ToggleLegend -> structureLegend.value = !structureLegend.value
            OverlaySwipe.TogglePinyin -> pinyinScope.value = pinyinScope.value.toggle()
            is OverlaySwipe.StepCard -> {
                assistCard.value = assistCard.value.step(swipe.forward)
                loadReadingIfNeeded()
            }
        }
    }

    /** Retry a failed layer request, or the Russian window when that text is still missing. */
    fun retryAssist() {
        val card = assistCard.value
        val readingMissing = layer.value == ReadingLayer.ASSIST &&
            card.needsReading &&
            currentReading(card) == null
        if (assist.value is AssistState.Failed) {
            refreshAssist()
            return
        }
        if (readingMissing) loadReadingIfNeeded(force = true)
    }

    fun refreshAssist() {
        assistJob?.cancel()
        val currentLayer = layer.value
        val text = pageText
        if (text != readingPage) {
            cancelReadings()
            readingPage = text
        }
        syncVisibleReadings(text)
        if (currentLayer == ReadingLayer.TEXT || currentLayer == ReadingLayer.PINYIN || text.isBlank()) {
            readingStatus.value = ReadingStatus.Idle
            assist.value = AssistState.Idle
            return
        }
        val prefs = settings.value
        if (!prefs.endpointReady) {
            assist.value = AssistState.Failed("Укажите base URL и модель в настройках.")
            loadReadingIfNeeded()
            return
        }
        val key = cacheKey(currentLayer, prefs, text)
        cache[key]?.let {
            assist.value = it
            loadReadingIfNeeded()
            return
        }
        assist.value = AssistState.Loading
        loadReadingIfNeeded()
        assistJob = viewModelScope.launch {
            try {
                val state = when (currentLayer) {
                    ReadingLayer.STRUCTURE -> requestStructure(prefs, text)
                    ReadingLayer.ASSIST -> requestNotes(prefs, text)
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

    private fun loadReadingIfNeeded(force: Boolean = false) {
        if (layer.value != ReadingLayer.ASSIST) {
            readingStatus.value = ReadingStatus.Idle
            return
        }
        val card = assistCard.value
        if (!card.needsReading) {
            readingStatus.value = ReadingStatus.Idle
            return
        }
        val text = pageText
        if (text.isBlank()) return
        if (!force && currentReading(card) != null) {
            readingStatus.value = ReadingStatus.Idle
            return
        }
        val existing = readingJobs[card]
        if (!force && existing?.isActive == true) {
            readingStatus.value = ReadingStatus.Loading
            return
        }
        existing?.cancel()
        val prefs = settings.value
        if (!prefs.endpointReady) {
            readingStatus.value = ReadingStatus.Failed("Укажите base URL и модель в настройках.")
            return
        }
        readingStatus.value = ReadingStatus.Loading
        readingJobs[card] = viewModelScope.launch {
            try {
                val body = requestReading(prefs, text, card)
                if (!isActive) return@launch
                publishReading(text, card, body)
                if (pageText == text && assistCard.value == card) {
                    readingStatus.value = ReadingStatus.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pageText == text && assistCard.value == card) {
                    readingStatus.value = ReadingStatus.Failed(e.message ?: "Ошибка запроса")
                }
            }
        }
    }

    private suspend fun requestReading(prefs: ReaderSettings, text: String, card: AssistCard): String {
        val system = when (card) {
            AssistCard.LITERAL -> Prompts.literalSystem
            AssistCard.RETELLING -> Prompts.retellingSystem
            else -> error("not a reading")
        }
        val raw = llm.complete(prefs.baseUrl, prefs.token, prefs.model, system, Prompts.readingUser(text))
        return parsePageReading(raw)
    }

    private fun publishReading(page: String, card: AssistCard, body: String) {
        readingText[readingKey(page, card)] = body
        if (pageText != page) return
        when (card) {
            AssistCard.LITERAL -> literalText.value = body
            AssistCard.RETELLING -> retellingText.value = body
            else -> Unit
        }
    }

    private fun syncVisibleReadings(page: String) {
        literalText.value = readingText[readingKey(page, AssistCard.LITERAL)]
        retellingText.value = readingText[readingKey(page, AssistCard.RETELLING)]
    }

    private fun currentReading(card: AssistCard): String? = when (card) {
        AssistCard.LITERAL -> literalText.value
        AssistCard.RETELLING -> retellingText.value
        else -> null
    }

    private fun readingKey(page: String, card: AssistCard): String = "$page\u0000${card.name}"

    private fun cancelReadings() {
        readingJobs.values.forEach { it.cancel() }
        readingJobs.clear()
    }

    private fun cacheKey(layer: ReadingLayer, prefs: ReaderSettings, text: String): String =
        listOf(layer.name, prefs.baseUrl, prefs.model, prefs.knownWords, text).joinToString("|")

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

    private suspend fun requestNotes(prefs: ReaderSettings, text: String): AssistState = coroutineScope {
        val reference = async { loadReference(prefs, text) }
        val glossZh = async { loadGloss(prefs, text, russian = false) }
        val glossRu = async { loadGloss(prefs, text, russian = true) }
        val (page, spans) = reference.await()
        AssistState.Notes(
            glossZh = glossZh.await(),
            glossRu = glossRu.await(),
            reference = page,
            spans = spans,
        )
    }

    private suspend fun loadReference(
        prefs: ReaderSettings,
        text: String,
    ): Pair<ReferencePage, List<ReferenceSpan>> {
        val raw = llm.complete(
            prefs.baseUrl,
            prefs.token,
            prefs.model,
            Prompts.referenceSystem,
            Prompts.referenceUser(text),
        )
        val page = visibleReference(text, parseReference(raw))
        return page to alignReference(text, page)
    }

    private suspend fun loadGloss(prefs: ReaderSettings, text: String, russian: Boolean): GlossPage {
        val known = KnownLexicon.words(prefs.knownWords)
        val raw = llm.complete(
            prefs.baseUrl,
            prefs.token,
            prefs.model,
            Prompts.glossSystem(russian),
            Prompts.glossUser(text, known),
        )
        return visibleGloss(text, known.toSet(), parseGloss(raw))
    }
}
