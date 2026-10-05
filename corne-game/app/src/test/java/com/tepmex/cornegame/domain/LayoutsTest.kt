package com.tepmex.cornegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutsTest {
    @Test
    fun englishRowsMatchSpec() {
        val board = board(Language.EN)
        assertEquals(listOf("Q", "W", "E", "R", "T", "Y"), legends(board.leftRows[0]))
        assertEquals(listOf("A", "S", "D", "F", "G", "H"), legends(board.leftRows[1]))
        assertEquals(listOf("Z", "X", "C", "V", "B", "N"), legends(board.leftRows[2]))
        assertEquals(listOf("U", "I", "O", "P", "[", "]"), legends(board.rightRows[0]))
        assertEquals(listOf("J", "K", "L", ";", "'", "\\"), legends(board.rightRows[1]))
        assertEquals(listOf("M", ",", ".", "/", "-", "="), legends(board.rightRows[2]))
        assertThumbs(board)
        assertEquals('q', output(board.leftRows[0][0]))
        assertEquals('\\', output(board.rightRows[1][5]))
        assertEquals('[', output(board.rightRows[0][4]))
    }

    @Test
    fun russianRowsMatchSpec() {
        val board = board(Language.RU)
        assertEquals(listOf("Й", "Ц", "У", "К", "Е", "Н"), legends(board.leftRows[0]))
        assertEquals(listOf("Ф", "Ы", "В", "А", "П", "О"), legends(board.leftRows[1]))
        assertEquals(listOf("Я", "Ч", "С", "М", "И", "Т"), legends(board.leftRows[2]))
        assertEquals(listOf("Г", "Ш", "Щ", "З", "Х", "Ъ"), legends(board.rightRows[0]))
        assertEquals(listOf("Р", "Л", "Д", "Ж", "Э", "/"), legends(board.rightRows[1]))
        assertEquals(listOf("Ь", "Б", "Ю", ".", ",", ";"), legends(board.rightRows[2]))
        assertThumbs(board)
        assertEquals('й', output(board.leftRows[0][0]))
        assertEquals('ъ', output(board.rightRows[0][5]))
    }

    @Test
    fun eachHalfHasThreeRowsOfSixAndSixThumbs() {
        listOf(Language.EN, Language.RU).forEach { language ->
            val board = board(language)
            assertEquals(3, board.leftRows.size)
            assertEquals(3, board.rightRows.size)
            board.leftRows.forEach { assertEquals(6, it.size) }
            board.rightRows.forEach { assertEquals(6, it.size) }
            assertEquals(42, board.keyCells().size)
        }
    }

    @Test
    fun fingerZonesFollowQwertyColumns() {
        val en = board(Language.EN)
        assertEquals(Finger.LEFT_PINKY, cell(en, "Q").finger)
        assertEquals(Finger.LEFT_PINKY, cell(en, "A").finger)
        assertEquals(Finger.LEFT_PINKY, cell(en, "Z").finger)
        assertEquals(Finger.LEFT_RING, cell(en, "W").finger)
        assertEquals(Finger.LEFT_RING, cell(en, "S").finger)
        assertEquals(Finger.LEFT_RING, cell(en, "X").finger)
        assertEquals(Finger.LEFT_MIDDLE, cell(en, "E").finger)
        assertEquals(Finger.LEFT_MIDDLE, cell(en, "D").finger)
        assertEquals(Finger.LEFT_MIDDLE, cell(en, "C").finger)
        assertEquals(Finger.LEFT_INDEX, cell(en, "R").finger)
        assertEquals(Finger.LEFT_INDEX, cell(en, "T").finger)
        assertEquals(Finger.LEFT_INDEX, cell(en, "F").finger)
        assertEquals(Finger.LEFT_INDEX, cell(en, "G").finger)
        assertEquals(Finger.LEFT_INDEX, cell(en, "V").finger)
        assertEquals(Finger.LEFT_INDEX, cell(en, "B").finger)
        assertEquals(Finger.RIGHT_INDEX, cell(en, "Y").finger)
        assertEquals(Finger.RIGHT_INDEX, cell(en, "U").finger)
        assertEquals(Finger.RIGHT_INDEX, cell(en, "H").finger)
        assertEquals(Finger.RIGHT_INDEX, cell(en, "J").finger)
        assertEquals(Finger.RIGHT_INDEX, cell(en, "N").finger)
        assertEquals(Finger.RIGHT_INDEX, cell(en, "M").finger)
        assertEquals(Finger.RIGHT_MIDDLE, cell(en, "I").finger)
        assertEquals(Finger.RIGHT_MIDDLE, cell(en, "K").finger)
        assertEquals(Finger.RIGHT_MIDDLE, cell(en, ",").finger)
        assertEquals(Finger.RIGHT_RING, cell(en, "O").finger)
        assertEquals(Finger.RIGHT_RING, cell(en, "L").finger)
        assertEquals(Finger.RIGHT_RING, cell(en, ".").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "P").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, ";").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "/").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "[").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "]").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "'").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "\\").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "-").finger)
        assertEquals(Finger.RIGHT_PINKY, cell(en, "=").finger)
        assertEquals(Finger.LEFT_THUMB, cell(en, "Tab").finger)
        assertEquals(Finger.RIGHT_THUMB, cell(en, "␣").finger)
        assertEquals(Finger.LEFT_PINKY, cell(board(Language.RU), "Й").finger)
    }

    @Test
    fun spaceHighlightIsTheSpaceThumbNotEnter() {
        val board = board(Language.EN)
        assertEquals("␣", board.highlightCell(' ')?.legend)
        assertEquals(' ', output(cell(board, "Enter")))
        assertEquals(TrainerAction.Backspace, cell(board, "Backspace").action)
        assertNull(cell(board, "Shift").action)
        assertNull(cell(board, "Tab").action)
        assertNull(cell(board, "Ctrl").action)
    }

    private fun assertThumbs(board: Board) {
        assertEquals(listOf("Tab", "Shift", "Ctrl"), board.leftThumbs.map { it.legend })
        assertEquals(listOf("␣", "Enter", "Backspace"), board.rightThumbs.map { it.legend })
    }

    private fun legends(row: List<KeyCell>): List<String> = row.map { it.legend }

    private fun cell(board: Board, legend: String): KeyCell =
        board.keyCells().first { it.legend == legend }

    private fun output(cell: KeyCell): Char =
        (cell.action as TrainerAction.Character).value
}
