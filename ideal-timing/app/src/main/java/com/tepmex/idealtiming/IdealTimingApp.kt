package com.tepmex.idealtiming

import android.app.Application
import com.tepmex.idealtiming.data.AuthTokenStore
import com.tepmex.idealtiming.data.IdealTimingRepository
import com.tepmex.idealtiming.data.WakeSnapshotStore

class IdealTimingApp : Application() {
    lateinit var repository: IdealTimingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = IdealTimingRepository(
            tokenStore = AuthTokenStore(this),
            wakeStore = WakeSnapshotStore(this),
        )
    }
}
