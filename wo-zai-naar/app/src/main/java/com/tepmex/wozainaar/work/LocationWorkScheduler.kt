package com.tepmex.wozainaar.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object LocationWorkScheduler {
    private val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LocationWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .addTag(LocationWorker.UNIQUE_PERIODIC_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LocationWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        TrackingLogger.log(
            "Scheduled periodic location work (every ${INTERVAL_MINUTES} min, tag=${LocationWorker.UNIQUE_PERIODIC_WORK})",
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<LocationWorker>()
            .addTag(LocationWorker.UNIQUE_ONE_TIME_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            LocationWorker.UNIQUE_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        TrackingLogger.log("Enqueued one-time location capture (tag=${LocationWorker.UNIQUE_ONE_TIME_WORK})")
    }
}
