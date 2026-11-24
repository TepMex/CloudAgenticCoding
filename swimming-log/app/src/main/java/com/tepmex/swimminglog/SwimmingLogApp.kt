package com.tepmex.swimminglog

import android.app.Application
import com.tepmex.swimminglog.data.AuthTokenStore
import com.tepmex.swimminglog.data.SwimmingLogDatabase
import com.tepmex.swimminglog.data.SwimmingRepository

class SwimmingLogApp : Application() {
    lateinit var repository: SwimmingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = SwimmingLogDatabase.create(this)
        repository = SwimmingRepository(
            dao = db.swimmingActivityDao(),
            tokenStore = AuthTokenStore(this),
        )
    }
}
