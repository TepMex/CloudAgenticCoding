package com.tepmex.wodeluyou

import android.app.Application
import com.tepmex.wodeluyou.data.DictionaryRepository

class WoDeLuyouApp : Application() {
    lateinit var repository: DictionaryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DictionaryRepository(assets)
    }
}
