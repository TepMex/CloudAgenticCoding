package com.tepmex.hanziinfogf14.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CatalogJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): HanziCatalog {
        val characters = json.parseToJsonElement(text).jsonObject.getValue("characters").jsonObject
        val entries = ArrayList<Pair<String, List<String>>>(characters.size)
        for ((hanzi, element) in characters) {
            val components = element.jsonObject["components"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: emptyList()
            entries.add(hanzi to components)
        }
        return NeighborIndex.build(entries)
    }
}
