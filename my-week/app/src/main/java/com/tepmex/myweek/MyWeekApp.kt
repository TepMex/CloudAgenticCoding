package com.tepmex.myweek

import android.app.Application
import android.content.pm.PackageManager
import com.tepmex.myweek.data.WeekDatabase
import com.tepmex.myweek.data.WeekRepository
import com.tepmex.myweek.data.WeekSettings

class MyWeekApp : Application() {
    lateinit var repository: WeekRepository
        private set

    lateinit var settings: WeekSettings
        private set

    val versionName: String by lazy {
        packageManager
            .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName
            .orEmpty()
    }

    override fun onCreate() {
        super.onCreate()
        settings = WeekSettings(this)
        val database = WeekDatabase.create(this)
        repository = WeekRepository(database, database.weekDao())
    }
}
