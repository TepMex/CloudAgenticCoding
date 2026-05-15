package com.tepmex.ankidroidllm

import android.app.Application
import com.tepmex.ankidroidllm.data.AppPreferences

class AnkiLlmApp : Application() {
    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
    }
}
