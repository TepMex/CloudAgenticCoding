package com.tepmex.cornegame.domain

sealed class TrainerAction {
    data class Character(val value: Char) : TrainerAction()
    data object Backspace : TrainerAction()
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
) {
    val completed: Boolean get() = target.isNotEmpty() && index >= target.length

    val nextChar: Char? get() = target.getOrNull(index)

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
            TrainerAction.Backspace -> {
                if (index == 0) {
                    this to InputEffect(mistake = false, completed = false)
                } else {
                    copy(index = index - 1) to InputEffect(mistake = false, completed = false)
                }
            }
            is TrainerAction.Character -> onCharacter(action.value, nowMs)
        }
    }

    private fun onCharacter(typed: Char, nowMs: Long): Pair<TypingSession, InputEffect> {
        val started = startedAtMs ?: nowMs
        val base = if (startedAtMs == null) copy(startedAtMs = started) else this
        val expected = target[index]
        if (typed == expected) {
            val nextIndex = index + 1
            val done = nextIndex >= target.length
            val next = base.copy(
                index = nextIndex,
                correctPresses = correctPresses + 1,
                finishedAtMs = if (done) nowMs else null,
            )
            return next to InputEffect(mistake = false, completed = done)
        }
        val previous = misses[expected].orEmpty()
        val count = (previous[typed] ?: 0) + 1
        val next = base.copy(
            errors = errors + 1,
            misses = misses + (expected to (previous + (typed to count))),
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
