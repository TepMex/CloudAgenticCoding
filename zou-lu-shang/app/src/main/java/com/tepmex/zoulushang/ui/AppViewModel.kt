package com.tepmex.zoulushang.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.zoulushang.data.AppRepository
import com.tepmex.zoulushang.data.CityBoundary
import com.tepmex.zoulushang.data.MapSettings
import com.tepmex.zoulushang.data.SettingsDraft
import com.tepmex.zoulushang.geo.GeoJsonParser
import com.tepmex.zoulushang.geo.TileMath
import com.tepmex.zoulushang.importing.ImportProgress
import com.tepmex.zoulushang.location.FillMapForegroundService
import com.tepmex.zoulushang.location.FillMapSession
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
    val visitedLookup: HashMap<Long, Int> = hashMapOf(),
    val liveLookup: HashMap<Long, Int> = hashMapOf(),
    val visitedTileCount: Int = 0,
    val liveTileCount: Int = 0,
    val mapBounds: BoundingBox? = null,
    val isFillMapRunning: Boolean = false,
    val fillMapMinutesRemaining: Int = 0,
    val fillMapSamplesTaken: Int = 0,
    val recenterMyLocationToken: Int = 0,
    val citySearchQuery: String = "",
    val citySearchResults: List<NominatimSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isSavingCity: Boolean = false,
    val importProgress: ImportProgress? = null,
    val isImporting: Boolean = false,
    val takeoutDbUri: String? = null,
    val errorMessage: String? = null,
    val showCityPicker: Boolean = false,
    val showImport: Boolean = false,
    val showSettings: Boolean = false,
    val mapSettings: MapSettings = MapSettings(),
    val settingsDraft: SettingsDraft? = null,
    val isSavingSettings: Boolean = false,
)

class AppViewModel(
    private val repository: AppRepository,
    private val appContext: android.content.Context,
) : ViewModel() {
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
        viewModelScope.launch {
            repository.takeoutDbUri.collect { uri ->
                _uiState.update { it.copy(takeoutDbUri = uri) }
            }
        }
        viewModelScope.launch {
            repository.mapSettings.collect { settings ->
                refreshLookupsForSettings(settings)
            }
        }
        viewModelScope.launch {
            FillMapSession.state.collect { session ->
                updateFillMapUi(session)
                if (session.isRunning) {
                    while (FillMapSession.state.value.isRunning) {
                        delay(5_000)
                        updateFillMapUi(FillMapSession.state.value)
                    }
                }
            }
        }
    }

    private fun updateFillMapUi(session: com.tepmex.zoulushang.location.FillMapSessionState) {
        val minutesRemaining = session.endsAtMillis?.let { endsAt ->
            ((endsAt - System.currentTimeMillis()).coerceAtLeast(0) / 60_000).toInt()
        } ?: 0
        _uiState.update {
            it.copy(
                isFillMapRunning = session.isRunning,
                fillMapMinutesRemaining = minutesRemaining,
                fillMapSamplesTaken = session.samplesTaken,
            )
        }
    }

    fun refreshCities(preferredCityId: Long? = null, autoImportIfNeeded: Boolean = true) {
        viewModelScope.launch {
            loadCitiesState(preferredCityId, autoImportIfNeeded)
        }
    }

    private suspend fun loadCitiesState(preferredCityId: Long? = null, autoImportIfNeeded: Boolean = true) {
        val cities = repository.getCities()
        val selectedId = preferredCityId ?: selectedCityId.value
        val selected = selectedId?.let { id -> cities.find { it.id == id } }
            ?: cities.firstOrNull()
        if (selected != null && selected.id != selectedCityId.value) {
            repository.setSelectedCityId(selected.id)
        }
        val gridZoom = repository.getMapSettings().gridZoom
        val lookup = selected?.let { repository.getVisitedTileLookup(it.id, gridZoom) } ?: hashMapOf()
        val liveLookup = selected?.let { repository.getLiveTileLookup(it.id, gridZoom) } ?: hashMapOf()
        val count = lookup.size
        val liveCount = liveLookup.size
        val tileBounds = TileMath.boundsFromTileKeys((lookup.keys + liveLookup.keys).toSet())
        val cityBounds = selected?.let { city ->
            runCatching { GeoJsonParser.parsePolygon(city.geoJson).boundingBox }.getOrNull()
        }
        val bounds = tileBounds ?: cityBounds
        _uiState.update {
            it.copy(
                cities = cities,
                selectedCity = selected,
                visitedLookup = lookup,
                liveLookup = liveLookup,
                visitedTileCount = count,
                liveTileCount = liveCount,
                mapBounds = bounds,
            )
        }
        selected?.let { city ->
            observeLiveTiles(city.id)
        }
        if (autoImportIfNeeded && selected != null && count == 0 && !_uiState.value.isImporting) {
            maybeAutoImportFromSavedTakeoutDb(selected.id)
        }
    }

    private var liveTilesJob: Job? = null

    private fun observeLiveTiles(cityId: Long) {
        liveTilesJob?.cancel()
        liveTilesJob = viewModelScope.launch {
            repository.observeLiveTileLookup(cityId).collect { lookup ->
                val showLive = _uiState.value.mapSettings.showLiveGrid
                _uiState.update {
                    it.copy(
                        liveLookup = lookup,
                        liveTileCount = if (showLive) lookup.size else 0,
                    )
                }
            }
        }
    }

    private fun refreshLookupsForSettings(settings: MapSettings) {
        val cityId = _uiState.value.selectedCity?.id ?: run {
            _uiState.update { it.copy(mapSettings = settings) }
            return
        }
        viewModelScope.launch {
            val visitedLookup = repository.getVisitedTileLookup(cityId, settings.gridZoom)
            val liveLookup = repository.getLiveTileLookup(cityId, settings.gridZoom)
            _uiState.update {
                it.copy(
                    mapSettings = settings,
                    visitedLookup = visitedLookup,
                    liveLookup = liveLookup,
                    visitedTileCount = if (settings.showTakeoutGrid) visitedLookup.size else 0,
                    liveTileCount = if (settings.showLiveGrid) liveLookup.size else 0,
                )
            }
        }
    }

    fun startFillMap() {
        val cityId = _uiState.value.selectedCity?.id ?: return
        FillMapForegroundService.start(appContext, cityId)
    }

    fun stopFillMap() {
        FillMapForegroundService.stop(appContext)
    }

    fun recenterOnMyLocation() {
        _uiState.update { it.copy(recenterMyLocationToken = it.recenterMyLocationToken + 1) }
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

    fun setShowSettings(show: Boolean) {
        if (show) {
            val settings = _uiState.value.mapSettings
            _uiState.update {
                it.copy(
                    showSettings = true,
                    settingsDraft = SettingsDraft(
                        showTakeoutGrid = settings.showTakeoutGrid,
                        showLiveGrid = settings.showLiveGrid,
                        gridZoom = settings.gridZoom,
                    ),
                )
            }
        } else {
            _uiState.update { it.copy(showSettings = false, settingsDraft = null) }
        }
    }

    fun updateSettingsDraft(
        showTakeoutGrid: Boolean? = null,
        showLiveGrid: Boolean? = null,
        gridZoom: Int? = null,
    ) {
        val draft = _uiState.value.settingsDraft ?: return
        _uiState.update {
            it.copy(
                settingsDraft = draft.copy(
                    showTakeoutGrid = showTakeoutGrid ?: draft.showTakeoutGrid,
                    showLiveGrid = showLiveGrid ?: draft.showLiveGrid,
                    gridZoom = gridZoom?.coerceIn(TileMath.MIN_GRID_ZOOM, TileMath.MAX_GRID_ZOOM)
                        ?: draft.gridZoom,
                ),
            )
        }
    }

    fun saveSettings() {
        val draft = _uiState.value.settingsDraft ?: return
        val cityId = _uiState.value.selectedCity?.id
        val previousZoom = _uiState.value.mapSettings.gridZoom
        val zoomChanged = draft.gridZoom != previousZoom
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true, errorMessage = null) }
            val newSettings = MapSettings(
                showTakeoutGrid = draft.showTakeoutGrid,
                showLiveGrid = draft.showLiveGrid,
                gridZoom = draft.gridZoom,
            )
            runCatching {
                repository.setMapSettings(newSettings)
                if (cityId != null && zoomChanged) {
                    _uiState.update { it.copy(isImporting = true, importProgress = null) }
                    repository.rebuildTilesForCity(cityId, draft.gridZoom) { progress ->
                        _uiState.update { state -> state.copy(importProgress = progress) }
                    }
                }
            }.onSuccess {
                if (cityId != null) {
                    loadCitiesState(cityId, autoImportIfNeeded = false)
                }
                _uiState.update {
                    it.copy(
                        mapSettings = newSettings,
                        showSettings = false,
                        settingsDraft = null,
                        isSavingSettings = false,
                        isImporting = false,
                        importProgress = null,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isSavingSettings = false,
                        isImporting = false,
                        errorMessage = e.message ?: "Failed to save settings",
                    )
                }
            }
        }
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
            repository.setTakeoutDbUri(uri.toString())
            runImport(cityId, uri)
        }
    }

    fun clearTakeoutDb() {
        viewModelScope.launch {
            repository.setTakeoutDbUri(null)
        }
    }

    private suspend fun maybeAutoImportFromSavedTakeoutDb(cityId: Long) {
        val uriString = repository.getTakeoutDbUri() ?: return
        runImport(cityId, Uri.parse(uriString))
    }

    private suspend fun runImport(cityId: Long, uri: Uri) {
        _uiState.update { it.copy(isImporting = true, importProgress = null, errorMessage = null) }
        runCatching { repository.importTakeoutDb(cityId, uri) { progress ->
            _uiState.update { state -> state.copy(importProgress = progress) }
        } }
            .onSuccess { count ->
                loadCitiesState(cityId, autoImportIfNeeded = false)
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

class AppViewModelFactory(
    private val repository: AppRepository,
    private val appContext: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
