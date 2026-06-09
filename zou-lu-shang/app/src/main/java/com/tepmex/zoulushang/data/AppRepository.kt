package com.tepmex.zoulushang.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tepmex.zoulushang.geo.GeoJsonParser
import com.tepmex.zoulushang.importing.ImportProcessor
import com.tepmex.zoulushang.importing.ImportProgress
import com.tepmex.zoulushang.importing.TakeoutDbReader
import com.tepmex.zoulushang.importing.TakeoutJsonReader
import com.tepmex.zoulushang.nominatim.NominatimApi
import com.tepmex.zoulushang.nominatim.NominatimSearchResult
import com.tepmex.zoulushang.geo.LatLng
import com.tepmex.zoulushang.geo.PointInPolygon
import com.tepmex.zoulushang.geo.TileLookupRemapper
import com.tepmex.zoulushang.geo.TileMath
import com.tepmex.zoulushang.importing.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("zou_lu_shang_prefs")

class AppRepository(
    private val context: Context,
    private val database: ZouLuShangDatabase,
) {
    private val nominatim = NominatimApi(userAgent = context.packageName)
    private val json = Json { ignoreUnknownKeys = true }

    val selectedCityId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_CITY_ID]
    }

    val takeoutDbUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TAKEOUT_DB_URI]
    }

    val mapSettings: Flow<MapSettings> = context.dataStore.data.map { prefs ->
        MapSettings(
            showTakeoutGrid = prefs[KEY_SHOW_TAKEOUT_GRID] ?: true,
            showLiveGrid = prefs[KEY_SHOW_LIVE_GRID] ?: true,
            gridZoom = prefs[KEY_GRID_ZOOM] ?: TileMath.DEFAULT_GRID_ZOOM,
        )
    }

    suspend fun setSelectedCityId(cityId: Long?) {
        context.dataStore.edit { prefs ->
            if (cityId == null) prefs.remove(KEY_SELECTED_CITY_ID) else prefs[KEY_SELECTED_CITY_ID] = cityId
        }
    }

    suspend fun setTakeoutDbUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_TAKEOUT_DB_URI) else prefs[KEY_TAKEOUT_DB_URI] = uri
        }
    }

    suspend fun getTakeoutDbUri(): String? = context.dataStore.data.map { prefs ->
        prefs[KEY_TAKEOUT_DB_URI]
    }.first()

    suspend fun getMapSettings(): MapSettings = mapSettings.first()

    suspend fun setMapSettings(settings: MapSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_TAKEOUT_GRID] = settings.showTakeoutGrid
            prefs[KEY_SHOW_LIVE_GRID] = settings.showLiveGrid
            prefs[KEY_GRID_ZOOM] = settings.gridZoom
        }
    }

    suspend fun getCities(): List<CityBoundary> = database.cityBoundaryDao().getAll()

    suspend fun getCity(id: Long): CityBoundary? = database.cityBoundaryDao().getById(id)

    suspend fun searchCities(query: String): List<NominatimSearchResult> =
        nominatim.searchCities(query)

    suspend fun saveCityFromSearch(result: NominatimSearchResult): Long = withContext(Dispatchers.IO) {
        val geoJson = resolveCityGeoJson(result)
        val parsed = GeoJsonParser.parsePolygon(geoJson)
        val bbox = parsed.boundingBox
        val existing = database.cityBoundaryDao().getByOsmPlaceId(result.placeId)
        val city = CityBoundary(
            id = existing?.id ?: 0,
            osmPlaceId = result.placeId,
            displayName = result.displayName,
            geoJson = geoJson,
            minLat = bbox.latSouth,
            maxLat = bbox.latNorth,
            minLng = bbox.lonWest,
            maxLng = bbox.lonEast,
        )
        database.cityBoundaryDao().insert(city)
        database.cityBoundaryDao().getByOsmPlaceId(result.placeId)!!.id
    }

    suspend fun getVisitedTileLookup(cityId: Long, gridZoom: Int = getMapSettings().gridZoom): HashMap<Long, Int> =
        withContext(Dispatchers.IO) {
            remapStoredLookup(database.visitedTileDao().getTilesForCity(cityId), gridZoom)
        }

    suspend fun getVisitedTileCount(cityId: Long, gridZoom: Int = getMapSettings().gridZoom): Int =
        getVisitedTileLookup(cityId, gridZoom).size

    fun observeLiveTileLookup(cityId: Long): Flow<HashMap<Long, Int>> =
        combine(
            database.liveTileDao().observeTilesForCity(cityId),
            mapSettings,
        ) { tiles, settings ->
            remapStoredLookup(tiles, settings.gridZoom)
        }

    suspend fun getLiveTileLookup(cityId: Long, gridZoom: Int = getMapSettings().gridZoom): HashMap<Long, Int> =
        withContext(Dispatchers.IO) {
            remapStoredLookup(database.liveTileDao().getTilesForCity(cityId), gridZoom)
        }

    suspend fun getLiveTileCount(cityId: Long, gridZoom: Int = getMapSettings().gridZoom): Int =
        getLiveTileLookup(cityId, gridZoom).size

    suspend fun recordLiveLocation(cityId: Long, latitude: Double, longitude: Double, accuracyMeters: Float?) {
        if (accuracyMeters != null && accuracyMeters > MAX_LIVE_ACCURACY_METERS) return
        val city = database.cityBoundaryDao().getById(cityId) ?: return
        val polygon = GeoJsonParser.parsePolygon(city.geoJson)
        if (!PointInPolygon.containsInAnyRing(LatLng(latitude, longitude), polygon.rings)) return
        val gridZoom = getMapSettings().gridZoom
        val (x, y) = TileMath.latLngToTile(latitude, longitude, gridZoom)
        val tileKey = TileMath.packTileKey(gridZoom, x, y)
        withContext(Dispatchers.IO) {
            database.liveLocationSampleDao().insert(
                LiveLocationSample(cityId = cityId, latitude = latitude, longitude = longitude),
            )
            database.liveTileDao().recordVisit(cityId, tileKey)
        }
    }

    suspend fun importTakeoutDb(
        cityId: Long,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit,
    ): Int = importTakeoutDb(cityId, uri, onProgress, getMapSettings().gridZoom)

    suspend fun importTakeoutDb(
        cityId: Long,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit,
        gridZoom: Int,
    ): Int = withContext(Dispatchers.IO) {
        val city = database.cityBoundaryDao().getById(cityId) ?: error("City not found")
        onProgress(ImportProgress(ImportProgress.Stage.READING))
        val reader = TakeoutDbReader(context)
        val rawPoints = reader.readPoints(uri) { count ->
            onProgress(ImportProgress(ImportProgress.Stage.READING, count, count))
        }
        saveImportedTiles(city, rawPoints, onProgress, gridZoom)
    }

    suspend fun importTakeoutJson(
        cityId: Long,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val city = database.cityBoundaryDao().getById(cityId) ?: error("City not found")
        val gridZoom = getMapSettings().gridZoom
        onProgress(ImportProgress(ImportProgress.Stage.READING))
        val reader = TakeoutJsonReader(context)
        val rawPoints = reader.readPoints(uri) { count ->
            onProgress(ImportProgress(ImportProgress.Stage.READING, count, count))
        }
        saveImportedTiles(city, rawPoints, onProgress, gridZoom)
    }

    suspend fun rebuildTilesForCity(
        cityId: Long,
        gridZoom: Int,
        onProgress: (ImportProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onProgress(ImportProgress(ImportProgress.Stage.MAPPING, 0, 2))
        rebuildVisitedTilesAtZoom(cityId, gridZoom, onProgress)
        onProgress(ImportProgress(ImportProgress.Stage.MAPPING, 1, 2))
        rebuildLiveTilesAtZoom(cityId, gridZoom)
        onProgress(ImportProgress(ImportProgress.Stage.DONE, 2, 2))
    }

    private suspend fun rebuildVisitedTilesAtZoom(
        cityId: Long,
        gridZoom: Int,
        onProgress: (ImportProgress) -> Unit,
    ) {
        val cachedPoints = database.importedLocationPointDao().getForCity(cityId)
        if (cachedPoints.isNotEmpty()) {
            val city = database.cityBoundaryDao().getById(cityId) ?: return
            saveImportedTiles(city, cachedPoints.toLocationPoints(), onProgress, gridZoom)
            return
        }
        val uriString = getTakeoutDbUri()
        if (uriString != null) {
            importTakeoutDb(cityId, Uri.parse(uriString), onProgress, gridZoom)
            return
        }
        val existing = database.visitedTileDao().getTilesForCity(cityId)
        if (existing.isEmpty()) return
        onProgress(ImportProgress(ImportProgress.Stage.MAPPING, 0, existing.size))
        persistRemappedVisitedTiles(cityId, existing, gridZoom)
    }

    private suspend fun rebuildLiveTilesAtZoom(cityId: Long, gridZoom: Int) {
        val samples = database.liveLocationSampleDao().getSamplesForCity(cityId)
        val existing = database.liveTileDao().getTilesForCity(cityId)
        database.liveTileDao().deleteForCity(cityId)
        val tileCounts = HashMap<Long, Int>()
        if (samples.isNotEmpty()) {
            for (sample in samples) {
                val (x, y) = TileMath.latLngToTile(sample.latitude, sample.longitude, gridZoom)
                val key = TileMath.packTileKey(gridZoom, x, y)
                tileCounts[key] = (tileCounts[key] ?: 0) + 1
            }
        } else if (existing.isNotEmpty()) {
            val remapped = remapStoredLookup(existing, gridZoom)
            remapped.forEach { (tileKey, pointCount) ->
                tileCounts[tileKey] = pointCount
            }
        } else {
            return
        }
        database.liveTileDao().insertAll(
            tileCounts.map { (tileKey, pointCount) ->
                LiveTile(cityId = cityId, tileKey = tileKey, pointCount = pointCount)
            },
        )
    }

    private suspend fun persistRemappedVisitedTiles(
        cityId: Long,
        tiles: List<VisitedTile>,
        gridZoom: Int,
    ) {
        val remapped = remapStoredLookup(tiles, gridZoom)
        database.visitedTileDao().deleteForCity(cityId)
        database.visitedTileDao().insertAll(
            remapped.map { (tileKey, pointCount) ->
                VisitedTile(cityId = cityId, tileKey = tileKey, pointCount = pointCount)
            },
        )
    }

    private suspend fun saveImportedTiles(
        city: CityBoundary,
        rawPoints: List<LocationPoint>,
        onProgress: (ImportProgress) -> Unit,
        gridZoom: Int,
    ): Int {
        onProgress(ImportProgress(ImportProgress.Stage.SAVING, 0, 1))
        cacheImportedPoints(city.id, rawPoints)
        database.visitedTileDao().deleteForCity(city.id)
        val tiles = ImportProcessor.process(rawPoints, city.geoJson, city.id, onProgress, gridZoom)
        database.visitedTileDao().insertAll(tiles)
        return tiles.size
    }

    private suspend fun cacheImportedPoints(cityId: Long, rawPoints: List<LocationPoint>) {
        database.importedLocationPointDao().deleteForCity(cityId)
        if (rawPoints.isEmpty()) return
        database.importedLocationPointDao().insertAll(
            rawPoints.map { point ->
                ImportedLocationPoint(
                    cityId = cityId,
                    ts = point.ts,
                    latitude = point.lat,
                    longitude = point.lng,
                    accuracyMeters = point.accuracyMeters,
                )
            },
        )
    }

    private fun remapStoredLookup(tiles: List<VisitedTile>, gridZoom: Int): HashMap<Long, Int> {
        val lookup = tiles.associate { it.tileKey to it.pointCount }.let { HashMap(it) }
        return TileLookupRemapper.remap(lookup, gridZoom)
    }

    private fun remapStoredLookup(tiles: List<LiveTile>, gridZoom: Int): HashMap<Long, Int> {
        val lookup = tiles.associate { it.tileKey to it.pointCount }.let { HashMap(it) }
        return TileLookupRemapper.remap(lookup, gridZoom)
    }

    private fun List<ImportedLocationPoint>.toLocationPoints(): List<LocationPoint> =
        map { point ->
            LocationPoint(
                ts = point.ts,
                lat = point.latitude,
                lng = point.longitude,
                accuracyMeters = point.accuracyMeters,
            )
        }

    private suspend fun resolveCityGeoJson(result: NominatimSearchResult): String {
        val candidates = buildList {
            result.geoJson?.let { add(it) }
            runCatching { nominatim.lookupBoundary(result).geoJson }.getOrNull()?.let { add(it) }
            runCatching { nominatim.detailsGeometry(result.placeId) }.getOrNull()?.let { add(it) }
        }
        for (element in candidates) {
            val geoJson = json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                element,
            )
            if (runCatching { GeoJsonParser.parsePolygon(geoJson) }.isSuccess) {
                return geoJson
            }
        }
        val bbox = result.boundingbox
        if (bbox != null && bbox.size >= 4) {
            return GeoJsonParser.rectangleFromBoundingBox(bbox)
        }
        error("No boundary polygon for ${result.displayName}")
    }

    companion object {
        private const val MAX_LIVE_ACCURACY_METERS = 50f
        private val KEY_SELECTED_CITY_ID = longPreferencesKey("selected_city_id")
        private val KEY_TAKEOUT_DB_URI = stringPreferencesKey("takeout_db_uri")
        private val KEY_SHOW_TAKEOUT_GRID = booleanPreferencesKey("show_takeout_grid")
        private val KEY_SHOW_LIVE_GRID = booleanPreferencesKey("show_live_grid")
        private val KEY_GRID_ZOOM = intPreferencesKey("grid_zoom")
    }
}
