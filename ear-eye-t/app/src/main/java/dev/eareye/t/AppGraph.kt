package dev.eareye.t

import android.content.Context
import dev.eareye.t.collect.AndroidTimeSource
import dev.eareye.t.collect.CategoryCache
import dev.eareye.t.collect.DeviceStatus
import dev.eareye.t.collect.MonitoringHealthTracker
import dev.eareye.t.collect.ScreenUsageCollector
import dev.eareye.t.collect.TimeSource
import dev.eareye.t.data.EventRepository
import dev.eareye.t.data.db.EarEyeDatabase

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val time: TimeSource = AndroidTimeSource
    val database: EarEyeDatabase = EarEyeDatabase.create(appContext)
    val repository: EventRepository = EventRepository(database)
    val device: DeviceStatus = DeviceStatus(appContext, time)
    val categories: CategoryCache = CategoryCache()
    val health: MonitoringHealthTracker = MonitoringHealthTracker(repository, device, categories)
    val collector: ScreenUsageCollector = ScreenUsageCollector(appContext, repository, device, time)
}
