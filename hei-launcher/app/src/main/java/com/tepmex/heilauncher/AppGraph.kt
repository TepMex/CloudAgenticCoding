package com.tepmex.heilauncher

import android.content.Context
import com.tepmex.heilauncher.data.BatteryReader
import com.tepmex.heilauncher.data.InstalledApps
import com.tepmex.heilauncher.data.PrefsStore
import com.tepmex.heilauncher.data.UsageTimes
import com.tepmex.heilauncher.data.WeatherRepository

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val prefs = PrefsStore(appContext)
    val apps = InstalledApps(appContext)
    val usage = UsageTimes(appContext)
    val battery = BatteryReader(appContext)
    val weather = WeatherRepository(appContext)
}
