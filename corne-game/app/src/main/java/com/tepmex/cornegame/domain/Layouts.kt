package com.tepmex.cornegame.domain

/**
 * Cornedeon 2M (v2m): матрица 4×6 на половинку, 48 клавиш.
 * Источник — выгрузка Vial `cornedeon_v2m.vil` (реальная раскладка клавиатуры).
 * Слои 0–2 совпадают с `keymaps/vial/keymap.c` в alko-kbd/cornedeon_2mod.
 * Правая половинка в файле Vial записана от внешнего края к внутреннему:
 * на экран она разворачивается слева направо.
 *
 * Слои прошивки: 0 база, 1 Lower (`FN_MO13` / `TL_LOWR` на левом большом),
 * 2 Upper (`FN_MO23` / `TL_UPPR` на правом). Одновременное удержание обоих
 * включает слой 3; в упражнение он не входит.
 * Shift — модификатор ОС, не слой прошивки: прописные и сдвинутые знаки базы.
 * Mod-tap в выгрузке нет: `` ` `` `[` `]` и русские `ё` `х` `ъ` живут на Lower.
 * Буквы русского слоя — те же HID-коды, прочитанные раскладкой ЙЦУКЕН.
 * Цифровой блок (`KC_KP_*`) от языка не зависит. Переключение языка — левый Alt, затем Shift.
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
    val hid: Int = 0,
    val forcedShift: Boolean = false,
)

/**
 * Живое состояние клавиатуры, отдельно от защёлки упражнения.
 * Shift и Alt приходят с физической клавиатуры и с зажатого пальца на экране.
 * L1/L2 Android с прошивки не видит: их даёт палец на нарисованной клавише.
 * [language] — раскладка подключённой клавиатуры; null значит «как у текста упражнения».
 */
data class BoardLive(
    val language: Language? = null,
    val hardwareShift: Boolean = false,
    val hardwareAlt: Boolean = false,
    val screenShift: Boolean = false,
    val screenAlt: Boolean = false,
    val screenLower: Boolean = false,
    val screenUpper: Boolean = false,
) {
    val shiftHeld: Boolean get() = hardwareShift || screenShift
    val altHeld: Boolean get() = hardwareAlt || screenAlt
}

/** Символы, которые KeyCharacterMap реально отдаёт для кодов клавиш. */
data class LegendTable(
    val plain: Map<Int, Char> = emptyMap(),
    val shifted: Map<Int, Char> = emptyMap(),
) {
    fun isBlank(): Boolean = plain.isEmpty() && shifted.isEmpty()

    fun char(code: Int, shift: Boolean): Char? {
        val found = if (shift) shifted[code] else plain[code]
        if (found == null || found == ' ' || found.isISOControl()) return null
        return found
    }
}

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
    put("calc", named("Calc", Role.NONE))
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

private data class Hid(val code: Int, val forcedShift: Boolean = false)

private fun h(code: Int, shift: Boolean = false) = Hid(code, shift)

private fun z() = Hid(0)

// Коды Android KeyEvent. Буквы и знаки, которые ОС превращает в символ.
// 0 — подпись (Esc, стрелки, L1), её раскладка не меняет.
private val baseHids: List<Hid> = listOf(
    z(), h(45), h(51), h(33), h(46), h(48),
    z(), h(29), h(47), h(32), h(34), h(35),
    z(), h(54), h(52), h(31), h(50), h(30),
    z(), z(), z(), z(), z(), z(),
    h(53), h(49), h(37), h(43), h(44), z(),
    h(36), h(38), h(39), h(40), h(74), h(75),
    h(42), h(41), h(55), h(56), h(76), z(),
    z(), z(), z(), z(), z(), z(),
)

private val lowerHids: List<Hid> = listOf(
    z(), h(68), h(16, true), h(7, true), h(155), h(154),
    z(), z(), h(71), h(72), h(70, true), h(69),
    z(), z(), h(71, true), h(72, true), h(55, true), h(56, true),
    z(), z(), z(), z(), z(), z(),
    h(14), h(15), h(16), h(7), z(), z(),
    h(11), h(12), h(13), h(158), h(69, true), h(73),
    h(8), h(9), h(10), h(161), h(76), z(),
    z(), z(), z(), z(), z(), z(),
)

private val upperHids: List<Hid> = listOf(
    h(155), h(154), h(144), h(151), h(152), h(153),
    h(157), h(156), h(158), h(148), h(149), h(150),
    z(), z(), h(161), h(145), h(146), h(147),
    z(), z(), z(), z(), z(), z(),
    z(), z(), z(), z(), z(), z(),
    z(), z(), z(), z(), z(), h(74),
    z(), z(), z(), z(), z(), z(),
    z(), z(), z(), z(), z(), z(),
)

private fun attach(slots: List<Slot>, hids: List<Hid>): List<Slot> {
    require(slots.size == hids.size) { "${slots.size} слотов и ${hids.size} кодов" }
    return slots.zip(hids) { slot, hid ->
        slot.copy(hid = hid.code, forcedShift = hid.forcedShift)
    }
}

/** Коды, которые надо спросить у раскладки ОС, чтобы легенда совпала с клавишей. */
fun trackedLegendCodes(): Set<Int> =
    (baseHids + lowerHids + upperHids).map { it.code }.filter { it != 0 }.toSet()

// Сначала четыре ряда левой половинки, затем четыре ряда правой. Ряд больших — четвёртый.
private val enBase = attach(grid(
    """
    esc q w e r t
    tab a s d f g
    sft z x c v b
    ctl left right alt l1 sp
    y u i o p bs
    h j k l ; '
    n m , . / sft
    sp l2 gui up down ctl
    """,
), baseHids)

private val enShift = attach(grid(
    """
    esc Q W E R T
    tab A S D F G
    sft Z X C V B
    ctl left right alt l1 sp
    Y U I O P bs
    H J K L : "
    N M < > ? sft
    sp l2 gui up down ctl
    """,
), baseHids)

private val enLower = attach(grid(
    """
    esc ` ( ) * /
    tab xx [ ] + -
    sft xx { } < >
    ctl home end alt l1 ent
    7 8 9 0 ins del
    4 5 6 . _ \
    1 2 3 = / sft
    ent l2 gui pgu pgd ctl
    """,
), lowerHids)

private val ruBase = attach(grid(
    """
    esc й ц у к е
    tab ф ы в а п
    sft я ч с м и
    ctl left right alt l1 sp
    н г ш щ з bs
    р о л д ж э
    т ь б ю . sft
    sp l2 gui up down ctl
    """,
), baseHids)

private val ruShift = attach(grid(
    """
    esc Й Ц У К Е
    tab Ф Ы В А П
    sft Я Ч С М И
    ctl left right alt l1 sp
    Н Г Ш Щ З bs
    Р О Л Д Ж Э
    Т Ь Б Ю , sft
    sp l2 gui up down ctl
    """,
), baseHids)

private val ruLower = attach(grid(
    """
    esc ё ( ) * /
    tab xx х ъ + -
    sft xx Х Ъ Б Ю
    ctl home end alt l1 ent
    7 8 9 0 ins del
    4 5 6 . _ \
    1 2 3 = . sft
    ent l2 gui pgu pgd ctl
    """,
), lowerHids)

private val enUpper = attach(grid(
    """
    * / 0 7 8 9
    + - . 4 5 6
    sft xx = 1 2 3
    ctl home end calc l1 pent
    f7 f8 f9 f10 prt pau
    f4 f5 f6 f11 num ;
    f1 f2 f3 f12 men sft
    ent l2 caps pgu pgd ctl
    """,
), upperHids)

private val ruUpper = attach(grid(
    """
    * / 0 7 8 9
    + - . 4 5 6
    sft xx = 1 2 3
    ctl home end calc l1 pent
    f7 f8 f9 f10 prt pau
    f4 f5 f6 f11 num ж
    f1 f2 f3 f12 men sft
    ent l2 caps pgu pgd ctl
    """,
), upperHids)

private fun slots(language: Language, latch: Latch): List<Slot> = when (latch) {
    Latch.NONE -> if (language == Language.EN) enBase else ruBase
    Latch.SHIFT -> if (language == Language.EN) enShift else ruShift
    Latch.LOWER -> if (language == Language.EN) enLower else ruLower
    Latch.UPPER -> if (language == Language.EN) enUpper else ruUpper
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

/** Буквы тапа базового слоя. Lower в словарь слов не входит. */
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

fun keyboardModel(
    session: TypingSession,
    live: BoardLive = BoardLive(),
    legends: LegendTable = LegendTable(),
): KeyboardModel {
    val language = live.language ?: session.typingLanguage
    val shown = shownLayer(session, live)
    val marks = coach(session, live, shown)
    val userShift = live.shiftHeld && (shown == Latch.LOWER || shown == Latch.UPPER)
    val faces = slots(language, shown).mapIndexed { id, raw ->
        val slot = raw
            .withUserShift(language, shown, id, userShift)
            .withDeviceLegend(legends, live.shiftHeld || shown == Latch.SHIFT)
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
    return KeyboardModel(left, right, caption(session, live, shown, marks))
}

private fun shownLayer(session: TypingSession, live: BoardLive): Latch = when {
    live.screenUpper -> Latch.UPPER
    live.screenLower -> Latch.LOWER
    session.latch == Latch.LOWER || session.latch == Latch.UPPER -> session.latch
    live.shiftHeld || session.latch == Latch.SHIFT -> Latch.SHIFT
    else -> Latch.NONE
}

private fun modifierReady(session: TypingSession, live: BoardLive, needed: Latch): Boolean = when (needed) {
    Latch.NONE -> true
    Latch.SHIFT -> live.shiftHeld || session.latch == Latch.SHIFT
    Latch.LOWER -> live.screenLower || session.latch == Latch.LOWER
    Latch.UPPER -> live.screenUpper || session.latch == Latch.UPPER
}

private fun Slot.withUserShift(language: Language, shown: Latch, id: Int, active: Boolean): Slot {
    if (!active) return this
    val shifted = userShiftedSymbol(language, shown, id) ?: return this
    return copy(legend = shifted.toString(), output = shifted, role = Role.TEXT)
}

private fun userShiftedSymbol(language: Language, shown: Latch, id: Int): Char? {
    if (shown == Latch.UPPER && id == 35) {
        return if (language == Language.EN) ':' else 'Ж'
    }
    if (shown != Latch.LOWER) return null
    // Уже сдвинутые коды прошивки и цифровой блок от дополнительного Shift не меняются.
    if (id in setOf(2, 3, 4, 5, 10, 14, 15, 16, 17, 33, 34, 39)) return null
    return if (language == Language.EN) {
        when (id) {
            1 -> '~'
            8 -> '{'
            9 -> '}'
            11 -> '_'
            24 -> '&'
            25 -> '*'
            26 -> '('
            27 -> ')'
            30 -> '$'
            31 -> '%'
            32 -> '^'
            35 -> '|'
            36 -> '!'
            37 -> '@'
            38 -> '#'
            40 -> '?'
            else -> null
        }
    } else {
        when (id) {
            1 -> 'Ё'
            8 -> 'Х'
            9 -> 'Ъ'
            11 -> '_'
            24 -> '?'
            25 -> '*'
            26 -> '('
            27 -> ')'
            30 -> ';'
            31 -> '%'
            32 -> ':'
            35 -> '/'
            36 -> '!'
            37 -> '"'
            38 -> '№'
            40 -> ','
            else -> null
        }
    }
}

private fun Slot.withDeviceLegend(table: LegendTable, shift: Boolean): Slot {
    if (hid == 0 || table.isBlank()) return this
    val produced = table.char(hid, forcedShift || shift) ?: return this
    return copy(legend = produced.toString(), output = produced, role = Role.TEXT)
}

private data class Coach(
    val press: Set<Int>,
    val hint: Set<Int>,
    val hold: Boolean,
)

private fun coach(session: TypingSession, live: BoardLive, shown: Latch): Coach {
    if (session.completed) return Coach(emptySet(), emptySet(), hold = false)
    val next = session.nextChar ?: return Coach(emptySet(), emptySet(), hold = false)
    if (next == LANGUAGE_SWITCH) {
        val keys = if (session.altArmed || live.altHeld) SHIFT_KEY_IDS else setOf(ALT_KEY)
        return Coach(keys, emptySet(), hold = false)
    }
    val stroke = strokeFor(next, session.typingLanguage)
        ?: return Coach(emptySet(), emptySet(), hold = false)
    if (!modifierReady(session, live, stroke.latch)) {
        val modifiers = when (stroke.latch) {
            Latch.SHIFT -> SHIFT_KEY_IDS
            Latch.LOWER -> setOf(LOWER_KEY)
            Latch.UPPER -> setOf(UPPER_KEY)
            Latch.NONE -> stroke.keyIds
        }
        val hint = if (stroke.latch == Latch.NONE) emptySet() else stroke.keyIds
        return Coach(modifiers, hint, hold = false)
    }
    return Coach(stroke.keyIds, emptySet(), hold = stroke.hold && shown == stroke.latch)
}

private fun caption(session: TypingSession, live: BoardLive, shown: Latch, marks: Coach): String {
    if (session.completed) return "Готово"
    val keys = live.language ?: session.typingLanguage
    val next = session.nextChar
    if (next == LANGUAGE_SWITCH) {
        val armed = session.altArmed || live.altHeld
        val label = keys.name
        return if (armed) "$label · язык: теперь Shift" else "$label · язык: Alt, затем Shift"
    }
    val stroke = next?.let { strokeFor(it, session.typingLanguage) }
    val layer = when (shown) {
        Latch.NONE -> "строчные"
        Latch.SHIFT -> "прописные"
        Latch.LOWER -> "знаки"
        Latch.UPPER -> "функции"
    }
    val needed = when {
        stroke == null || modifierReady(session, live, stroke.latch) -> ""
        stroke.latch == Latch.SHIFT -> " · нужен Shift"
        stroke.latch == Latch.LOWER -> " · нужен L1"
        stroke.latch == Latch.UPPER -> " · нужен L2"
        else -> ""
    }
    val hold = if (marks.hold) " · удержать" else ""
    val otherText = if (live.language != null && live.language != session.typingLanguage) {
        " · текст ${session.typingLanguage.name}"
    } else {
        ""
    }
    return "${keys.name} · $layer$needed$hold$otherText"
}

/** Слоты для тестов: легенда тапа на базовом английском слое. */
internal fun baseLegend(language: Language, id: Int): String =
    slots(language, Latch.NONE)[id].legend

internal fun baseOutput(language: Language, id: Int): Char? =
    slots(language, Latch.NONE)[id].output

internal fun baseHold(language: Language, id: Int): Char? =
    slots(language, Latch.NONE)[id].holdOutput
