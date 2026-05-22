package com.tepmex.ankidashboard

import android.app.Application
import com.tepmex.ankidashboard.data.AppPreferences

class AnkiDashboardApp : Application() {
    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
    }
}
