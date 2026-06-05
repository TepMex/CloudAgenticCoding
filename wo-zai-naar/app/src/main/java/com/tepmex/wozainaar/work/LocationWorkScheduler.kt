package com.tepmex.wozainaar.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
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
  }
}
