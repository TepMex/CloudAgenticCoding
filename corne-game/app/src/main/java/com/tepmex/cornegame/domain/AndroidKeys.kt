package com.tepmex.cornegame.domain

/** ACTION_DOWN / ACTION_UP / ACTION_MULTIPLE, те же числа, что у Android KeyEvent. */
const val HARDWARE_DOWN = 0
const val HARDWARE_UP = 1
const val HARDWARE_MULTIPLE = 2

/**
 * Коды Android KeyEvent. Домен не зависит от android.jar, поэтому числа записаны явно.
 * Буквы A–Z идут подряд с 29. Цифры верхнего ряда: 0 = 7 … 9 = 16.
 * F1–F12: 131–142. Стрелки — dpad 19–22.
 */
object AndroidKeyCodes {
    const val UNKNOWN = 0
    const val DIGIT_0 = 7
    const val DIGIT_1 = 8
    const val DIGIT_2 = 9
    const val DIGIT_3 = 10
    const val DIGIT_4 = 11
    const val DIGIT_5 = 12
    const val DIGIT_6 = 13
    const val DIGIT_7 = 14
    const val DIGIT_8 = 15
    const val DIGIT_9 = 16
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val A = 29
    const val B = 30
    const val C = 31
    const val D = 32
    const val E = 33
    const val F = 34
    const val G = 35
    const val H = 36
    const val I = 37
    const val J = 38
    const val K = 39
    const val L = 40
    const val M = 41
    const val N = 42
    const val O = 43
    const val P = 44
    const val Q = 45
    const val R = 46
    const val S = 47
    const val T = 48
    const val U = 49
    const val V = 50
    const val W = 51
    const val X = 52
    const val Y = 53
    const val Z = 54
    const val COMMA = 55
    const val PERIOD = 56
    const val ALT_LEFT = 57
    const val ALT_RIGHT = 58
    const val SHIFT_LEFT = 59
    const val SHIFT_RIGHT = 60
    const val TAB = 61
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val GRAVE = 68
    const val MINUS = 69
    const val EQUALS = 70
    const val LEFT_BRACKET = 71
    const val RIGHT_BRACKET = 72
    const val BACKSLASH = 73
    const val SEMICOLON = 74
    const val APOSTROPHE = 75
    const val SLASH = 76
    const val MENU = 82
    const val PAGE_UP = 92
    const val PAGE_DOWN = 93
    const val ESCAPE = 111
    const val FORWARD_DEL = 112
    const val CTRL_LEFT = 113
    const val CTRL_RIGHT = 114
    const val CAPS_LOCK = 115
    const val META_LEFT = 117
    const val META_RIGHT = 118
    const val SYSRQ = 120
    const val BREAK = 121
    const val MOVE_HOME = 122
    const val MOVE_END = 123
    const val INSERT = 124
    const val F1 = 131
    const val F2 = 132
    const val F3 = 133
    const val F4 = 134
    const val F5 = 135
    const val F6 = 136
    const val F7 = 137
    const val F8 = 138
    const val F9 = 139
    const val F10 = 140
    const val F11 = 141
    const val F12 = 142
    const val NUM_LOCK = 143
    const val NUMPAD_0 = 144
    const val NUMPAD_1 = 145
    const val NUMPAD_2 = 146
    const val NUMPAD_3 = 147
    const val NUMPAD_4 = 148
    const val NUMPAD_5 = 149
    const val NUMPAD_6 = 150
    const val NUMPAD_7 = 151
    const val NUMPAD_8 = 152
    const val NUMPAD_9 = 153
    const val NUMPAD_DIVIDE = 154
    const val NUMPAD_MULTIPLY = 155
    const val NUMPAD_SUBTRACT = 156
    const val NUMPAD_ADD = 157
    const val NUMPAD_DOT = 158
    const val NUMPAD_ENTER = 160
    const val NUMPAD_EQUALS = 161
    const val CALCULATOR = 210

    val F_KEYS: List<Int> = (F1..F12).toList()
}

/** Сторона не важна, пока упражнение само её не потребует. */
enum class KeyModifier {
    SHIFT,
    CTRL,
    ALT,
    META,
}

enum class SideKey {
    SHIFT_LEFT,
    SHIFT_RIGHT,
    CTRL_LEFT,
    CTRL_RIGHT,
    ALT_LEFT,
    ALT_RIGHT,
    META_LEFT,
    META_RIGHT,
    ;

    fun kind(): KeyModifier = when (this) {
        SHIFT_LEFT, SHIFT_RIGHT -> KeyModifier.SHIFT
        CTRL_LEFT, CTRL_RIGHT -> KeyModifier.CTRL
        ALT_LEFT, ALT_RIGHT -> KeyModifier.ALT
        META_LEFT, META_RIGHT -> KeyModifier.META
    }
}

fun sideKey(keyCode: Int): SideKey? = when (keyCode) {
    AndroidKeyCodes.SHIFT_LEFT -> SideKey.SHIFT_LEFT
    AndroidKeyCodes.SHIFT_RIGHT -> SideKey.SHIFT_RIGHT
    AndroidKeyCodes.CTRL_LEFT -> SideKey.CTRL_LEFT
    AndroidKeyCodes.CTRL_RIGHT -> SideKey.CTRL_RIGHT
    AndroidKeyCodes.ALT_LEFT -> SideKey.ALT_LEFT
    AndroidKeyCodes.ALT_RIGHT -> SideKey.ALT_RIGHT
    AndroidKeyCodes.META_LEFT -> SideKey.META_LEFT
    AndroidKeyCodes.META_RIGHT -> SideKey.META_RIGHT
    else -> null
}

/**
 * Откуда пришло событие.
 * PHYSICAL — реальный deviceId ≥ 0.
 * TEXT — уже готовый Unicode (ACTION_MULTIPLE, IME, KEYCODE_UNKNOWN со строкой).
 * UNKNOWN — виртуальное устройство или событие без подтверждённого источника.
 * deviceId = -1 никогда не считается Cornedeon.
 */
enum class InputSource {
    PHYSICAL,
    TEXT,
    UNKNOWN,
}

fun classifySource(deviceId: Int, phase: Int, keyCode: Int, characters: String?, unicodeChar: Int): InputSource {
    if (phase == HARDWARE_MULTIPLE) return InputSource.TEXT
    if (keyCode == AndroidKeyCodes.UNKNOWN && (unicodeChar != 0 || !characters.isNullOrEmpty())) {
        return InputSource.TEXT
    }
    if (deviceId >= 0) return InputSource.PHYSICAL
    return InputSource.UNKNOWN
}

/**
 * Таблицу символов можно читать только у того устройства, которое прислало событие.
 * Чужая клавиатура не доказывает, что виртуальное событие пришло с неё.
 */
fun ownKeyboardDevice(requestedId: Int, alphabeticDeviceIds: Set<Int>): Int? {
    if (requestedId >= 0 && requestedId in alphabeticDeviceIds) return requestedId
    return null
}
