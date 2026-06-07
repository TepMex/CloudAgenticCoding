package com.tepmex.zoulushang.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.zoulushang.data.AppRepository
import com.tepmex.zoulushang.data.CityBoundary
import com.tepmex.zoulushang.geo.GeoJsonParser
import com.tepmex.zoulushang.geo.TileMath
import com.tepmex.zoulushang.importing.ImportProgress
import com.tepmex.zoulushang.nominatim.NominatimSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox

data class AppUiState(
    val cities: List<CityBoundary> = emptyList(),
    val selectedCity: CityBoundary? = null,
    val visitedLookup: HashMap<Long, Boolean> = hashMapOf(),
    val visitedTileCount: Int = 0,
    val mapBounds: BoundingBox? = null,
    val citySearchQuery: String = "",
    val citySearchResults: List<NominatimSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isSavingCity: Boolean = false,
    val importProgress: ImportProgress? = null,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val showCityPicker: Boolean = false,
    val showImport: Boolean = false,
)

class AppViewModel(private val repository: AppRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val selectedCityId = repository.selectedCityId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedCityId.collect { cityId ->
                refreshCities(cityId)
            }
        }
    }

    fun refreshCities(preferredCityId: Long? = null) {
        viewModelScope.launch {
            val cities = repository.getCities()
            val selectedId = preferredCityId ?: selectedCityId.value
            val selected = selectedId?.let { id -> cities.find { it.id == id } }
                ?: cities.firstOrNull()
            if (selected != null && selected.id != selectedCityId.value) {
                repository.setSelectedCityId(selected.id)
            }
            val lookup = selected?.let { repository.getVisitedTileLookup(it.id) } ?: hashMapOf()
            val count = selected?.let { repository.getVisitedTileCount(it.id) } ?: 0
            val tileBounds = TileMath.boundsFromTileKeys(lookup.keys)
            val cityBounds = selected?.let { city ->
                runCatching { GeoJsonParser.parsePolygon(city.geoJson).boundingBox }.getOrNull()
            }
            val bounds = tileBounds ?: cityBounds
            _uiState.update {
                it.copy(
                    cities = cities,
                    selectedCity = selected,
                    visitedLookup = lookup,
                    visitedTileCount = count,
                    mapBounds = bounds,
                )
            }
        }
    }

    fun selectCity(city: CityBoundary) {
        viewModelScope.launch {
            repository.setSelectedCityId(city.id)
            _uiState.update { it.copy(showCityPicker = false) }
        }
    }

    fun setShowCityPicker(show: Boolean) {
        _uiState.update { it.copy(showCityPicker = show, citySearchQuery = "", citySearchResults = emptyList()) }
    }

    fun setShowImport(show: Boolean) {
        _uiState.update { it.copy(showImport = show, importProgress = null, errorMessage = null) }
    }

    fun updateCitySearchQuery(query: String) {
        _uiState.update { it.copy(citySearchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.length < 2) {
                _uiState.update { it.copy(citySearchResults = emptyList(), isSearching = false) }
                return@launch
            }
            _uiState.update { it.copy(isSearching = true) }
            delay(400)
            runCatching { repository.searchCities(query) }
                .onSuccess { results ->
                    _uiState.update { it.copy(citySearchResults = results, isSearching = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = e.message ?: "City search failed",
                        )
                    }
                }
        }
    }

    fun addCityFromSearch(result: NominatimSearchResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingCity = true, errorMessage = null) }
            runCatching { repository.saveCityFromSearch(result) }
                .onSuccess { cityId ->
                    repository.setSelectedCityId(cityId)
                    refreshCities(cityId)
                    _uiState.update {
                        it.copy(
                            isSavingCity = false,
                            showCityPicker = false,
                            citySearchQuery = "",
                            citySearchResults = emptyList(),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSavingCity = false,
                            errorMessage = e.message ?: "Failed to save city boundary",
                        )
                    }
                }
        }
    }

    fun importTakeoutDb(uri: Uri) {
        val cityId = _uiState.value.selectedCity?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = null, errorMessage = null) }
            runCatching { repository.importTakeoutDb(cityId, uri) { progress ->
                _uiState.update { state -> state.copy(importProgress = progress) }
            } }
                .onSuccess { count ->
                    refreshCities(cityId)
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importProgress = ImportProgress(
                                ImportProgress.Stage.DONE,
                                tilesFound = count,
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = e.message ?: "Import failed",
                        )
                    }
                }
        }
    }

    fun importTakeoutJson(uri: Uri) {
        val cityId = _uiState.value.selectedCity?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = null, errorMessage = null) }
            runCatching { repository.importTakeoutJson(cityId, uri) { progress ->
                _uiState.update { state -> state.copy(importProgress = progress) }
            } }
                .onSuccess { count ->
                    refreshCities(cityId)
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importProgress = ImportProgress(
                                ImportProgress.Stage.DONE,
                                tilesFound = count,
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = e.message ?: "Import failed",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
