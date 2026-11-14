package com.tepmex.cornegame.domain

/**
 * Шаг упражнения. Текстовый режим по-прежнему живёт в [TypingSession]:
 * строка и WPM не ломаются. Служебные клавиши и сочетания идут отдельной сессией
 * по [ExerciseStep], потому что у них нет символа.
 */
sealed interface ExerciseStep {
    val title: String
    val hint: String
    val recommendedLayer: Latch
    val keyIds: Set<Int>
    val hintIds: Set<Int>
    val interceptNote: String?

    data class Text(val expected: String) : ExerciseStep {
        override val title: String = expected
        override val hint: String = "Символ"
        override val recommendedLayer: Latch = Latch.NONE
        override val keyIds: Set<Int> = emptySet()
        override val hintIds: Set<Int> = emptySet()
        override val interceptNote: String? = null
    }

    data class KeyPress(
        val keyCode: Int,
        override val title: String,
        override val hint: String,
        override val recommendedLayer: Latch,
        override val keyIds: Set<Int>,
        override val hintIds: Set<Int>,
        override val interceptNote: String? = null,
    ) : ExerciseStep

    data class Shortcut(
        val keyCode: Int,
        val modifiers: Set<KeyModifier>,
        override val title: String,
        override val hint: String,
        override val recommendedLayer: Latch,
        override val keyIds: Set<Int>,
        override val hintIds: Set<Int>,
        override val interceptNote: String? = null,
    ) : ExerciseStep
}

enum class DrillKind {
    TEXT,
    SERVICE,
    SHORTCUT,
}

data class ActionStats(
    val perMinute: Double,
    val accuracyPercent: Double,
    val errors: Int,
    val elapsedMs: Long,
)

/**
 * Сессия служебных клавиш и сочетаний.
 * Верный Shortcut фиксируется только по нажатию основной клавиши при уже удержанных модификаторах.
 * Повтор и отпускание модификатора сюда не приходят: их отсекает разбор событий.
 * Совпадение слоя L1/L2 здесь не доказывается: [recommendedLayer] — подсказка раскладки.
 */
data class ActionSession(
    val kind: DrillKind,
    val steps: List<ExerciseStep>,
    val index: Int = 0,
    val errors: Int = 0,
    val correctPresses: Int = 0,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val lastAccepted: Boolean? = null,
) {
    val completed: Boolean get() = steps.isNotEmpty() && index >= steps.size
    val current: ExerciseStep? get() = steps.getOrNull(index)

    fun stats(nowMs: Long): ActionStats {
        val end = finishedAtMs ?: if (startedAtMs != null) nowMs else null
        val elapsed = if (startedAtMs != null && end != null) (end - startedAtMs).coerceAtLeast(0L) else 0L
        val minutes = elapsed / 60_000.0
        val pace = if (minutes > 0.0) correctPresses / minutes else 0.0
        val attempts = correctPresses + errors
        val accuracy = if (attempts == 0) 100.0 else 100.0 * correctPresses / attempts
        return ActionStats(pace, accuracy, errors, elapsed)
    }

    fun apply(resolved: Resolved, nowMs: Long): Pair<ActionSession, InputEffect> {
        if (completed) return this to InputEffect(mistake = false, completed = true)
        if (isModifierNoise(resolved.action)) {
            return this to InputEffect(mistake = false, completed = false)
        }
        val step = steps[index]
        if (matches(step, resolved)) {
            val started = startedAtMs ?: nowMs
            val nextIndex = index + 1
            val done = nextIndex >= steps.size
            val next = copy(
                index = nextIndex,
                correctPresses = correctPresses + 1,
                startedAtMs = started,
                finishedAtMs = if (done) nowMs else null,
                lastAccepted = true,
            )
            return next to InputEffect(mistake = false, completed = done)
        }
        val started = startedAtMs ?: nowMs
        val next = copy(
            startedAtMs = started,
            errors = errors + 1,
            lastAccepted = false,
        )
        return next to InputEffect(mistake = true, completed = false)
    }
}

fun matches(step: ExerciseStep, resolved: Resolved): Boolean = when (step) {
    is ExerciseStep.Text -> {
        val typed = resolved.action as? TrainerAction.Character
        typed != null && typed.value.toString() == step.expected && resolved.modifiers.isEmpty()
    }
    is ExerciseStep.KeyPress ->
        resolved.action !is TrainerAction.Shortcut &&
            resolved.modifiers.isEmpty() &&
            keyCodeOf(resolved) == step.keyCode
    is ExerciseStep.Shortcut -> {
        val shortcut = resolved.action as? TrainerAction.Shortcut
        shortcut != null && shortcut.keyCode == step.keyCode && shortcut.modifiers == step.modifiers
    }
}

fun keyCodeOf(resolved: Resolved): Int = when (val action = resolved.action) {
    is TrainerAction.KeyPress -> action.keyCode
    is TrainerAction.Shortcut -> action.keyCode
    TrainerAction.Backspace -> AndroidKeyCodes.DEL
    else -> resolved.keyCode
}

private fun isModifierNoise(action: TrainerAction): Boolean = when (action) {
    TrainerAction.Shift,
    TrainerAction.Alt,
    TrainerAction.Ctrl,
    TrainerAction.Meta,
    TrainerAction.Lower,
    TrainerAction.Upper,
    TrainerAction.AltShift,
    -> true
    else -> false
}

fun serviceExercises(): List<ExerciseStep.KeyPress> =
    SERVICE_CANDIDATES.mapNotNull { candidate ->
        val placed = placementsFor(candidate.keyCode)
        if (placed.isEmpty()) return@mapNotNull null
        val layer = placed.minBy { layerRank(it.layer) }.layer
        val ids = placed.filter { it.layer == layer }.map { it.id }.toSet()
        ExerciseStep.KeyPress(
            keyCode = candidate.keyCode,
            title = candidate.label,
            hint = keyHint(candidate.label, layer, intercept = null),
            recommendedLayer = layer,
            keyIds = ids,
            hintIds = layerHint(layer),
        )
    }

fun shortcutExercises(): List<ExerciseStep.Shortcut> =
    SHORTCUT_CANDIDATES.mapNotNull { candidate ->
        val placed = placementsFor(candidate.keyCode)
        if (placed.isEmpty()) return@mapNotNull null
        val layer = placed.minBy { layerRank(it.layer) }.layer
        val keyIds = placed.filter { it.layer == layer }.map { it.id }.toSet()
        val modifierIds = candidate.modifiers.flatMap { modifierKeyIds(it) }.toSet()
        ExerciseStep.Shortcut(
            keyCode = candidate.keyCode,
            modifiers = candidate.modifiers,
            title = candidate.label,
            hint = keyHint(candidate.label, layer, candidate.interceptNote),
            recommendedLayer = layer,
            keyIds = keyIds + modifierIds,
            hintIds = layerHint(layer),
            interceptNote = candidate.interceptNote,
        )
    }

fun ExerciseStep.toFocus(): DrillFocus {
    val actions = when (this) {
        is ExerciseStep.KeyPress -> keyIds
            .filter { id -> id != LOWER_KEY && id != UPPER_KEY && id !in SHIFT_KEY_IDS && id !in modifierKeyIds(KeyModifier.CTRL) && id !in modifierKeyIds(KeyModifier.ALT) && id !in modifierKeyIds(KeyModifier.META) }
            .associateWith { TrainerAction.KeyPress(keyCode) }
        is ExerciseStep.Shortcut -> emptyMap()
        is ExerciseStep.Text -> emptyMap()
    }
    return DrillFocus(
        layer = recommendedLayer,
        press = keyIds,
        hint = hintIds,
        caption = hint,
        keyActions = actions,
    )
}

/**
 * Текст после верной команды. Маска слоёв остаётся неизвестной, пока прошивка её не прислала.
 * Рекомендация слоя не называется состоянием клавиатуры.
 */
fun acceptanceLine(layer: Latch, firmware: FirmwareState): String {
    val base = when (layer) {
        Latch.LOWER -> "Команда получена. L1 — рекомендуемый слой по Vial, прошивка удержание не подтвердила."
        Latch.UPPER -> "Команда получена. L2 — рекомендуемый слой по Vial, прошивка удержание не подтвердила."
        else -> "Команда получена."
    }
    val mask = firmware.activeLayersMask
    if (mask == null) return base
    return "$base Маска от прошивки: 0x${mask.toString(16)}."
}

private fun keyHint(label: String, layer: Latch, intercept: String?): String {
    val layerText = when (layer) {
        Latch.LOWER -> " · рекомендуемый слой L1 (не показание прошивки)"
        Latch.UPPER -> " · рекомендуемый слой L2 (не показание прошивки)"
        else -> ""
    }
    val note = if (intercept == null) "" else " · $intercept"
    return "$label$layerText$note"
}

private fun layerHint(layer: Latch): Set<Int> = when (layer) {
    Latch.LOWER -> setOf(LOWER_KEY)
    Latch.UPPER -> setOf(UPPER_KEY)
    else -> emptySet()
}

private fun layerRank(layer: Latch): Int = when (layer) {
    Latch.NONE -> 0
    Latch.LOWER -> 1
    Latch.UPPER -> 2
    Latch.SHIFT -> 3
    Latch.ADJUST -> 4
}

private fun modifierKeyIds(kind: KeyModifier): Set<Int> = when (kind) {
    KeyModifier.SHIFT -> SHIFT_KEY_IDS
    KeyModifier.CTRL -> placementsFor(AndroidKeyCodes.CTRL_LEFT).map { it.id }.toSet() +
        placementsFor(AndroidKeyCodes.CTRL_RIGHT).map { it.id }.toSet()
    KeyModifier.ALT -> placementsFor(AndroidKeyCodes.ALT_LEFT).map { it.id }.toSet() +
        placementsFor(AndroidKeyCodes.ALT_RIGHT).map { it.id }.toSet()
    KeyModifier.META -> placementsFor(AndroidKeyCodes.META_LEFT).map { it.id }.toSet() +
        placementsFor(AndroidKeyCodes.META_RIGHT).map { it.id }.toSet()
}

private data class KeyCandidate(val keyCode: Int, val label: String)

private val SERVICE_CANDIDATES: List<KeyCandidate> = buildList {
    AndroidKeyCodes.F_KEYS.forEachIndexed { index, code -> add(KeyCandidate(code, "F${index + 1}")) }
    add(KeyCandidate(AndroidKeyCodes.ESCAPE, "Esc"))
    add(KeyCandidate(AndroidKeyCodes.TAB, "Tab"))
    add(KeyCandidate(AndroidKeyCodes.ENTER, "Enter"))
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_ENTER, "Num Enter"))
    add(KeyCandidate(AndroidKeyCodes.DEL, "Backspace"))
    add(KeyCandidate(AndroidKeyCodes.FORWARD_DEL, "Delete"))
    add(KeyCandidate(AndroidKeyCodes.MOVE_HOME, "Home"))
    add(KeyCandidate(AndroidKeyCodes.MOVE_END, "End"))
    add(KeyCandidate(AndroidKeyCodes.PAGE_UP, "Page Up"))
    add(KeyCandidate(AndroidKeyCodes.PAGE_DOWN, "Page Down"))
    add(KeyCandidate(AndroidKeyCodes.DPAD_LEFT, "←"))
    add(KeyCandidate(AndroidKeyCodes.DPAD_RIGHT, "→"))
    add(KeyCandidate(AndroidKeyCodes.DPAD_UP, "↑"))
    add(KeyCandidate(AndroidKeyCodes.DPAD_DOWN, "↓"))
    add(KeyCandidate(AndroidKeyCodes.INSERT, "Insert"))
    for (n in 0..9) {
        val code = AndroidKeyCodes.NUMPAD_0 + n
        add(KeyCandidate(code, "Num $n"))
    }
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_DOT, "Num ."))
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_ADD, "Num +"))
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_SUBTRACT, "Num −"))
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_MULTIPLY, "Num *"))
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_DIVIDE, "Num /"))
    add(KeyCandidate(AndroidKeyCodes.NUMPAD_EQUALS, "Num ="))
    add(KeyCandidate(AndroidKeyCodes.CAPS_LOCK, "Caps Lock"))
    add(KeyCandidate(AndroidKeyCodes.SYSRQ, "Print Screen"))
    add(KeyCandidate(AndroidKeyCodes.BREAK, "Pause"))
    add(KeyCandidate(AndroidKeyCodes.NUM_LOCK, "Num Lock"))
    add(KeyCandidate(AndroidKeyCodes.MENU, "Menu"))
    add(KeyCandidate(AndroidKeyCodes.CALCULATOR, "Calc"))
}

private data class ShortcutCandidate(
    val keyCode: Int,
    val modifiers: Set<KeyModifier>,
    val label: String,
    val interceptNote: String?,
)

private const val SYSTEM_MAY_TAKE =
    "система может забрать сочетание раньше приложения"

private val SHORTCUT_CANDIDATES = listOf(
    ShortcutCandidate(AndroidKeyCodes.C, setOf(KeyModifier.CTRL), "Ctrl+C", SYSTEM_MAY_TAKE),
    ShortcutCandidate(AndroidKeyCodes.V, setOf(KeyModifier.CTRL), "Ctrl+V", SYSTEM_MAY_TAKE),
    ShortcutCandidate(AndroidKeyCodes.X, setOf(KeyModifier.CTRL), "Ctrl+X", SYSTEM_MAY_TAKE),
    ShortcutCandidate(AndroidKeyCodes.A, setOf(KeyModifier.CTRL), "Ctrl+A", SYSTEM_MAY_TAKE),
    ShortcutCandidate(AndroidKeyCodes.Z, setOf(KeyModifier.CTRL), "Ctrl+Z", SYSTEM_MAY_TAKE),
    ShortcutCandidate(AndroidKeyCodes.S, setOf(KeyModifier.CTRL), "Ctrl+S", SYSTEM_MAY_TAKE),
    ShortcutCandidate(
        AndroidKeyCodes.Z,
        setOf(KeyModifier.CTRL, KeyModifier.SHIFT),
        "Ctrl+Shift+Z",
        SYSTEM_MAY_TAKE,
    ),
    ShortcutCandidate(AndroidKeyCodes.F10, setOf(KeyModifier.SHIFT), "Shift+F10", null),
    ShortcutCandidate(AndroidKeyCodes.F5, setOf(KeyModifier.CTRL), "Ctrl+F5", SYSTEM_MAY_TAKE),
    ShortcutCandidate(AndroidKeyCodes.ENTER, setOf(KeyModifier.ALT), "Alt+Enter", SYSTEM_MAY_TAKE),
)
