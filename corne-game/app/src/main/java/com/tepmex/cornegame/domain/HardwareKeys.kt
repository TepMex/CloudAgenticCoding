package com.tepmex.cornegame.domain

/**
 * Коды Android KeyEvent. Домен не зависит от android.jar, поэтому числа записаны явно:
 * SPACE 62, ENTER 66, NUMPAD_ENTER 160, DEL 67.
 *
 * Буква приводится к нижнему регистру: Shift в упражнении не тренируется.
 * Символ забирается на ACTION_DOWN (так надёжнее для unicode), а в сессию
 * отдаётся на ACTION_UP — это делает Activity.
 */
object AndroidKeyCodes {
    const val SPACE = 62
    const val ENTER = 66
    const val NUMPAD_ENTER = 160
    const val DEL = 67
}

fun mapHardwareKey(keyCode: Int, unicodeChar: Int): TrainerAction? {
    when (keyCode) {
        AndroidKeyCodes.SPACE,
        AndroidKeyCodes.ENTER,
        AndroidKeyCodes.NUMPAD_ENTER,
        -> return TrainerAction.Character(' ')
        AndroidKeyCodes.DEL -> return TrainerAction.Backspace
    }
    if (unicodeChar == 0) return null
    val raw = unicodeChar.toChar()
    if (raw == '\n' || raw == '\r') return TrainerAction.Character(' ')
    val value = if (raw.isLetter()) raw.lowercaseChar() else raw
    return TrainerAction.Character(value)
}
