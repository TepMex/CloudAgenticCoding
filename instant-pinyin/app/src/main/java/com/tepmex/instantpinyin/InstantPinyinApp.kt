package com.tepmex.instantpinyin

import android.app.Application
import com.tepmex.instantpinyin.ocr.LiteRtHanziOcr

class InstantPinyinApp : Application() {
    lateinit var ocr: LiteRtHanziOcr
        private set

    override fun onCreate() {
        super.onCreate()
        ocr = LiteRtHanziOcr(this)
    }
}
