package com.tepmex.wodeluyou.data

import android.content.res.AssetManager
import java.nio.charset.StandardCharsets

class DictionaryRepository(private val assets: AssetManager) {
    val catalog: DictionaryCatalog by lazy {
        assets.open(ASSET_NAME).bufferedReader(StandardCharsets.UTF_8).use { reader ->
            DictionaryParser.parse(reader.readText())
        }
    }

    companion object {
        const val ASSET_NAME = "dictionary.tsv"
    }
}
