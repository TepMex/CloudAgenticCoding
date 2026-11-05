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
