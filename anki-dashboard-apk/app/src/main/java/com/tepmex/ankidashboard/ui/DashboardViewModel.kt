package com.tepmex.ankidashboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.ankidashboard.AnkiDashboardApp
import com.tepmex.ankidashboard.data.AnkiDroidRepository
import com.tepmex.ankidashboard.data.CollectionReader
import com.tepmex.ankidashboard.data.DashboardData
import com.tepmex.ankidashboard.data.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val selectedDecks: Set<String> = emptySet(),
    val data: DashboardData? = null,
    val collectionUri: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AnkiDashboardApp
    val ankiRepository = AnkiDroidRepository(application)
    private val collectionReader = CollectionReader(application)
    private val dashboardRepository = DashboardRepository(
        application,
        ankiRepository,
        collectionReader,
    )

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<Pair<String, String>?>(null)
    private val _data = MutableStateFlow<DashboardData?>(null)
    private val _reloadToken = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        _loading,
        _error,
        app.preferences.selectedDecks,
        app.preferences.collectionUri,
        _data,
    ) { loading, error, decks, collectionUri, data ->
        DashboardUiState(
            loading = loading,
            errorCode = error?.first,
            errorMessage = error?.second,
            selectedDecks = decks,
            data = data,
            collectionUri = collectionUri,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(),
    )

    init {
        viewModelScope.launch {
            combine(
                app.preferences.selectedDecks,
                app.preferences.collectionUri,
                _reloadToken,
            ) { decks, uri, _ -> decks to uri }
                .collect { (decks, uri) -> loadDashboard(decks.toList(), uri) }
        }
    }

    fun setSelectedDecks(decks: Set<String>) {
        viewModelScope.launch {
            app.preferences.setSelectedDecks(decks)
        }
    }

    fun setCollectionUri(uri: String?) {
        viewModelScope.launch {
            app.preferences.setCollectionUri(uri)
            collectionReader.close()
            _reloadToken.value++
        }
    }

    fun reload() {
        _reloadToken.value++
    }

    private fun loadDashboard(selectedDecks: List<String>, collectionUri: String?) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                if (!ankiRepository.hasAnkiInstalled()) {
                    _error.value = "anki_missing" to "Install AnkiDroid to use this dashboard."
                    _data.value = null
                    return@launch
                }
                if (!ankiRepository.hasAnkiPermission()) {
                    _error.value = "anki_permission" to "Grant AnkiDroid database access to load statistics."
                    _data.value = null
                    return@launch
                }
                val result = dashboardRepository.loadDashboard(selectedDecks, collectionUri)
                result.onSuccess { dashboard ->
                    val deckMap = dashboard.deckNamesAndIds
                    if (deckMap.isNotEmpty() && selectedDecks.isNotEmpty()) {
                        val pruned = reconcileSelectedDecks(selectedDecks, deckMap.keys)
                        if (pruned != selectedDecks.toSet()) {
                            app.preferences.setSelectedDecks(pruned)
                            return@launch
                        }
                    }
                    _data.value = dashboard
                }.onFailure { e ->
                    _error.value = "load_failed" to (e.message ?: "Failed to load dashboard")
                    _data.value = null
                }
            } finally {
                _loading.value = false
            }
        }
    }

    private fun reconcileSelectedDecks(
        selected: List<String>,
        validNames: Set<String>,
    ): Set<String> =
        selected.filter { deckName ->
            validNames.contains(deckName) ||
                validNames.any { it.startsWith("$deckName::") }
        }.toSet()

    override fun onCleared() {
        collectionReader.close()
        super.onCleared()
    }
}
