package com.tepmex.heilauncher

import android.content.Context
import com.tepmex.heilauncher.data.BatteryReader
import com.tepmex.heilauncher.data.InstalledApps
import com.tepmex.heilauncher.data.PrefsStore
import com.tepmex.heilauncher.data.WeatherRepository
import com.tepmex.heilauncher.timing.data.AuthTokenStore
import com.tepmex.heilauncher.timing.data.DeviceLocationSource
import com.tepmex.heilauncher.timing.data.GeoLocationStore
import com.tepmex.heilauncher.timing.data.IdealTimingRepository
import com.tepmex.heilauncher.timing.data.NfcCheckInStore
import com.tepmex.heilauncher.timing.data.WakeSnapshotStore
import com.tepmex.heilauncher.timing.notification.SectionNotificationScheduler

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val prefs = PrefsStore(appContext)
    val apps = InstalledApps(appContext)
    val battery = BatteryReader(appContext)
    val weather = WeatherRepository(appContext)
    val timingRepository = IdealTimingRepository(
        tokenStore = AuthTokenStore(appContext),
        wakeStore = WakeSnapshotStore(appContext),
    )
    val sectionNotifications = SectionNotificationScheduler(appContext).also { it.ensureChannel() }
    val locationSource = DeviceLocationSource(appContext, GeoLocationStore(appContext))
    val nfcCheckInStore = NfcCheckInStore(appContext)
}
