package dev.eareye.t.collect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.eareye.t.EarEyeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        val app = context.applicationContext as EarEyeApp
        app.appScope.launch(Dispatchers.IO) {
            try {
                val message = when (action) {
                    Intent.ACTION_TIME_CHANGED ->
                        "Системные часы изменились. Уже записанные интервалы не переписываются задним числом."
                    Intent.ACTION_TIMEZONE_CHANGED ->
                        "Часовой пояс изменился. Дни пересчитываются по новым локальным границам."
                    else -> "Получено системное событие времени."
                }
                app.graph.repository.addDiagnostic(
                    kind = "time_change",
                    message = message,
                    wallMillis = app.graph.time.wallTimeMillis(),
                    bump = true,
                )
                MonitorBridge.resnapshot?.invoke()
            } finally {
                pending.finish()
            }
        }
    }
}
