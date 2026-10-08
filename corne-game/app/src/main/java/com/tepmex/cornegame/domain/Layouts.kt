package com.tepmex.cornegame.domain

/**
 * Cornedeon 2M (v2m): матрица 4×6 на половинку, 48 клавиш.
 * Источник — прошивка alko-kbd/zmk-cornedeon-2mod, файл config/cornedeon_2mod.keymap
 * и геометрия config/info.json (keyboard_name «Cornedeon 2M ZMK»).
 *
 * Слои прошивки: 0 база, 1 Lower (MO на левом большом), 2 Upper (MO на правом).
 * Shift — модификатор, не отдельный слой прошивки: он даёт прописные и сдвинутые знаки.
 * Буквы русского слоя — это те же HID-коды QWERTY, прочитанные раскладкой ЙЦУКЕН.
 * Удержание T / P / M — mod-tap: grave, `[`, `]`.
 * Переключение языка — левый Alt, затем Shift (сочетание Windows по умолчанию).
 */

enum class Language {
    EN,
    RU,
}

fun Language.other(): Language = when (this) {
    Language.EN -> Language.RU
    Language.RU -> Language.EN
}

enum class Side {
    LEFT,
    RIGHT,
}

/** Десять пальцев: восемь на рядах и два больших. */
enum class Finger {
    LEFT_PINKY,
    LEFT_RING,
    LEFT_MIDDLE,
    LEFT_INDEX,
    RIGHT_INDEX,
    RIGHT_MIDDLE,
    RIGHT_RING,
    RIGHT_PINKY,
    LEFT_THUMB,
    RIGHT_THUMB,
}

/** Какой модификатор должен быть зажат, чтобы тап дал символ. */
enum class Latch {
    NONE,
    SHIFT,
    LOWER,
    UPPER,
}

/** Один шаг цели. ⇄ — не символ, а сочетание Alt, затем Shift. */
const val LANGUAGE_SWITCH = '\u21C4'

const val KEY_COUNT = 48
const val KEY_ROWS = 4
const val KEY_COLS = 6

private const val LEFT_SHIFT = 12
private const val RIGHT_SHIFT = 41
private const val LOWER_KEY = 22
private const val UPPER_KEY = 43
private const val ALT_KEY = 21

val SHIFT_KEY_IDS = setOf(LEFT_SHIFT, RIGHT_SHIFT)

private enum class Role {
    TEXT,
    SHIFT,
    LOWER,
    UPPER,
    ALT,
    BACKSPACE,
    NONE,
}

private data class Slot(
    val legend: String,
    val output: Char? = null,
    val holdLegend: String? = null,
    val holdOutput: Char? = null,
    val role: Role = Role.TEXT,
)

data class KeyFace(
    val id: Int,
    val column: Int,
    val legend: String,
    val holdLegend: String?,
    val finger: Finger,
    val tap: TrainerAction,
    val longPress: TrainerAction?,
    val primary: Boolean,
    val hinted: Boolean,
    val holdCue: Boolean,
)

data class KeyboardModel(
    val leftRows: List<List<KeyFace>>,
    val rightRows: List<List<KeyFace>>,
    val caption: String,
)

data class Stroke(
    val latch: Latch,
    val keyIds: Set<Int>,
    val hold: Boolean,
)

private fun named(
    legend: String,
    role: Role,
    output: Char? = null,
): Slot = Slot(legend = legend, output = output, role = role)

private val NAMED: Map<String, Slot> = buildMap {
    put("esc", named("Esc", Role.NONE))
    put("tab", named("Tab", Role.NONE))
    put("sft", named("Sft", Role.SHIFT))
    put("ctl", named("Ctl", Role.NONE))
    put("alt", named("Alt", Role.ALT))
    put("l1", named("L1", Role.LOWER))
    put("l2", named("L2", Role.UPPER))
    put("sp", named("␣", Role.TEXT, output = ' '))
    put("bs", named("⌫", Role.BACKSPACE))
    put("gui", named("Gui", Role.NONE))
    put("left", named("←", Role.NONE))
    put("right", named("→", Role.NONE))
    put("up", named("↑", Role.NONE))
    put("down", named("↓", Role.NONE))
    put("ins", named("Ins", Role.NONE))
    put("del", named("Del", Role.NONE))
    put("home", named("Home", Role.NONE))
    put("end", named("End", Role.NONE))
    put("ent", named("Ent", Role.NONE))
    put("pent", named("Enter", Role.NONE))
    put("pgu", named("PgUp", Role.NONE))
    put("pgd", named("PgDn", Role.NONE))
    put("caps", named("Caps", Role.NONE))
    put("prt", named("Prt", Role.NONE))
    put("pau", named("Pau", Role.NONE))
    put("num", named("Num", Role.NONE))
    put("scr", named("Scr", Role.NONE))
    put("men", named("Menu", Role.NONE))
    put("xx", named("·", Role.NONE))
    for (n in 1..12) {
        put("f$n", named("F$n", Role.NONE))
    }
}

private fun grid(text: String): List<Slot> {
    val tokens = text.trimIndent().lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { it.split(Regex("\\s+")) }
        .toList()
    require(tokens.size == KEY_COUNT) { "Ожидалось $KEY_COUNT, получено ${tokens.size}: $tokens" }
    return tokens.map { parseToken(it) }
}

private fun parseToken(token: String): Slot {
    val slash = token.indexOf('/')
    if (slash <= 0 || slash != token.lastIndex - 1) {
        return literal(token)
    }
    val tap = token.substring(0, slash)
    val hold = token.substring(slash + 1)
    require(tap.length == 1 && hold.length == 1) { token }
    return Slot(
        legend = tap,
        output = tap.first(),
        holdLegend = hold,
        holdOutput = hold.first(),
        role = Role.TEXT,
    )
}

private fun literal(token: String): Slot {
    NAMED[token]?.let { return it }
    require(token.length == 1) { token }
    return Slot(legend = token, output = token.first(), role = Role.TEXT)
}

// Сначала четыре ряда левой половинки, затем четыре ряда правой. Ряд больших — четвёртый.
private val enBase = grid(
    """
    esc q w e r t/`
    tab a s d f g
    sft z x c v b
    ctl left right alt l1 sp
    y u i o p/[ bs
    h j k l ; '
    n m/] , . / sft
    sp l2 gui up down ctl
    """,
)

private val enShift = grid(
    """
    esc Q W E R T/~
    tab A S D F G
    sft Z X C V B
    ctl left right alt l1 sp
    Y U I O P/{ bs
    H J K L : "
    N M/} < > ? sft
    sp l2 gui up down ctl
    """,
)

private val enLower = grid(
    """
    ~ xx ( ) * /
    tab xx [ ] + -
    sft xx { } < >
    ctl home end alt l1 ent
    7 8 9 0 ins del
    4 5 6 . / \
    1 2 3 = _ sft
    ent l2 gui pgu pgd ctl
    """,
)

private val ruBase = grid(
    """
    esc й ц у к е/ё
    tab ф ы в а п
    sft я ч с м и
    ctl left right alt l1 sp
    н г ш щ з/х bs
    р о л д ж э
    т ь/ъ б ю . sft
    sp l2 gui up down ctl
    """,
)

private val ruShift = grid(
    """
    esc Й Ц У К Е/Ё
    tab Ф Ы В А П
    sft Я Ч С М И
    ctl left right alt l1 sp
    Н Г Ш Щ З/Х bs
    Р О Л Д Ж Э
    Т Ь/Ъ Б Ю , sft
    sp l2 gui up down ctl
    """,
)

private val ruLower = grid(
    """
    Ё xx ( ) * .
    tab xx х ъ + -
    sft xx Х Ъ Б Ю
    ctl home end alt l1 ent
    7 8 9 0 ins del
    4 5 6 ю . \
    1 2 3 = _ sft
    ent l2 gui pgu pgd ctl
    """,
)

private val upper = grid(
    """
    * / 0 7 8 9
    + - . 4 5 6
    sft xx = 1 2 3
    ctl home end alt l1 pent
    f7 f8 f9 f10 prt pau
    f4 f5 f6 f11 num scr
    f1 f2 f3 f12 men sft
    ent l2 caps pgu pgd ctl
    """,
)

private fun slots(language: Language, latch: Latch): List<Slot> = when (latch) {
    Latch.NONE -> if (language == Language.EN) enBase else ruBase
    Latch.SHIFT -> if (language == Language.EN) enShift else ruShift
    Latch.LOWER -> if (language == Language.EN) enLower else ruLower
    Latch.UPPER -> upper
}

private fun Slot.tapAction(): TrainerAction = when (role) {
    Role.TEXT -> output?.let { TrainerAction.Character(it) } ?: TrainerAction.Unexpected
    Role.SHIFT -> TrainerAction.Shift
    Role.LOWER -> TrainerAction.Lower
    Role.UPPER -> TrainerAction.Upper
    Role.ALT -> TrainerAction.Alt
    Role.BACKSPACE -> TrainerAction.Backspace
    Role.NONE -> TrainerAction.Unexpected
}

private fun Slot.holdAction(): TrainerAction? = holdOutput?.let { TrainerAction.Character(it) }

fun fingerFor(id: Int): Finger {
    val local = id % 24
    val row = local / KEY_COLS
    val column = local % KEY_COLS
    val side = if (id < 24) Side.LEFT else Side.RIGHT
    if (row == 3) {
        return if (side == Side.LEFT) Finger.LEFT_THUMB else Finger.RIGHT_THUMB
    }
    return when (side) {
        Side.LEFT -> when (column) {
            0, 1 -> Finger.LEFT_PINKY
            2 -> Finger.LEFT_RING
            3 -> Finger.LEFT_MIDDLE
            else -> Finger.LEFT_INDEX
        }
        Side.RIGHT -> when (column) {
            0, 1 -> Finger.RIGHT_INDEX
            2 -> Finger.RIGHT_MIDDLE
            3 -> Finger.RIGHT_RING
            else -> Finger.RIGHT_PINKY
        }
    }
}

/** Буквы тапа базового слоя. Удержание и Lower в словарь слов не входят. */
fun baseLetters(language: Language): Set<Char> =
    slots(language, Latch.NONE).mapNotNull { slot ->
        slot.output?.takeIf { it.isLetter() }
    }.toSet()

fun strokeFor(char: Char, language: Language): Stroke? {
    for (latch in listOf(Latch.NONE, Latch.SHIFT)) {
        val ids = tapIds(char, language, latch)
        if (ids.isNotEmpty()) return Stroke(latch, ids, hold = false)
    }
    for (latch in listOf(Latch.NONE, Latch.SHIFT)) {
        val ids = holdIds(char, language, latch)
        if (ids.isNotEmpty()) return Stroke(latch, ids, hold = true)
    }
    for (latch in listOf(Latch.LOWER, Latch.UPPER)) {
        val ids = tapIds(char, language, latch)
        if (ids.isNotEmpty()) return Stroke(latch, ids, hold = false)
    }
    return null
}

private fun tapIds(char: Char, language: Language, latch: Latch): Set<Int> =
    slots(language, latch).mapIndexedNotNullTo(linkedSetOf<Int>()) { id, slot ->
        if (slot.output == char) id else null
    }

private fun holdIds(char: Char, language: Language, latch: Latch): Set<Int> =
    slots(language, latch).mapIndexedNotNullTo(linkedSetOf<Int>()) { id, slot ->
        if (slot.holdOutput == char) id else null
    }

/**
 * Каноническая последовательность тапов. Аппаратный ввод может прислать сразу
 * готовый символ, без предварительного Shift или L1.
 */
fun actionsToType(target: String, start: Language): List<TrainerAction> {
    var language = start
    val actions = mutableListOf<TrainerAction>()
    for (char in target) {
        if (char == LANGUAGE_SWITCH) {
            actions += TrainerAction.Alt
            actions += TrainerAction.Shift
            language = language.other()
            continue
        }
        val stroke = strokeFor(char, language) ?: error("Нет клавиши для '$char' / $language")
        when (stroke.latch) {
            Latch.SHIFT -> actions += TrainerAction.Shift
            Latch.LOWER -> actions += TrainerAction.Lower
            Latch.UPPER -> actions += TrainerAction.Upper
            Latch.NONE -> Unit
        }
        actions += TrainerAction.Character(char)
    }
    return actions
}

fun keyboardModel(session: TypingSession): KeyboardModel {
    val language = session.typingLanguage
    val marks = coach(session)
    val faces = slots(language, session.latch).mapIndexed { id, slot ->
        KeyFace(
            id = id,
            column = id % KEY_COLS,
            legend = slot.legend,
            holdLegend = slot.holdLegend,
            finger = fingerFor(id),
            tap = slot.tapAction(),
            longPress = slot.holdAction(),
            primary = id in marks.press,
            hinted = id in marks.hint,
            holdCue = marks.hold && id in marks.press,
        )
    }
    val left = List(KEY_ROWS) { row -> faces.subList(row * KEY_COLS, (row + 1) * KEY_COLS) }
    val right = List(KEY_ROWS) { row ->
        val start = 24 + row * KEY_COLS
        faces.subList(start, start + KEY_COLS)
    }
    return KeyboardModel(left, right, caption(session, marks))
}

private data class Coach(
    val press: Set<Int>,
    val hint: Set<Int>,
    val hold: Boolean,
)

private fun coach(session: TypingSession): Coach {
    if (session.completed) return Coach(emptySet(), emptySet(), hold = false)
    val next = session.nextChar ?: return Coach(emptySet(), emptySet(), hold = false)
    if (next == LANGUAGE_SWITCH) {
        val keys = if (session.altArmed) SHIFT_KEY_IDS else setOf(ALT_KEY)
        return Coach(keys, emptySet(), hold = false)
    }
    val stroke = strokeFor(next, session.typingLanguage)
        ?: return Coach(emptySet(), emptySet(), hold = false)
    if (session.latch != stroke.latch) {
        val modifiers = when (stroke.latch) {
            Latch.SHIFT -> SHIFT_KEY_IDS
            Latch.LOWER -> setOf(LOWER_KEY)
            Latch.UPPER -> setOf(UPPER_KEY)
            Latch.NONE -> stroke.keyIds
        }
        val hint = if (stroke.latch == Latch.NONE) emptySet() else stroke.keyIds
        return Coach(modifiers, hint, hold = false)
    }
    return Coach(stroke.keyIds, emptySet(), hold = stroke.hold)
}

private fun caption(session: TypingSession, marks: Coach): String {
    if (session.completed) return "Готово"
    val language = session.typingLanguage.name
    val next = session.nextChar
    if (next == LANGUAGE_SWITCH) {
        return if (session.altArmed) "$language · язык: теперь Shift" else "$language · язык: Alt, затем Shift"
    }
    val stroke = next?.let { strokeFor(it, session.typingLanguage) }
    val shown = session.latch
    val layer = when (shown) {
        Latch.NONE -> "строчные"
        Latch.SHIFT -> "прописные"
        Latch.LOWER -> "знаки"
        Latch.UPPER -> "функции"
    }
    val needed = when {
        stroke == null || shown == stroke.latch -> ""
        stroke.latch == Latch.SHIFT -> " · нужен Shift"
        stroke.latch == Latch.LOWER -> " · нужен L1"
        stroke.latch == Latch.UPPER -> " · нужен L2"
        else -> ""
    }
    val hold = if (marks.hold) " · удержать" else ""
    return "$language · $layer$needed$hold"
}

/** Слоты для тестов: легенда тапа на базовом английском слое. */
internal fun baseLegend(language: Language, id: Int): String =
    slots(language, Latch.NONE)[id].legend

internal fun baseOutput(language: Language, id: Int): Char? =
    slots(language, Latch.NONE)[id].output

internal fun baseHold(language: Language, id: Int): Char? =
    slots(language, Latch.NONE)[id].holdOutput
