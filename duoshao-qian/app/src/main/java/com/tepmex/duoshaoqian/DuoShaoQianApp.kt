package com.tepmex.duoshaoqian

import android.app.Application
import com.tepmex.duoshaoqian.data.AnswerSfxPlayer
import com.tepmex.duoshaoqian.data.AudioCatalog
import com.tepmex.duoshaoqian.data.AudioCatalogParser
import com.tepmex.duoshaoqian.data.PriceAudioPlayer

class DuoShaoQianApp : Application() {
    lateinit var catalog: AudioCatalog
        private set

    lateinit var audioPlayer: PriceAudioPlayer
        private set

    lateinit var answerSfx: AnswerSfxPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        val json = assets.open(CATALOG_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        catalog = AudioCatalogParser.parse(json)
        audioPlayer = PriceAudioPlayer(this)
        answerSfx = AnswerSfxPlayer(this)
    }

    companion object {
        const val CATALOG_ASSET = "chinese_money_numbers.json"
    }
}
