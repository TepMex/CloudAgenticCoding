package dev.eareye.t.collect

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dev.eareye.t.data.EventRepository
import dev.eareye.t.domain.ObservationPolicy
import dev.eareye.t.domain.ScreenEventType
import dev.eareye.t.domain.withStableOrdinals
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncOutcome {
    Ok,
    NoPermission,
    Retry,
}

class ScreenUsageCollector(
    private val context: Context,
    private val repository: EventRepository,
    private val device: DeviceStatus,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome = mutex.withLock { syncLocked() }

    private suspend fun syncLocked(): SyncOutcome {
        val now = time.wallTimeMillis()
        if (!device.usageAccessGranted()) {
            repository.addDiagnostic(
                "usage_permission_missing",
                "Нет доступа к статистике использования. События экрана не читаются.",
                now,
            )
            repository.markSyncFailure("нет доступа к статистике использования")
            return SyncOutcome.NoPermission
        }
        val state = repository.syncState()
        val begin = if (state?.trustedUntilMillis == null) {
            (now - ObservationPolicy.USAGE_INITIAL_LOOKBACK_MILLIS).coerceAtLeast(0L)
        } else {
            (state.trustedUntilMillis - ObservationPolicy.USAGE_SYNC_OVERLAP_MILLIS).coerceAtLeast(0L)
        }
        if (begin >= now) return SyncOutcome.Ok
        val hadError = !state?.lastError.isNullOrBlank()
        return try {
            val queried = query(begin, now) ?: run {
                repository.addDiagnostic(
                    "screen_sync_failed",
                    "События экрана сейчас недоступны. Повтор не стирает уже сохранённое.",
                    now,
                    bump = false,
                )
                return SyncOutcome.Retry
            }
            val events = withStableOrdinals(queried)
            repository.replaceScreenEvents(
                fromMillis = begin,
                toMillis = now,
                events = events,
                trustedUntilMillis = now,
                successWallMillis = now,
            )
            if (hadError) {
                repository.addDiagnostic(
                    "screen_sync_recovered",
                    "Синхронизация экрана снова выполнена. Событий в окне: ${events.size}.",
                    now,
                    bump = false,
                )
            }
            Log.i(TAG, "screen sync stored ${events.size} events")
            SyncOutcome.Ok
        } catch (security: SecurityException) {
            Log.w(TAG, "usage access rejected")
            repository.addDiagnostic(
                "usage_permission_missing",
                "Система отклонила чтение статистики использования.",
                now,
            )
            repository.markSyncFailure("доступ отклонён")
            SyncOutcome.NoPermission
        } catch (error: Throwable) {
            Log.w(TAG, "screen sync failed: ${error.javaClass.simpleName}")
            repository.addDiagnostic(
                "screen_sync_failed",
                "Синхронизация экрана не выполнена (${error.javaClass.simpleName}). Сохранённые события не заменены.",
                now,
            )
            repository.markSyncFailure(error.javaClass.simpleName)
            SyncOutcome.Retry
        }
    }

    private fun query(begin: Long, end: Long): List<Pair<Long, ScreenEventType>>? {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val cursor = if (Build.VERSION.SDK_INT >= 35) {
            queryModern(manager, begin, end)
        } else {
            manager.queryEvents(begin, end)
        } ?: return null
        val out = mutableListOf<Pair<Long, ScreenEventType>>()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(event)
            val timestamp = event.timeStamp
            if (timestamp < begin || timestamp >= end) continue
            val type = mapUsageEvent(event.eventType) ?: continue
            out += timestamp to type
        }
        return out
    }

    @RequiresApi(35)
    private fun queryModern(manager: UsageStatsManager, begin: Long, end: Long): UsageEvents? {
        val query = UsageEventsQuery.Builder(begin, end)
            .setEventTypes(
                UsageEvents.Event.SCREEN_INTERACTIVE,
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.KEYGUARD_HIDDEN,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                UsageEvents.Event.DEVICE_STARTUP,
            )
            .build()
        return manager.queryEvents(query)
    }

    private companion object {
        const val TAG = "EarEyeScreen"
    }
}

fun mapUsageEvent(eventType: Int): ScreenEventType? = when (eventType) {
    UsageEvents.Event.SCREEN_INTERACTIVE -> ScreenEventType.SCREEN_INTERACTIVE
    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ScreenEventType.SCREEN_NON_INTERACTIVE
    UsageEvents.Event.KEYGUARD_SHOWN -> ScreenEventType.KEYGUARD_SHOWN
    UsageEvents.Event.KEYGUARD_HIDDEN -> ScreenEventType.KEYGUARD_HIDDEN
    UsageEvents.Event.DEVICE_SHUTDOWN -> ScreenEventType.DEVICE_SHUTDOWN
    UsageEvents.Event.DEVICE_STARTUP -> ScreenEventType.DEVICE_STARTUP
    else -> null
}
