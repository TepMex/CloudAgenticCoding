package com.tepmex.cornegame.ui

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.ownKeyboardDevice
import com.tepmex.cornegame.domain.trackedLegendCodes

/**
 * Раскладка именно этого устройства. deviceId < 0 и чужая клавиатура сюда не подставляются:
 * виртуальное событие не доказывает, что его породила Cornedeon.
 * Таблица нужна для диагностики ОС. Символ упражнения считается по выбранному языку тренажёра.
 */
internal fun readKeyboardLayout(deviceId: Int): OsKeyboardSample? {
    val alphabetic = InputDevice.getDeviceIds().filterTo(mutableSetOf()) { candidate ->
        InputDevice.getDevice(candidate)?.isAlphabeticKeyboard() == true
    }
    val own = ownKeyboardDevice(deviceId, alphabetic) ?: return null
    val map = InputDevice.getDevice(own)?.keyCharacterMap ?: return null
    val language = when (map.get(KeyEvent.KEYCODE_Q, 0)) {
        'q'.code -> Language.EN
        'й'.code -> Language.RU
        else -> null
    }
    return OsKeyboardSample(language, readLegendTable(map))
}

internal data class OsKeyboardSample(
    val language: Language?,
    val legends: LegendTable,
)

/**
 * Поля KeyEvent для лога. [unicodeChar] — символ, который сообщило событие.
 * В сессию он попадает только как текст. Физический keycode разбирается отдельно.
 */
@Suppress("DEPRECATION")
internal fun readHardwareTrace(event: KeyEvent, unicodeChar: Int, unicodeBare: Int): HardwareKeyTrace {
    val deviceName = InputDevice.getDevice(event.deviceId)?.name ?: "-"
    return HardwareKeyTrace(
        phase = event.action,
        keyCode = event.keyCode,
        keyLabel = KeyEvent.keyCodeToString(event.keyCode),
        scanCode = event.scanCode,
        unicodeChar = unicodeChar,
        unicodeBare = unicodeBare,
        metaState = event.metaState,
        shift = event.isShiftPressed,
        alt = event.isAltPressed,
        ctrl = event.isCtrlPressed,
        capsLock = event.isCapsLockOn,
        meta = event.isMetaPressed,
        function = event.isFunctionPressed,
        repeatCount = event.repeatCount,
        deviceId = event.deviceId,
        deviceName = deviceName,
        source = event.source,
        flags = event.flags,
        characters = event.characters,
        displayLabel = event.displayLabel,
        number = event.number,
        downTime = event.downTime,
        eventTime = event.eventTime,
    )
}

private fun InputDevice.isAlphabeticKeyboard(): Boolean =
    !isVirtual && keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

private fun readLegendTable(map: KeyCharacterMap): LegendTable {
    fun read(shift: Boolean): Map<Int, Char> {
        val meta = if (shift) {
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        } else {
            0
        }
        return trackedLegendCodes().mapNotNull { code ->
            val value = map.get(code, meta)
            if (value == 0) null else code to value.toChar()
        }.toMap()
    }
    return LegendTable(plain = read(false), shifted = read(true))
}
