package com.tepmex.cornegame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tepmex.cornegame.domain.HardwareEvent
import com.tepmex.cornegame.domain.InputMachine
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.stepHardware
import com.tepmex.cornegame.ui.CorneApp
import com.tepmex.cornegame.ui.TrainViewModel
import com.tepmex.cornegame.ui.readHardwareTrace
import com.tepmex.cornegame.ui.readKeyboardLayout
import com.tepmex.cornegame.ui.theme.CorneTheme
import com.tepmex.cornegame.ui.trainViewModelFactory
import com.tepmex.cornegame.usb.AndroidUsbHardware
import com.tepmex.cornegame.domain.UsbDiagnosticsController

/**
 * Одна activity. Поворот её пересоздаёт, но [TrainViewModel] остаётся в store
 * и сессия не обнуляется. configChanges специально не перехватываем.
 *
 * Печатный символ фиксируется на ACTION_DOWN. ACTION_UP снимает удержание и
 * только тогда добирает символ, если на нажатии его ещё не было.
 * deviceId < 0 не читает чужую таблицу клавиш.
 */
class MainActivity : ComponentActivity() {
    private val trainViewModel: TrainViewModel by viewModels { trainViewModelFactory(this) }
    private val usbHardware = AndroidUsbHardware(this)
    val usb = UsbDiagnosticsController(usbHardware)

    private var machine = InputMachine()
    private val usbEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                -> usb.refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        usbHardware.register()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        trainViewModel.noteBoot(info.versionName ?: "?", versionCode)
        setContent {
            CorneTheme {
                CorneApp(trainViewModel, usb)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        noteOsMap(deviceId = -1)
        usb.refresh()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbEvents) }
        usbHardware.unregister()
        usb.close()
        super.onDestroy()
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
            machine,
            HardwareEvent(
                phase = event.action,
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                deviceId = event.deviceId,
                unicodeChar = unicodeChar,
                characters = event.characters,
                repeatCount = event.repeatCount,
                shift = event.isShiftPressed,
                alt = event.isAltPressed,
                ctrl = event.isCtrlPressed,
                meta = event.isMetaPressed,
                language = trainViewModel.resolutionLanguage(),
            ),
        )
        machine = step.machine
        trainViewModel.onHardwareModifiers(step.shiftDown, step.altDown, step.ctrlDown, step.metaDown)
        noteOsMap(event.deviceId)
        trainViewModel.onHardwareKey(before, trace, step)
        return if (step.consumed) true else super.dispatchKeyEvent(event)
    }

    /**
     * ОС-карта пишется в лог только для устройства, которое её отдало.
     * Виртуальный deviceId не заимствует таблицу другой клавиатуры и не меняет язык упражнения.
     */
    private fun noteOsMap(deviceId: Int) {
        if (deviceId < 0) {
            trainViewModel.noteKeyboard(
                deviceId = deviceId,
                found = false,
                language = null,
                table = LegendTable(),
                cleared = false,
            )
            return
        }
        val sample = readKeyboardLayout(deviceId)
        trainViewModel.noteKeyboard(
            deviceId = deviceId,
            found = sample != null,
            language = sample?.language,
            table = sample?.legends ?: LegendTable(),
            cleared = false,
        )
    }
}
