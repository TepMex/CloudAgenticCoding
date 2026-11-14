package dev.eareye.t

import android.app.Application
import dev.eareye.t.collect.ScreenSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class EarEyeApp : Application() {
    lateinit var graph: AppGraph
        private set

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        runBlocking(Dispatchers.IO) {
            graph.repository.ensureSingletons()
            graph.categories.selection = graph.repository.audioSettings()
            graph.health.closeDanglingUnexpected()
        }
        ScreenSyncScheduler.schedule(this)
        appScope.launch(Dispatchers.IO) {
            graph.collector.sync()
        }
    }
}
