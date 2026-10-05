package com.tepmex.heilauncher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.tepmex.heilauncher.domain.LaunchableApp
import com.tepmex.heilauncher.domain.sortApps
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Resolves launcher activities to labels only.
 * Never calls [android.content.pm.PackageManager.getApplicationIcon] or ResolveInfo.loadIcon —
 * decoding icons is what makes a launcher feel slow.
 */
class InstalledApps(private val context: Context) {
    private val lock = Any()
    private var cache: List<LaunchableApp>? = null
    private var generation = 0
    private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = changesFlow.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            invalidate()
        }
    }

    fun registerPackageWatcher() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun invalidate() {
        synchronized(lock) {
            generation += 1
            cache = null
        }
        changesFlow.tryEmit(Unit)
    }

    fun load(): List<LaunchableApp> {
        val snapshot = synchronized(lock) {
            cache?.let { return it }
            generation
        }
        val loaded = query()
        return synchronized(lock) {
            if (generation == snapshot) {
                cache = loaded
            }
            cache ?: loaded
        }
    }

    private fun query(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        val self = context.packageName
        val apps = ArrayList<LaunchableApp>(resolved.size)
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            if (!activity.enabled) continue
            if (activity.packageName == self) continue
            val appInfo = activity.applicationInfo
            if (appInfo != null && !appInfo.enabled) continue
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { activity.packageName }
            val component = ComponentName(activity.packageName, activity.name).flattenToString()
            apps.add(
                LaunchableApp(
                    component = component,
                    packageName = activity.packageName,
                    label = label,
                ),
            )
        }
        return sortApps(apps)
    }
}
