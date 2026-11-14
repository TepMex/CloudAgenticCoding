package dev.eareye.t.collect

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.eareye.t.EarEyeApp

class ScreenSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? EarEyeApp ?: return Result.retry()
        return try {
            when (app.graph.collector.sync()) {
                SyncOutcome.Ok, SyncOutcome.NoPermission -> Result.success()
                SyncOutcome.Retry -> Result.retry()
            }
        } catch (error: Throwable) {
            Log.w(TAG, "screen sync worker failed: ${error.javaClass.simpleName}")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "EarEyeScreen"
    }
}
