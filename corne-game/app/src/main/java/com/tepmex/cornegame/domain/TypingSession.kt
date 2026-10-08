package com.tepmex.cornegame.domain

sealed class TrainerAction {
    data class Character(val value: Char) : TrainerAction()
    data object Backspace : TrainerAction()
    data object Shift : TrainerAction()
    data object Lower : TrainerAction()
    data object Upper : TrainerAction()
    data object Alt : TrainerAction()

    /** Alt удерживается, Shift отпущен: аппаратное сочетание смены языка. */
    data object AltShift : TrainerAction()
    data object Unexpected : TrainerAction()
}

data class InputEffect(
    val mistake: Boolean,
    val completed: Boolean,
)

data class LiveStats(
    val wpm: Double,
    val accuracyPercent: Double,
    val errors: Int,
    val elapsedMs: Long,
)

data class Substitution(
    val actual: Char,
    val count: Int,
)

data class MissBucket(
    val expected: Char,
    val total: Int,
    val pressedInstead: List<Substitution>,
)

/**
 * Одна сессия. Состояние неизменяемое: ViewModel держит экземпляр и подменяет его,
 * поэтому поворот экрана не теряет индекс и карту промахов.
 *
 * WPM = (текущий индекс / 5) / минуты с первого символа.
 * Индекс — это верно набранные знаки, которые ещё не стёрты.
 */
data class TypingSession(
    val language: Language,
    val target: String,
    val index: Int = 0,
    val errors: Int = 0,
    val correctPresses: Int = 0,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val misses: Map<Char, Map<Char, Int>> = emptyMap(),
    val latch: Latch = Latch.NONE,
    val altArmed: Boolean = false,
) {
    val completed: Boolean get() = target.isNotEmpty() && index >= target.length

    val nextChar: Char? get() = target.getOrNull(index)

    /** Язык легенд в этой позиции. ⇄ в уже набранной части переключает его. */
    val typingLanguage: Language
        get() {
            var current = language
            val upTo = index.coerceIn(0, target.length)
            for (cursor in 0 until upTo) {
                if (target[cursor] == LANGUAGE_SWITCH) current = current.other()
            }
            return current
        }

    fun stats(nowMs: Long): LiveStats {
        val end = finishedAtMs ?: if (startedAtMs != null) nowMs else null
        val elapsed = if (startedAtMs != null && end != null) {
            (end - startedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val minutes = elapsed / 60_000.0
        val wpm = if (minutes > 0.0) (index / 5.0) / minutes else 0.0
        val attempts = correctPresses + errors
        val accuracy = if (attempts == 0) 100.0 else 100.0 * correctPresses / attempts
        return LiveStats(
            wpm = wpm,
            accuracyPercent = accuracy,
            errors = errors,
            elapsedMs = elapsed,
        )
    }

    fun apply(action: TrainerAction, nowMs: Long): Pair<TypingSession, InputEffect> {
        if (completed) return this to InputEffect(mistake = false, completed = true)
        return when (action) {
            TrainerAction.Backspace -> onBackspace()
            TrainerAction.Shift -> onShift(nowMs)
            TrainerAction.Lower -> onLayer(Latch.LOWER, nowMs)
            TrainerAction.Upper -> onLayer(Latch.UPPER, nowMs)
            TrainerAction.Alt -> onAlt(nowMs)
            TrainerAction.AltShift -> onAltShift(nowMs)
            TrainerAction.Unexpected -> fail(typed = '·', nowMs)
            is TrainerAction.Character -> onCharacter(action.value, nowMs)
        }
    }

    private fun onBackspace(): Pair<TypingSession, InputEffect> {
        val idle = InputEffect(mistake = false, completed = false)
        if (latch != Latch.NONE || altArmed) {
            return copy(latch = Latch.NONE, altArmed = false) to idle
        }
        if (index == 0) return this to idle
        return copy(index = index - 1) to idle
    }

    private fun onShift(nowMs: Long): Pair<TypingSession, InputEffect> {
        if (nextChar == LANGUAGE_SWITCH && altArmed) return commitStep(nowMs)
        if (latch == Latch.SHIFT) {
            return copy(latch = Latch.NONE) to InputEffect(mistake = false, completed = false)
        }
        return arm(Latch.SHIFT, nowMs, typedOnMiss = '⇧')
    }

    private fun onLayer(which: Latch, nowMs: Long): Pair<TypingSession, InputEffect> {
        if (latch == which) {
            return copy(latch = Latch.NONE) to InputEffect(mistake = false, completed = false)
        }
        val mark = if (which == Latch.LOWER) '₁' else '₂'
        return arm(which, nowMs, typedOnMiss = mark)
    }

    private fun arm(which: Latch, nowMs: Long, typedOnMiss: Char): Pair<TypingSession, InputEffect> {
        val expected = nextChar
        val stroke = expected?.takeIf { it != LANGUAGE_SWITCH }?.let { strokeFor(it, typingLanguage) }
        if (stroke?.latch == which && latch == Latch.NONE) {
            return copy(latch = which, altArmed = false) to InputEffect(mistake = false, completed = false)
        }
        return fail(typedOnMiss, nowMs)
    }

    private fun onAlt(nowMs: Long): Pair<TypingSession, InputEffect> {
        if (nextChar == LANGUAGE_SWITCH && !altArmed && latch == Latch.NONE) {
            return copy(altArmed = true) to InputEffect(mistake = false, completed = false)
        }
        return fail(typed = '⎇', nowMs)
    }

    private fun onAltShift(nowMs: Long): Pair<TypingSession, InputEffect> {
        if (nextChar == LANGUAGE_SWITCH) return commitStep(nowMs)
        return fail(typed = LANGUAGE_SWITCH, nowMs)
    }

    private fun onCharacter(typed: Char, nowMs: Long): Pair<TypingSession, InputEffect> {
        val expected = target[index]
        if (typed == expected && expected != LANGUAGE_SWITCH) return commitStep(nowMs)
        return fail(typed, nowMs)
    }

    private fun commitStep(nowMs: Long): Pair<TypingSession, InputEffect> {
        val started = startedAtMs ?: nowMs
        val nextIndex = index + 1
        val done = nextIndex >= target.length
        val next = copy(
            index = nextIndex,
            correctPresses = correctPresses + 1,
            startedAtMs = started,
            finishedAtMs = if (done) nowMs else null,
            latch = Latch.NONE,
            altArmed = false,
        )
        return next to InputEffect(mistake = false, completed = done)
    }

    private fun fail(typed: Char, nowMs: Long): Pair<TypingSession, InputEffect> {
        val started = startedAtMs ?: nowMs
        val expected = target[index]
        val previous = misses[expected].orEmpty()
        val count = (previous[typed] ?: 0) + 1
        val next = copy(
            startedAtMs = started,
            errors = errors + 1,
            misses = misses + (expected to (previous + (typed to count))),
            latch = Latch.NONE,
            altArmed = false,
        )
        return next to InputEffect(mistake = true, completed = false)
    }
}

/** Топ символов, которые промахнулись, и чем их заменяли. */
fun topMisses(misses: Map<Char, Map<Char, Int>>, limit: Int = 10): List<MissBucket> =
    misses.map { (expected, subs) ->
        val sorted = subs.entries.sortedWith(
            compareByDescending<Map.Entry<Char, Int>> { it.value }.thenBy { it.key },
        )
        MissBucket(
            expected = expected,
            total = sorted.sumOf { it.value },
            pressedInstead = sorted.map { Substitution(it.key, it.value) },
        )
    }.sortedWith(
        compareByDescending<MissBucket> { it.total }.thenBy { it.expected },
    ).take(limit)
