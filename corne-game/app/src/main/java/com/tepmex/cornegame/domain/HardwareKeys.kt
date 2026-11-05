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

/** ACTION_DOWN / ACTION_UP, те же числа, что у Android KeyEvent. */
const val HARDWARE_DOWN = 0
const val HARDWARE_UP = 1

data class HardwareStep(
    val pending: Map<Int, TrainerAction>,
    val deliver: TrainerAction?,
    val shiftDown: Boolean,
    val altDown: Boolean,
    val consumed: Boolean,
)

/**
 * Символ запоминаем на DOWN и отдаём на UP.
 * У части Bluetooth-клавиатур на UP unicode уже 0: если смотреть только UP,
 * нажатие пропадает, хотя буква на DOWN была.
 * Повтор зажатой клавиши в сессию не идёт.
 */
fun stepHardware(
    phase: Int,
    keyCode: Int,
    unicodeChar: Int,
    shiftDown: Boolean,
    altDown: Boolean,
    repeatCount: Int,
    pending: Map<Int, TrainerAction>,
): HardwareStep {
    if (phase != HARDWARE_DOWN && phase != HARDWARE_UP) {
        return HardwareStep(
            pending = pending - keyCode,
            deliver = null,
            shiftDown = shiftDown,
            altDown = altDown,
            consumed = keyCode in pending,
        )
    }
    if (phase == HARDWARE_DOWN) {
        if (repeatCount > 0) {
            return HardwareStep(pending, null, shiftDown, altDown, consumed = keyCode in pending)
        }
        val mapped = mapHardwareKey(keyCode, unicodeChar, altDown)
            ?: return HardwareStep(pending, null, shiftDown, altDown, consumed = false)
        return HardwareStep(pending + (keyCode to mapped), null, shiftDown, altDown, consumed = true)
    }
    pending[keyCode]?.let { saved ->
        return HardwareStep(pending - keyCode, saved, shiftDown, altDown, consumed = true)
    }
    val mapped = mapHardwareKey(keyCode, unicodeChar, altDown)
        ?: return HardwareStep(pending, null, shiftDown, altDown, consumed = false)
    return HardwareStep(pending, mapped, shiftDown, altDown, consumed = true)
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
