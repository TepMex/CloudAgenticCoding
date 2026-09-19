package com.tepmex.paizhaounknownhanzi

import android.app.Application
import com.tepmex.paizhaounknownhanzi.data.KnownHanziStore
import com.tepmex.paizhaounknownhanzi.ocr.LiteRtHanziOcr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaizhaoApp : Application() {
    lateinit var knownHanziStore: KnownHanziStore
        private set
    lateinit var ocr: LiteRtHanziOcr
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        knownHanziStore = KnownHanziStore(this)
        ocr = LiteRtHanziOcr(this)
        appScope.launch {
            runCatching { ocr.warmup() }
        }
    }
}
