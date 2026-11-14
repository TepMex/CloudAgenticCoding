package com.tepmex.cornegame.domain

/**
 * Какой символ даёт Android keycode на Cornedeon при выбранном в тренажёре языке.
 * Это не подмена чужого KeyCharacterMap и не таблица «латиница → кириллица» для уже готового Unicode.
 * Слой прошивки меняет keycode, который приходит в Android: KC_SLASH и KC_KP_SLASH — разные коды.
 */
data class Glyph(val plain: Char, val shifted: Char)

fun glyphFor(language: Language, keyCode: Int): Glyph? {
    sharedGlyph(keyCode)?.let { return it }
    val specific = if (language == Language.EN) EN_GLYPHS else RU_GLYPHS
    return specific[keyCode]
}

fun isNonCharacterKey(keyCode: Int): Boolean = keyCode in NON_CHARACTER_KEYS

/** Символы, которыми это физическое нажатие может отозваться во втором пути (IME / MULTIPLE). */
fun echoAliases(keyCode: Int, produced: Char?, unicodeChar: Int): Set<Char> {
    val aliases = linkedSetOf<Char>()
    if (produced != null) aliases += produced
    if (unicodeChar != 0) {
        val raw = unicodeChar.toChar()
        if (!raw.isISOControl()) aliases += raw
    }
    if (keyCode != AndroidKeyCodes.UNKNOWN) {
        glyphFor(Language.EN, keyCode)?.let {
            aliases += it.plain
            aliases += it.shifted
        }
        glyphFor(Language.RU, keyCode)?.let {
            aliases += it.plain
            aliases += it.shifted
        }
    }
    return aliases
}

private fun letter(plain: Char): Glyph = Glyph(plain, plain.uppercaseChar())

private fun pair(plain: Char, shifted: Char): Glyph = Glyph(plain, shifted)

private val EN_GLYPHS: Map<Int, Glyph> = buildMap {
    put(AndroidKeyCodes.A, letter('a'))
    put(AndroidKeyCodes.B, letter('b'))
    put(AndroidKeyCodes.C, letter('c'))
    put(AndroidKeyCodes.D, letter('d'))
    put(AndroidKeyCodes.E, letter('e'))
    put(AndroidKeyCodes.F, letter('f'))
    put(AndroidKeyCodes.G, letter('g'))
    put(AndroidKeyCodes.H, letter('h'))
    put(AndroidKeyCodes.I, letter('i'))
    put(AndroidKeyCodes.J, letter('j'))
    put(AndroidKeyCodes.K, letter('k'))
    put(AndroidKeyCodes.L, letter('l'))
    put(AndroidKeyCodes.M, letter('m'))
    put(AndroidKeyCodes.N, letter('n'))
    put(AndroidKeyCodes.O, letter('o'))
    put(AndroidKeyCodes.P, letter('p'))
    put(AndroidKeyCodes.Q, letter('q'))
    put(AndroidKeyCodes.R, letter('r'))
    put(AndroidKeyCodes.S, letter('s'))
    put(AndroidKeyCodes.T, letter('t'))
    put(AndroidKeyCodes.U, letter('u'))
    put(AndroidKeyCodes.V, letter('v'))
    put(AndroidKeyCodes.W, letter('w'))
    put(AndroidKeyCodes.X, letter('x'))
    put(AndroidKeyCodes.Y, letter('y'))
    put(AndroidKeyCodes.Z, letter('z'))
    put(AndroidKeyCodes.COMMA, pair(',', '<'))
    put(AndroidKeyCodes.PERIOD, pair('.', '>'))
    put(AndroidKeyCodes.SLASH, pair('/', '?'))
    put(AndroidKeyCodes.SEMICOLON, pair(';', ':'))
    put(AndroidKeyCodes.APOSTROPHE, pair('\'', '"'))
    put(AndroidKeyCodes.GRAVE, pair('`', '~'))
    put(AndroidKeyCodes.MINUS, pair('-', '_'))
    put(AndroidKeyCodes.EQUALS, pair('=', '+'))
    put(AndroidKeyCodes.LEFT_BRACKET, pair('[', '{'))
    put(AndroidKeyCodes.RIGHT_BRACKET, pair(']', '}'))
    put(AndroidKeyCodes.BACKSLASH, pair('\\', '|'))
    put(AndroidKeyCodes.DIGIT_0, pair('0', ')'))
    put(AndroidKeyCodes.DIGIT_1, pair('1', '!'))
    put(AndroidKeyCodes.DIGIT_2, pair('2', '@'))
    put(AndroidKeyCodes.DIGIT_3, pair('3', '#'))
    put(AndroidKeyCodes.DIGIT_4, pair('4', '$'))
    put(AndroidKeyCodes.DIGIT_5, pair('5', '%'))
    put(AndroidKeyCodes.DIGIT_6, pair('6', '^'))
    put(AndroidKeyCodes.DIGIT_7, pair('7', '&'))
    put(AndroidKeyCodes.DIGIT_8, pair('8', '*'))
    put(AndroidKeyCodes.DIGIT_9, pair('9', '('))
}

private val RU_GLYPHS: Map<Int, Glyph> = buildMap {
    put(AndroidKeyCodes.Q, letter('й'))
    put(AndroidKeyCodes.W, letter('ц'))
    put(AndroidKeyCodes.E, letter('у'))
    put(AndroidKeyCodes.R, letter('к'))
    put(AndroidKeyCodes.T, letter('е'))
    put(AndroidKeyCodes.Y, letter('н'))
    put(AndroidKeyCodes.U, letter('г'))
    put(AndroidKeyCodes.I, letter('ш'))
    put(AndroidKeyCodes.O, letter('щ'))
    put(AndroidKeyCodes.P, letter('з'))
    put(AndroidKeyCodes.A, letter('ф'))
    put(AndroidKeyCodes.S, letter('ы'))
    put(AndroidKeyCodes.D, letter('в'))
    put(AndroidKeyCodes.F, letter('а'))
    put(AndroidKeyCodes.G, letter('п'))
    put(AndroidKeyCodes.H, letter('р'))
    put(AndroidKeyCodes.J, letter('о'))
    put(AndroidKeyCodes.K, letter('л'))
    put(AndroidKeyCodes.L, letter('д'))
    put(AndroidKeyCodes.SEMICOLON, letter('ж'))
    put(AndroidKeyCodes.APOSTROPHE, letter('э'))
    put(AndroidKeyCodes.Z, letter('я'))
    put(AndroidKeyCodes.X, letter('ч'))
    put(AndroidKeyCodes.C, letter('с'))
    put(AndroidKeyCodes.V, letter('м'))
    put(AndroidKeyCodes.B, letter('и'))
    put(AndroidKeyCodes.N, letter('т'))
    put(AndroidKeyCodes.M, letter('ь'))
    put(AndroidKeyCodes.COMMA, letter('б'))
    put(AndroidKeyCodes.PERIOD, letter('ю'))
    put(AndroidKeyCodes.SLASH, pair('.', ','))
    put(AndroidKeyCodes.GRAVE, letter('ё'))
    put(AndroidKeyCodes.LEFT_BRACKET, letter('х'))
    put(AndroidKeyCodes.RIGHT_BRACKET, letter('ъ'))
    put(AndroidKeyCodes.MINUS, pair('-', '_'))
    put(AndroidKeyCodes.EQUALS, pair('=', '+'))
    put(AndroidKeyCodes.BACKSLASH, pair('\\', '/'))
    put(AndroidKeyCodes.DIGIT_0, pair('0', ')'))
    put(AndroidKeyCodes.DIGIT_1, pair('1', '!'))
    put(AndroidKeyCodes.DIGIT_2, pair('2', '"'))
    put(AndroidKeyCodes.DIGIT_3, pair('3', '№'))
    put(AndroidKeyCodes.DIGIT_4, pair('4', ';'))
    put(AndroidKeyCodes.DIGIT_5, pair('5', '%'))
    put(AndroidKeyCodes.DIGIT_6, pair('6', ':'))
    put(AndroidKeyCodes.DIGIT_7, pair('7', '?'))
    put(AndroidKeyCodes.DIGIT_8, pair('8', '*'))
    put(AndroidKeyCodes.DIGIT_9, pair('9', '('))
}

/** Цифровой блок не зависит от языка. KC_KP_* не равен соседнему HID-коду. */
private fun sharedGlyph(keyCode: Int): Glyph? = when (keyCode) {
    AndroidKeyCodes.NUMPAD_0 -> pair('0', '0')
    AndroidKeyCodes.NUMPAD_1 -> pair('1', '1')
    AndroidKeyCodes.NUMPAD_2 -> pair('2', '2')
    AndroidKeyCodes.NUMPAD_3 -> pair('3', '3')
    AndroidKeyCodes.NUMPAD_4 -> pair('4', '4')
    AndroidKeyCodes.NUMPAD_5 -> pair('5', '5')
    AndroidKeyCodes.NUMPAD_6 -> pair('6', '6')
    AndroidKeyCodes.NUMPAD_7 -> pair('7', '7')
    AndroidKeyCodes.NUMPAD_8 -> pair('8', '8')
    AndroidKeyCodes.NUMPAD_9 -> pair('9', '9')
    AndroidKeyCodes.NUMPAD_DIVIDE -> pair('/', '/')
    AndroidKeyCodes.NUMPAD_MULTIPLY -> pair('*', '*')
    AndroidKeyCodes.NUMPAD_SUBTRACT -> pair('-', '-')
    AndroidKeyCodes.NUMPAD_ADD -> pair('+', '+')
    AndroidKeyCodes.NUMPAD_DOT -> pair('.', '.')
    AndroidKeyCodes.NUMPAD_EQUALS -> pair('=', '=')
    else -> null
}

private val NON_CHARACTER_KEYS: Set<Int> = buildSet {
    add(AndroidKeyCodes.ESCAPE)
    add(AndroidKeyCodes.TAB)
    add(AndroidKeyCodes.ENTER)
    add(AndroidKeyCodes.NUMPAD_ENTER)
    add(AndroidKeyCodes.FORWARD_DEL)
    add(AndroidKeyCodes.MOVE_HOME)
    add(AndroidKeyCodes.MOVE_END)
    add(AndroidKeyCodes.PAGE_UP)
    add(AndroidKeyCodes.PAGE_DOWN)
    add(AndroidKeyCodes.DPAD_LEFT)
    add(AndroidKeyCodes.DPAD_RIGHT)
    add(AndroidKeyCodes.DPAD_UP)
    add(AndroidKeyCodes.DPAD_DOWN)
    add(AndroidKeyCodes.INSERT)
    add(AndroidKeyCodes.CAPS_LOCK)
    add(AndroidKeyCodes.SYSRQ)
    add(AndroidKeyCodes.BREAK)
    add(AndroidKeyCodes.NUM_LOCK)
    add(AndroidKeyCodes.MENU)
    add(AndroidKeyCodes.CALCULATOR)
    addAll(AndroidKeyCodes.F_KEYS)
}
