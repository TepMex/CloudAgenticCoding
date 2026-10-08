package com.tepmex.cornegame.domain

/**
 * Коды Android KeyEvent. Домен не зависит от android.jar, поэтому числа записаны явно:
 * SPACE 62, ENTER 66, NUMPAD_ENTER 160, DEL 67, ALT_LEFT 57, ALT_RIGHT 58,
 * SHIFT_LEFT 59, SHIFT_RIGHT 60.
 *
 * Регистр не срезается: прописная приходит как unicode уже с Shift.
 * Enter на Cornedeon 2M — клавиша слоя знаков, не пробел.
 * Смена языка с аппаратной клавиатуры — Shift при зажатом Alt.
 */
object AndroidKeyCodes {
    const val ALT_LEFT = 57
    const val ALT_RIGHT = 58
    const val SHIFT_LEFT = 59
    const val SHIFT_RIGHT = 60
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val NUMPAD_ENTER = 160
}

fun mapHardwareKey(keyCode: Int, unicodeChar: Int, altPressed: Boolean = false): TrainerAction? {
    when (keyCode) {
        AndroidKeyCodes.SPACE -> return TrainerAction.Character(' ')
        AndroidKeyCodes.DEL -> return TrainerAction.Backspace
        AndroidKeyCodes.SHIFT_LEFT,
        AndroidKeyCodes.SHIFT_RIGHT,
        -> return if (altPressed) TrainerAction.AltShift else null
        AndroidKeyCodes.ALT_LEFT,
        AndroidKeyCodes.ALT_RIGHT,
        AndroidKeyCodes.ENTER,
        AndroidKeyCodes.NUMPAD_ENTER,
        -> return null
    }
    if (unicodeChar == 0) return null
    val raw = unicodeChar.toChar()
    if (raw == '\n' || raw == '\r' || raw == '\t') return null
    return TrainerAction.Character(raw)
}
