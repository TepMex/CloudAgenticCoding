package com.tepmex.cornegame

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
import com.tepmex.cornegame.ui.readKeyboardLayout
import com.tepmex.cornegame.ui.theme.CorneTheme
import com.tepmex.cornegame.ui.trainViewModelFactory

/**
 * Одна activity. Поворот её пересоздаёт, но [TrainViewModel] остаётся в store
 * и сессия не обнуляется. configChanges специально не перехватываем.
 *
 * Символ читаем на ACTION_DOWN и отдаём на ACTION_UP. У части Bluetooth-клавиатур
 * unicode на UP уже пустой — тогда берём то, что запомнили на DOWN.
 * Зажатые Shift и Alt и раскладка клавиатуры обновляют легенды сразу.
 */
class MainActivity : ComponentActivity() {
    private val trainViewModel: TrainViewModel by viewModels { trainViewModelFactory(this) }

    private var pendingHardware: Map<Int, TrainerAction> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        val step = stepHardware(
            phase = event.action,
            keyCode = event.keyCode,
            unicodeChar = event.unicodeChar,
            shiftDown = event.isShiftPressed,
            altDown = event.isAltPressed,
            repeatCount = event.repeatCount,
            pending = pendingHardware,
        )
        pendingHardware = step.pending
        trainViewModel.onHardwareModifiers(step.shiftDown, step.altDown)
        publishKeyboard(event.deviceId, clearWhenMissing = false)
        step.deliver?.let(trainViewModel::onTrainerAction)
        return if (step.consumed) true else super.dispatchKeyEvent(event)
    }

    private fun publishKeyboard(deviceId: Int, clearWhenMissing: Boolean) {
        val layout = readKeyboardLayout(deviceId)
        if (layout == null) {
            if (clearWhenMissing) {
                trainViewModel.onInputLanguage(null)
                trainViewModel.onLegendTable(LegendTable())
            }
            return
        }
        trainViewModel.onInputLanguage(layout.language)
        trainViewModel.onLegendTable(layout.legends)
    }
}
