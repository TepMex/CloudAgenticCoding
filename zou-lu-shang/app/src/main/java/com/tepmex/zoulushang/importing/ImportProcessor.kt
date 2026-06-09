package com.tepmex.zoulushang.importing

import com.tepmex.zoulushang.data.VisitedTile
import com.tepmex.zoulushang.geo.GeoJsonParser
import com.tepmex.zoulushang.geo.LatLng
import com.tepmex.zoulushang.geo.PointInPolygon
import com.tepmex.zoulushang.geo.TileMath

data class ImportProgress(
    val stage: Stage,
    val processed: Int = 0,
    val total: Int = 0,
    val tilesFound: Int = 0,
) {
    enum class Stage {
        READING,
        FILTERING,
        CLUSTERING,
        MAPPING,
        SAVING,
        DONE,
    }

    val fraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()
}

object ImportProcessor {
    private const val MAX_ACCURACY_METERS = 50f

    fun process(
        rawPoints: List<LocationPoint>,
        geoJson: String,
        cityId: Long,
        onProgress: (ImportProgress) -> Unit,
        gridZoom: Int = TileMath.DEFAULT_GRID_ZOOM,
    ): List<VisitedTile> {
        onProgress(ImportProgress(ImportProgress.Stage.FILTERING, 0, rawPoints.size))

        val filtered = rawPoints.filter { point ->
            point.accuracyMeters == null || point.accuracyMeters <= MAX_ACCURACY_METERS
        }
        onProgress(
            ImportProgress(
                ImportProgress.Stage.FILTERING,
                filtered.size,
                rawPoints.size,
            ),
        )

        onProgress(ImportProgress(ImportProgress.Stage.CLUSTERING, 0, filtered.size))
        val clustered = PointClusterer.cluster(filtered)
        onProgress(
            ImportProgress(
                ImportProgress.Stage.CLUSTERING,
                clustered.size,
                filtered.size,
            ),
        )

        val polygon = GeoJsonParser.parsePolygon(geoJson)
        val tileCounts = HashMap<Long, Int>()

        onProgress(ImportProgress(ImportProgress.Stage.MAPPING, 0, clustered.size))
        clustered.forEachIndexed { index, point ->
            val latLng = LatLng(point.lat, point.lng)
            if (!PointInPolygon.containsInAnyRing(latLng, polygon.rings)) return@forEachIndexed
            val (x, y) = TileMath.latLngToTile(point.lat, point.lng, gridZoom)
            val key = TileMath.packTileKey(gridZoom, x, y)
            tileCounts[key] = (tileCounts[key] ?: 0) + 1
            if (index % 200 == 0) {
                onProgress(
                    ImportProgress(
                        ImportProgress.Stage.MAPPING,
                        index,
                        clustered.size,
                        tilesFound = tileCounts.size,
                    ),
                )
            }
        }

        val tiles = tileCounts.map { (tileKey, pointCount) ->
            VisitedTile(cityId = cityId, tileKey = tileKey, pointCount = pointCount)
        }
        onProgress(
            ImportProgress(
                ImportProgress.Stage.DONE,
                clustered.size,
                clustered.size,
                tilesFound = tiles.size,
            ),
        )
        return tiles
    }
}
