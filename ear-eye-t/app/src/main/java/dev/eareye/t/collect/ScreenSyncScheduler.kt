package dev.eareye.t.collect

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ScreenSyncScheduler {
    private const val PERIODIC = "ear-eye-screen-sync"
    private const val IMMEDIATE = "ear-eye-screen-sync-now"
    private var lastImmediateEnqueueMillis: Long = 0L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScreenSyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueSoon(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastImmediateEnqueueMillis < 60_000L) return
        lastImmediateEnqueueMillis = now
        val request = OneTimeWorkRequestBuilder<ScreenSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
