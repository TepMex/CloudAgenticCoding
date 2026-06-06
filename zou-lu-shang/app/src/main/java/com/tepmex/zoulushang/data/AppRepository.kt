package com.tepmex.zoulushang.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tepmex.zoulushang.geo.GeoJsonParser
import com.tepmex.zoulushang.importing.ImportProcessor
import com.tepmex.zoulushang.importing.ImportProgress
import com.tepmex.zoulushang.importing.TakeoutDbReader
import com.tepmex.zoulushang.importing.TakeoutJsonReader
import com.tepmex.zoulushang.nominatim.NominatimApi
import com.tepmex.zoulushang.nominatim.NominatimSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    suspend fun setSelectedCityId(cityId: Long?) {
        context.dataStore.edit { prefs ->
            if (cityId == null) prefs.remove(KEY_SELECTED_CITY_ID) else prefs[KEY_SELECTED_CITY_ID] = cityId
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

    suspend fun getVisitedTileLookup(cityId: Long): HashMap<Long, Boolean> = withContext(Dispatchers.IO) {
        database.visitedTileDao().getTileKeysForCity(cityId)
            .associateWith { true }
            .let { HashMap(it) }
    }

    suspend fun getVisitedTileCount(cityId: Long): Int =
        database.visitedTileDao().countForCity(cityId)

    suspend fun importTakeoutDb(
        cityId: Long,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val city = database.cityBoundaryDao().getById(cityId) ?: error("City not found")
        onProgress(ImportProgress(ImportProgress.Stage.READING))
        val reader = TakeoutDbReader(context)
        val rawPoints = reader.readPoints(uri) { count ->
            onProgress(ImportProgress(ImportProgress.Stage.READING, count, count))
        }
        saveImportedTiles(city, rawPoints, onProgress)
    }

    suspend fun importTakeoutJson(
        cityId: Long,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val city = database.cityBoundaryDao().getById(cityId) ?: error("City not found")
        onProgress(ImportProgress(ImportProgress.Stage.READING))
        val reader = TakeoutJsonReader(context)
        val rawPoints = reader.readPoints(uri) { count ->
            onProgress(ImportProgress(ImportProgress.Stage.READING, count, count))
        }
        saveImportedTiles(city, rawPoints, onProgress)
    }

    private suspend fun saveImportedTiles(
        city: CityBoundary,
        rawPoints: List<com.tepmex.zoulushang.importing.LocationPoint>,
        onProgress: (ImportProgress) -> Unit,
    ): Int {
        onProgress(ImportProgress(ImportProgress.Stage.SAVING, 0, 1))
        database.visitedTileDao().deleteForCity(city.id)
        val tiles = ImportProcessor.process(rawPoints, city.geoJson, city.id, onProgress)
        database.visitedTileDao().insertAll(tiles)
        return tiles.size
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
        private val KEY_SELECTED_CITY_ID = longPreferencesKey("selected_city_id")
    }
}
