package com.tepmex.cornegame.domain

/**
 * Ввод разделён на три вещи: нажатие, отпускание и текст.
 * Печатное действие фиксируется на ACTION_DOWN, если символ уже известен
 * (раскладка Cornedeon для keycode или готовый Unicode). ACTION_UP только
 * снимает удержание и, если на DOWN символа не было, забирает запоздалый Unicode.
 * Оба пути одну и ту же клавишу второй раз не вводят.
 *
 * Перекрывающиеся нажатия поэтому сохраняют порядок DOWN, а не порядок UP:
 * DOWN A, DOWN B, UP B, UP A → «ab».
 */

data class HardwareEvent(
    val phase: Int,
    val keyCode: Int,
    val scanCode: Int = 0,
    val deviceId: Int = -1,
    val unicodeChar: Int = 0,
    val characters: String? = null,
    val repeatCount: Int = 0,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
    val meta: Boolean = false,
    val language: Language = Language.EN,
)

data class HeldKey(
    val committed: Boolean,
    val aliases: Set<Char>,
)

data class InputMachine(
    val held: Map<Int, HeldKey> = emptyMap(),
    val echoes: List<Set<Char>> = emptyList(),
    val modifiers: Set<SideKey> = emptySet(),
)

/** Одно распознанное действие вместе с кодом клавиши, которым оно получено. */
data class Resolved(
    val action: TrainerAction,
    val keyCode: Int,
    val modifiers: Set<KeyModifier>,
    val source: InputSource,
)

data class HardwareStep(
    val machine: InputMachine,
    val deliver: List<Resolved>,
    val shiftDown: Boolean,
    val altDown: Boolean,
    val ctrlDown: Boolean,
    val metaDown: Boolean,
    val consumed: Boolean,
    val source: InputSource,
    val language: Language,
    val heldModifiers: Set<KeyModifier>,
    val deviceId: Int,
    val scanCode: Int,
    val keyCode: Int,
    val unicodeChar: Int,
    val characters: String?,
)

fun stepHardware(machine: InputMachine, event: HardwareEvent): HardwareStep {
    val source = classifySource(
        deviceId = event.deviceId,
        phase = event.phase,
        keyCode = event.keyCode,
        characters = event.characters,
        unicodeChar = event.unicodeChar,
    )
    return when (event.phase) {
        HARDWARE_MULTIPLE -> onText(machine, event, source)
        HARDWARE_DOWN -> onDown(machine, event, source)
        HARDWARE_UP -> onUp(machine, event, source)
        else -> pack(machine, event, source, emptyList(), consumed = false)
    }
}

/**
 * Запасной разбор готового Unicode (ACTION_MULTIPLE и UP без своей клавиши в раскладке).
 * Enter и таб текстом не становятся: на Cornedeon Enter — не пробел.
 */
fun mapHardwareKey(keyCode: Int, unicodeChar: Int, altPressed: Boolean = false): TrainerAction? {
    when (keyCode) {
        AndroidKeyCodes.SPACE -> return TrainerAction.Character(' ')
        AndroidKeyCodes.DEL -> return TrainerAction.Backspace
        AndroidKeyCodes.SHIFT_LEFT,
        AndroidKeyCodes.SHIFT_RIGHT,
        -> return if (altPressed) TrainerAction.AltShift else null
        AndroidKeyCodes.ALT_LEFT,
        AndroidKeyCodes.ALT_RIGHT,
        AndroidKeyCodes.CTRL_LEFT,
        AndroidKeyCodes.CTRL_RIGHT,
        AndroidKeyCodes.META_LEFT,
        AndroidKeyCodes.META_RIGHT,
        AndroidKeyCodes.ENTER,
        AndroidKeyCodes.NUMPAD_ENTER,
        -> return null
    }
    if (unicodeChar == 0) return null
    val raw = unicodeChar.toChar()
    if (raw == '\n' || raw == '\r' || raw == '\t' || raw.isISOControl()) return null
    return TrainerAction.Character(raw)
}

internal fun actionsFromCharacters(characters: String?): List<TrainerAction> {
    if (characters.isNullOrEmpty()) return emptyList()
    val out = ArrayList<TrainerAction>(characters.length)
    var index = 0
    while (index < characters.length) {
        val code = characters.codePointAt(index)
        if (code in 1..0xFFFF) {
            mapHardwareKey(AndroidKeyCodes.UNKNOWN, code)?.let { out += it }
        }
        index += Character.charCount(code)
    }
    return out
}

private fun onText(machine: InputMachine, event: HardwareEvent, source: InputSource): HardwareStep {
    val typed = when {
        !event.characters.isNullOrEmpty() -> actionsFromCharacters(event.characters)
        else -> listOfNotNull(mapHardwareKey(AndroidKeyCodes.UNKNOWN, event.unicodeChar))
    }
    if (typed.isEmpty()) {
        return pack(machine, event, source, emptyList(), consumed = false)
    }
    val (next, deliver) = absorbEcho(machine, typed, event)
    val consumed = deliver.isNotEmpty() || next.echoes.size < machine.echoes.size || typed.isNotEmpty() &&
        deliver.size < typed.size
    return pack(next, event, source, deliver, consumed = consumed || typed.isNotEmpty())
}

private fun onDown(machine: InputMachine, event: HardwareEvent, source: InputSource): HardwareStep {
    val side = sideKey(event.keyCode)
    if (side != null) return onModifierDown(machine, event, source, side)
    if (event.repeatCount > 0) {
        return pack(machine, event, source, emptyList(), consumed = event.keyCode in machine.held)
    }
    val mods = effectiveModifiers(machine.modifiers, event, ownKey = null)
    val action = resolveDown(event, mods, source)
    if (action == null) {
        val held = machine.held + (event.keyCode to HeldKey(committed = false, aliases = emptySet()))
        return pack(machine.copy(held = held), event, source, emptyList(), consumed = false, modifiers = mods)
    }
    val produced = (action as? TrainerAction.Character)?.value
    val aliases = echoAliases(event.keyCode, produced, event.unicodeChar)
    val held = machine.held + (event.keyCode to HeldKey(committed = true, aliases = aliases))
    val echoes = if (aliases.isEmpty()) machine.echoes else (machine.echoes + listOf(aliases)).takeLast(ECHO_LIMIT)
    val resolved = Resolved(
        action = action,
        keyCode = event.keyCode,
        modifiers = shortcutModifiers(action),
        source = source,
    )
    return pack(
        machine.copy(held = held, echoes = echoes),
        event,
        source,
        listOf(resolved),
        consumed = action !is TrainerAction.AltShift,
        modifiers = mods,
    )
}

private fun onUp(machine: InputMachine, event: HardwareEvent, source: InputSource): HardwareStep {
    val side = sideKey(event.keyCode)
    if (side != null) {
        val next = machine.copy(modifiers = machine.modifiers - side, held = machine.held - event.keyCode)
        return pack(next, event, source, emptyList(), consumed = false)
    }
    val current = machine.held[event.keyCode]
    val nextHeld = machine.held - event.keyCode
    if (current == null || current.committed || event.repeatCount > 0) {
        return pack(machine.copy(held = nextHeld), event, source, emptyList(), consumed = current != null)
    }
    val fallback = fallbackUnicode(event)
    if (fallback == null) {
        return pack(machine.copy(held = nextHeld), event, source, emptyList(), consumed = false)
    }
    val aliases = echoAliases(event.keyCode, fallback.value, event.unicodeChar)
    val echoes = if (aliases.isEmpty()) machine.echoes else (machine.echoes + listOf(aliases)).takeLast(ECHO_LIMIT)
    val resolved = Resolved(fallback, event.keyCode, emptySet(), source)
    return pack(
        machine.copy(held = nextHeld, echoes = echoes),
        event,
        source,
        listOf(resolved),
        consumed = true,
    )
}

private fun onModifierDown(
    machine: InputMachine,
    event: HardwareEvent,
    source: InputSource,
    side: SideKey,
): HardwareStep {
    if (event.repeatCount > 0) {
        return pack(machine, event, source, emptyList(), consumed = false)
    }
    val before = machine.modifiers.map { it.kind() }.toSet()
    val modifiers = machine.modifiers + side
    val after = effectiveModifiers(modifiers, event, ownKey = null)
    val beforeFlags = before.toMutableSet()
    if (event.alt && side.kind() != KeyModifier.ALT) beforeFlags += KeyModifier.ALT
    if (event.shift && side.kind() != KeyModifier.SHIFT) beforeFlags += KeyModifier.SHIFT
    val completesAltShift = KeyModifier.ALT in after && KeyModifier.SHIFT in after &&
        !(KeyModifier.ALT in beforeFlags && KeyModifier.SHIFT in beforeFlags)
    val deliver = if (completesAltShift) {
        listOf(
            Resolved(
                TrainerAction.AltShift,
                event.keyCode,
                setOf(KeyModifier.ALT, KeyModifier.SHIFT),
                source,
            ),
        )
    } else {
        emptyList()
    }
    return pack(
        machine.copy(modifiers = modifiers),
        event,
        source,
        deliver,
        consumed = false,
        modifiers = after,
    )
}

private fun resolveDown(event: HardwareEvent, mods: Set<KeyModifier>, source: InputSource): TrainerAction? {
    val blocking = mods.any { it == KeyModifier.CTRL || it == KeyModifier.ALT || it == KeyModifier.META }
    if (blocking || (KeyModifier.SHIFT in mods && glyphFor(event.language, event.keyCode) == null &&
            event.keyCode != AndroidKeyCodes.SPACE && event.keyCode != AndroidKeyCodes.DEL &&
            isNonCharacterKey(event.keyCode))
    ) {
        if (event.keyCode != AndroidKeyCodes.UNKNOWN && (blocking || isNonCharacterKey(event.keyCode))) {
            return TrainerAction.Shortcut(event.keyCode, mods)
        }
    }
    if (event.keyCode == AndroidKeyCodes.SPACE) return TrainerAction.Character(' ')
    if (event.keyCode == AndroidKeyCodes.DEL && !blocking) return TrainerAction.Backspace
    val glyph = if (source == InputSource.TEXT) null else glyphFor(event.language, event.keyCode)
    if (glyph != null && !blocking) {
        val ch = if (KeyModifier.SHIFT in mods) glyph.shifted else glyph.plain
        return TrainerAction.Character(ch)
    }
    if (!blocking && isNonCharacterKey(event.keyCode)) return TrainerAction.KeyPress(event.keyCode)
    if (source == InputSource.TEXT) {
        return mapHardwareKey(AndroidKeyCodes.UNKNOWN, event.unicodeChar)
    }
    // На DOWN нет ни раскладки, ни готового символа — подождём UP.
    if (event.unicodeChar == 0) return null
    // Keycode нам известен как клавиша Cornedeon, но glyph пуст: Unicode виртуального
    // события не подменяет раскладку. Неизвестный код может отдать свой Unicode.
    if (glyphFor(Language.EN, event.keyCode) != null || glyphFor(Language.RU, event.keyCode) != null) {
        return null
    }
    return mapHardwareKey(event.keyCode, event.unicodeChar)
}

private fun fallbackUnicode(event: HardwareEvent): TrainerAction.Character? {
    if (glyphFor(event.language, event.keyCode) != null) return null
    if (isNonCharacterKey(event.keyCode)) return null
    val mapped = mapHardwareKey(AndroidKeyCodes.UNKNOWN, event.unicodeChar)
    return mapped as? TrainerAction.Character
}

private fun absorbEcho(
    machine: InputMachine,
    typed: List<TrainerAction>,
    event: HardwareEvent,
): Pair<InputMachine, List<Resolved>> {
    val echoes = machine.echoes.toMutableList()
    var held = machine.held
    val deliver = mutableListOf<Resolved>()
    for (action in typed) {
        val ch = (action as? TrainerAction.Character)?.value
        val echoAt = if (ch == null) -1 else echoes.indexOfFirst { ch in it }
        if (echoAt >= 0) {
            echoes.removeAt(echoAt)
            held = markOneWaiting(held)
            continue
        }
        deliver += Resolved(action, event.keyCode, emptySet(), InputSource.TEXT)
        held = markOneWaiting(held)
    }
    return machine.copy(held = held, echoes = echoes) to deliver
}

/** Текст, пришедший вместо ещё не зафиксированного DOWN, закрывает это нажатие. */
private fun markOneWaiting(held: Map<Int, HeldKey>): Map<Int, HeldKey> {
    val waiting = held.entries.firstOrNull { !it.value.committed } ?: return held
    return held + (waiting.key to waiting.value.copy(committed = true))
}

private fun shortcutModifiers(action: TrainerAction): Set<KeyModifier> =
    (action as? TrainerAction.Shortcut)?.modifiers ?: emptySet()

private fun effectiveModifiers(tracked: Set<SideKey>, event: HardwareEvent, ownKey: SideKey?): Set<KeyModifier> {
    val kinds = tracked.mapTo(linkedSetOf()) { it.kind() }
    if (ownKey != null && event.phase == HARDWARE_UP) return kinds
    if (event.shift) kinds += KeyModifier.SHIFT
    if (event.alt) kinds += KeyModifier.ALT
    if (event.ctrl) kinds += KeyModifier.CTRL
    if (event.meta) kinds += KeyModifier.META
    return kinds
}

private fun pack(
    machine: InputMachine,
    event: HardwareEvent,
    source: InputSource,
    deliver: List<Resolved>,
    consumed: Boolean,
    modifiers: Set<KeyModifier> = effectiveModifiers(machine.modifiers, event, sideKey(event.keyCode)),
): HardwareStep = HardwareStep(
    machine = machine,
    deliver = deliver,
    shiftDown = KeyModifier.SHIFT in modifiers,
    altDown = KeyModifier.ALT in modifiers,
    ctrlDown = KeyModifier.CTRL in modifiers,
    metaDown = KeyModifier.META in modifiers,
    consumed = consumed,
    source = source,
    language = event.language,
    heldModifiers = modifiers,
    deviceId = event.deviceId,
    scanCode = event.scanCode,
    keyCode = event.keyCode,
    unicodeChar = event.unicodeChar,
    characters = event.characters,
)

private const val ECHO_LIMIT = 16
