package com.tepmex.idealtiming

import android.app.Application
import com.tepmex.idealtiming.data.AuthTokenStore
import com.tepmex.idealtiming.data.DeviceLocationSource
import com.tepmex.idealtiming.data.GeoLocationStore
import com.tepmex.idealtiming.data.IdealTimingRepository
import com.tepmex.idealtiming.data.NfcCheckInStore
import com.tepmex.idealtiming.data.WakeSnapshotStore
import com.tepmex.idealtiming.notification.SectionNotificationScheduler

class IdealTimingApp : Application() {
    lateinit var repository: IdealTimingRepository
        private set

    lateinit var sectionNotifications: SectionNotificationScheduler
        private set

    lateinit var locationSource: DeviceLocationSource
        private set

    lateinit var nfcCheckInStore: NfcCheckInStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = IdealTimingRepository(
            tokenStore = AuthTokenStore(this),
            wakeStore = WakeSnapshotStore(this),
        )
        sectionNotifications = SectionNotificationScheduler(this)
        sectionNotifications.ensureChannel()
        val geoStore = GeoLocationStore(this)
        locationSource = DeviceLocationSource(this, geoStore)
        nfcCheckInStore = NfcCheckInStore(this)
    }
}
