package com.tepmex.hanziinfogf14.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.hanziinfogf14.data.CatalogJson
import com.tepmex.hanziinfogf14.data.DeepLinkParser
import com.tepmex.hanziinfogf14.data.HanziCatalog
import com.tepmex.hanziinfogf14.data.HanziText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(app: Application) : AndroidViewModel(app) {
    var catalog by mutableStateOf<HanziCatalog?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var center by mutableStateOf("")
        private set
    var query by mutableStateOf("")
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var copyPulse by mutableIntStateOf(0)
        private set

    private val history = kotlin.collections.ArrayDeque<String>()

    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val text = app.assets.open(ASSET)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                    CatalogJson.parse(text)
                }
                catalog = loaded
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadError = error.message ?: error.javaClass.simpleName
            }
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
    }

    fun back() {
        if (history.isEmpty()) return
        val previous = history.removeLast()
        center = previous
        query = previous
        canGoBack = history.isNotEmpty()
    }

    fun linkForCenter(): String? {
        if (center.isEmpty()) return null
        return DeepLinkParser.format(center)
    }

    fun noteLinkCopied() {
        copyPulse += 1
    }

    private companion object {
        const val ASSET = "hanzi_components_gf0014_6152.json"
    }
}
