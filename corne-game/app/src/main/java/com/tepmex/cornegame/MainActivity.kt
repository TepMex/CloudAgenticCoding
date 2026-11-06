package com.tepmex.cornegame

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.stepHardware
import com.tepmex.cornegame.ui.CorneApp
import com.tepmex.cornegame.ui.TrainViewModel
import com.tepmex.cornegame.ui.readHardwareTrace
import com.tepmex.cornegame.ui.readKeyboardLayout
import com.tepmex.cornegame.ui.theme.CorneTheme
import com.tepmex.cornegame.ui.trainViewModelFactory

/**
 * Одна activity. Поворот её пересоздаёт, но [TrainViewModel] остаётся в store
 * и сессия не обнуляется. configChanges специально не перехватываем.
 *
 * Символ читаем на ACTION_DOWN и отдаём на ACTION_UP. У части Bluetooth-клавиатур
 * unicode на UP уже пустой — тогда берём то, что запомнили на DOWN.
 * Буква вне виртуальной карты приходит одним ACTION_MULTIPLE: KEYCODE_UNKNOWN
 * и getCharacters() (в логе это chars="э"). Её отдаём сразу, отпускания нет.
 * Зажатые Shift и Alt и раскладка клавиатуры обновляют легенды сразу.
 */
class MainActivity : ComponentActivity() {
    private val trainViewModel: TrainViewModel by viewModels { trainViewModelFactory(this) }

    private var pendingHardware: Map<Int, TrainerAction> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        trainViewModel.noteBoot(info.versionName ?: "?", versionCode)
        setContent {
            CorneTheme {
                CorneApp(trainViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        publishKeyboard(deviceId = -1, clearWhenMissing = true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val before = trainViewModel.debugBaseline()
        val unicodeChar = event.unicodeChar
        val trace = readHardwareTrace(
            event = event,
            unicodeChar = unicodeChar,
            unicodeBare = event.getUnicodeChar(0),
        )
        val step = stepHardware(
            phase = event.action,
            keyCode = event.keyCode,
            unicodeChar = unicodeChar,
            shiftDown = event.isShiftPressed,
            altDown = event.isAltPressed,
            repeatCount = event.repeatCount,
            pending = pendingHardware,
            characters = trace.characters,
        )
        pendingHardware = step.pending
        trainViewModel.onHardwareModifiers(step.shiftDown, step.altDown)
        publishKeyboard(event.deviceId, clearWhenMissing = false)
        trainViewModel.onHardwareKey(before, trace, step)
        return if (step.consumed) true else super.dispatchKeyEvent(event)
    }

    private fun publishKeyboard(deviceId: Int, clearWhenMissing: Boolean) {
        val layout = readKeyboardLayout(deviceId)
        if (layout == null) {
            trainViewModel.noteKeyboard(
                deviceId = deviceId,
                found = false,
                language = null,
                table = LegendTable(),
                cleared = clearWhenMissing,
            )
            if (clearWhenMissing) {
                trainViewModel.onInputLanguage(null)
                trainViewModel.onLegendTable(LegendTable())
            }
            return
        }
        trainViewModel.noteKeyboard(
            deviceId = deviceId,
            found = true,
            language = layout.language,
            table = layout.legends,
            cleared = false,
        )
        trainViewModel.onInputLanguage(layout.language)
        trainViewModel.onLegendTable(layout.legends)
    }
}
