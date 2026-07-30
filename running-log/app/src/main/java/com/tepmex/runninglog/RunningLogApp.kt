package com.tepmex.runninglog

import android.app.Application
import com.tepmex.runninglog.data.AuthTokenStore
import com.tepmex.runninglog.data.RunningLogDatabase
import com.tepmex.runninglog.data.RunningRepository

class RunningLogApp : Application() {
    lateinit var repository: RunningRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = RunningLogDatabase.create(this)
        repository = RunningRepository(
            dao = db.runningActivityDao(),
            tokenStore = AuthTokenStore(this),
        )
    }
}
