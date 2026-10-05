package com.tepmex.cornegame.domain

/**
 * Раскладки Cornedeon заполнены в SPEC.md и здесь не расширяются.
 * Легенда буквы — верхний регистр, как на колпачке. Базовый слой печатает строчную.
 * Знаки препинания печатаются тем символом, который стоит в ячейке.
 */

enum class Language {
    EN,
    RU,
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

data class KeyCell(
    val legend: String,
    val action: TrainerAction?,
    val finger: Finger,
)

data class Board(
    val language: Language,
    val leftRows: List<List<KeyCell>>,
    val rightRows: List<List<KeyCell>>,
    val leftThumbs: List<KeyCell>,
    val rightThumbs: List<KeyCell>,
) {
    fun keyCells(): List<KeyCell> =
        leftRows.flatten() + rightRows.flatten() + leftThumbs + rightThumbs

    /** Жёлтая подсветка. Пробел подсвечивает только ␣, хотя Enter тоже его вводит. */
    fun highlightCell(expected: Char): KeyCell? {
        if (expected == ' ') return rightThumbs.find { it.legend == "␣" }
        return (leftRows.flatten() + rightRows.flatten()).find { cell ->
            (cell.action as? TrainerAction.Character)?.value == expected
        }
    }
}

/**
 * Колонки считаются слева направо внутри половинки.
 * Y/H/N — левая половинка, колонка 6, но по заданному QWERTY-распределению
 * это правый указательный. Крайние правые знаки `[ ] ' \ - =` в списке пальцев
 * не названы: они продолжают колонки мизинца.
 */
fun fingerFor(side: Side, column: Int, thumb: Boolean): Finger {
    if (thumb) {
        return if (side == Side.LEFT) Finger.LEFT_THUMB else Finger.RIGHT_THUMB
    }
    return when (side) {
        Side.LEFT -> when (column) {
            0 -> Finger.LEFT_PINKY
            1 -> Finger.LEFT_RING
            2 -> Finger.LEFT_MIDDLE
            3, 4 -> Finger.LEFT_INDEX
            5 -> Finger.RIGHT_INDEX
            else -> error("Нет колонки $column")
        }
        Side.RIGHT -> when (column) {
            0 -> Finger.RIGHT_INDEX
            1 -> Finger.RIGHT_MIDDLE
            2 -> Finger.RIGHT_RING
            else -> Finger.RIGHT_PINKY
        }
    }
}

fun board(language: Language): Board = when (language) {
    Language.EN -> enBoard
    Language.RU -> ruBoard
}

/** Буквы базового слоя — из них собираются слова. Пробел и знаки в словарь не входят. */
fun baseLetters(language: Language): Set<Char> =
    board(language).keyCells().mapNotNull { cell ->
        (cell.action as? TrainerAction.Character)?.value?.takeIf { it.isLetter() }
    }.toSet()

private fun letterRow(spec: String, side: Side): List<KeyCell> {
    require(spec.length == 6) { spec }
    return spec.mapIndexed { column, glyph ->
        KeyCell(
            legend = glyph.toString(),
            action = TrainerAction.Character(glyph.lowercaseChar()),
            finger = fingerFor(side, column, thumb = false),
        )
    }
}

private fun thumb(legend: String, action: TrainerAction?, side: Side): KeyCell =
    KeyCell(
        legend = legend,
        action = action,
        finger = fingerFor(side, column = 0, thumb = true),
    )

private val thumbsLeft = listOf(
    thumb("Tab", null, Side.LEFT),
    thumb("Shift", null, Side.LEFT),
    thumb("Ctrl", null, Side.LEFT),
)

private val thumbsRight = listOf(
    thumb("␣", TrainerAction.Character(' '), Side.RIGHT),
    thumb("Enter", TrainerAction.Character(' '), Side.RIGHT),
    thumb("Backspace", TrainerAction.Backspace, Side.RIGHT),
)

private val enBoard = Board(
    language = Language.EN,
    leftRows = listOf(
        letterRow("QWERTY", Side.LEFT),
        letterRow("ASDFGH", Side.LEFT),
        letterRow("ZXCVBN", Side.LEFT),
    ),
    rightRows = listOf(
        letterRow("UIOP[]", Side.RIGHT),
        letterRow("JKL;'\\", Side.RIGHT),
        letterRow("M,./-=", Side.RIGHT),
    ),
    leftThumbs = thumbsLeft,
    rightThumbs = thumbsRight,
)

private val ruBoard = Board(
    language = Language.RU,
    leftRows = listOf(
        letterRow("ЙЦУКЕН", Side.LEFT),
        letterRow("ФЫВАПО", Side.LEFT),
        letterRow("ЯЧСМИТ", Side.LEFT),
    ),
    rightRows = listOf(
        letterRow("ГШЩЗХЪ", Side.RIGHT),
        letterRow("РЛДЖЭ/", Side.RIGHT),
        letterRow("ЬБЮ.,;", Side.RIGHT),
    ),
    leftThumbs = thumbsLeft,
    rightThumbs = thumbsRight,
)
