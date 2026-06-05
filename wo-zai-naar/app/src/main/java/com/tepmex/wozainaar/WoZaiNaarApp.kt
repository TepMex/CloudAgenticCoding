package com.tepmex.wozainaar

import android.app.Application
import com.tepmex.wozainaar.data.LocationDatabase
import com.tepmex.wozainaar.data.LocationRepository
import com.tepmex.wozainaar.notification.LocationNotifications
import com.tepmex.wozainaar.work.LocationWorkScheduler
import org.osmdroid.config.Configuration

class WoZaiNaarApp : Application() {
    val repository: LocationRepository by lazy {
        LocationRepository(LocationDatabase.create(this).locationPointDao())
    }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        LocationNotifications.ensureChannel(this)
        LocationWorkScheduler.schedule(this)
    }
}
