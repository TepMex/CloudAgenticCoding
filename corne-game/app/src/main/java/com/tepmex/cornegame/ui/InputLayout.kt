package com.tepmex.cornegame.ui

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.trackedLegendCodes

/** Раскладка физической клавиатуры: какой символ даст этот код прямо сейчас. */
internal fun readKeyboardLayout(deviceId: Int): KeyboardLayout? {
    val map = keyboardMap(deviceId) ?: return null
    val language = when (map.get(KeyEvent.KEYCODE_Q, 0)) {
        'q'.code -> Language.EN
        'й'.code -> Language.RU
        else -> null
    }
    return KeyboardLayout(language, readLegendTable(map))
}

internal data class KeyboardLayout(
    val language: Language?,
    val legends: LegendTable,
)

/**
 * Поля KeyEvent для лога. [unicodeChar] — уже прочитанный символ с metaState,
 * тот же, что уходит в сессию. [unicodeBare] — символ без модификаторов.
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

private fun keyboardMap(deviceId: Int): KeyCharacterMap? {
    if (deviceId >= 0) {
        val device = InputDevice.getDevice(deviceId)
        if (device != null && device.isAlphabeticKeyboard()) return device.keyCharacterMap
    }
    val id = InputDevice.getDeviceIds().firstOrNull { candidate ->
        InputDevice.getDevice(candidate)?.isAlphabeticKeyboard() == true
    } ?: return null
    return InputDevice.getDevice(id)?.keyCharacterMap
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
