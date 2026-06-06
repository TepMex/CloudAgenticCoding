package com.tepmex.zoulushang.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonParserTest {
    @Test
    fun parsePolygon_exteriorRingOnly() {
        val geoJson = """
            {"type":"Polygon","coordinates":[
              [[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0]],
              [[0.2,0.2],[0.3,0.2],[0.3,0.3],[0.2,0.3],[0.2,0.2]]
            ]}
        """.trimIndent()
        val parsed = GeoJsonParser.parsePolygon(geoJson)
        assertEquals(1, parsed.rings.size)
        assertEquals(5, parsed.rings.first().size)
    }

    @Test
    fun parseMultiPolygon_allParts() {
        val geoJson = """
            {"type":"MultiPolygon","coordinates":[
              [[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]],
              [[[2.0,2.0],[3.0,2.0],[3.0,3.0],[2.0,2.0]]]
            ]}
        """.trimIndent()
        val parsed = GeoJsonParser.parsePolygon(geoJson)
        assertEquals(2, parsed.rings.size)
    }

    @Test
    fun parseGeometryCollection_nestedPolygon() {
        val geoJson = """
            {"type":"GeometryCollection","geometries":[
              {"type":"Polygon","coordinates":[[[10.0,10.0],[11.0,10.0],[11.0,11.0],[10.0,10.0]]]}
            ]}
        """.trimIndent()
        val parsed = GeoJsonParser.parsePolygon(geoJson)
        assertEquals(1, parsed.rings.size)
        assertTrue(parsed.boundingBox.latNorth > parsed.boundingBox.latSouth)
    }

    @Test
    fun rectangleFromBoundingBox_buildsValidPolygon() {
        val geoJson = GeoJsonParser.rectangleFromBoundingBox(
            listOf("54.8", "55.4", "73.0", "73.6"),
        )
        val parsed = GeoJsonParser.parsePolygon(geoJson)
        assertEquals(1, parsed.rings.size)
        assertEquals(5, parsed.rings.first().size)
    }
}
