package com.tepmex.assumptions.domain

enum class Sign { Negative, Positive }

enum class Quadrant(
    val before: Sign,
    val after: Sign,
    val colorArgb: Long,
    val title: String,
) {
    NegativeNegative(
        before = Sign.Negative,
        after = Sign.Negative,
        colorArgb = 0xFFE23B3B,
        title = "Отрицательно → отрицательно",
    ),
    NegativePositive(
        before = Sign.Negative,
        after = Sign.Positive,
        colorArgb = 0xFF1B5E20,
        title = "Отрицательно → положительно",
    ),
    PositiveNegative(
        before = Sign.Positive,
        after = Sign.Negative,
        colorArgb = 0xFF9E9E9E,
        title = "Положительно → отрицательно",
    ),
    PositivePositive(
        before = Sign.Positive,
        after = Sign.Positive,
        colorArgb = 0xFF2FBF4A,
        title = "Положительно → положительно",
    ),
}

fun signOf(score: Int): Sign? = when {
    score > 0 -> Sign.Positive
    score < 0 -> Sign.Negative
    else -> null
}

fun quadrantOf(before: Int, after: Int?): Quadrant? {
    val afterScore = after ?: return null
    val beforeSign = signOf(before) ?: return null
    val afterSign = signOf(afterScore) ?: return null
    return Quadrant.entries.first { it.before == beforeSign && it.after == afterSign }
}

data class AssumptionReport(
    val counts: Map<Quadrant, Int>,
    val pending: Int,
    val neutral: Int,
) {
    val matrixTotal: Int get() = counts.values.sum()

    companion object {
        fun from(items: List<Expectation>): AssumptionReport {
            val counts = Quadrant.entries.associateWith { 0 }.toMutableMap()
            var pending = 0
            var neutral = 0
            for (item in items) {
                when {
                    item.afterScore == null -> pending += 1
                    else -> {
                        val quadrant = quadrantOf(item.beforeScore, item.afterScore)
                        if (quadrant == null) {
                            neutral += 1
                        } else {
                            counts[quadrant] = counts.getValue(quadrant) + 1
                        }
                    }
                }
            }
            return AssumptionReport(counts, pending, neutral)
        }
    }
}
