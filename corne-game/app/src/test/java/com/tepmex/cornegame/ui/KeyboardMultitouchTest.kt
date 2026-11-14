package com.tepmex.cornegame.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tepmex.cornegame.domain.BoardLive
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.TypingSession
import com.tepmex.cornegame.domain.keyboardModel
import com.tepmex.cornegame.ui.theme.CorneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Жесты идут через настоящий pointer input Compose, не через прямой вызов ViewModel.
 * Два pointer id удерживаются в одном жесте: L1 не отпускается, пока второй палец жмёт W.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w1000dp-h600dp-mdpi")
class KeyboardMultitouchTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun secondPointerTypesParenWhileL1StaysDown() {
        val session = TypingSession(Language.RU, "(")
        var live by mutableStateOf(BoardLive())
        val taps = mutableListOf<TrainerAction>()
        val contacts = mutableListOf<Pair<TrainerAction, Boolean>>()
        rule.setContent {
            CorneTheme {
                CorneKeyboard(
                    model = keyboardModel(session, live),
                    highlightEnabled = true,
                    fingerColors = false,
                    keyScale = 1f,
                    errorNonce = 0,
                    lastWrong = null,
                    onAction = { _, action -> taps += action },
                    onContact = { action, down ->
                        contacts += action to down
                        live = when (action) {
                            TrainerAction.Lower -> live.copy(screenLower = down)
                            TrainerAction.Upper -> live.copy(screenUpper = down)
                            else -> live
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.waitForIdle()
        val keyboard = rule.onNodeWithContentDescription("Клавиатура 48")
        val l1 = centerOf("Клавиша L1", keyboard)
        val w = centerOf("Клавиша ц", keyboard)

        // События уходят на экран в конце блока. Следующий блок продолжает тот же жест:
        // палец 0 остаётся на L1, пока между блоками проходит кадр и W становится «(».
        keyboard.performTouchInput {
            down(pointerId = 0, position = l1)
        }
        rule.waitForIdle()
        assertEquals(listOf(TrainerAction.Lower to true), contacts)
        assertTrue(live.screenLower)
        rule.onNodeWithContentDescription("Клавиша (", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("удерживается", substring = true, useUnmergedTree = true).assertExists()

        keyboard.performTouchInput {
            down(pointerId = 1, position = w)
            up(pointerId = 1)
        }
        rule.waitForIdle()
        assertEquals(listOf(TrainerAction.Character('(')), taps)
        assertTrue(live.screenLower)
        assertEquals(listOf(TrainerAction.Lower to true), contacts)

        keyboard.performTouchInput {
            up(pointerId = 0)
        }
        rule.waitForIdle()
        assertEquals(
            listOf(TrainerAction.Lower to true, TrainerAction.Lower to false),
            contacts,
        )
        assertFalse(live.screenLower)
        rule.onNodeWithContentDescription("Клавиша ц", substring = true).assertExists()
        rule.onNodeWithContentDescription("удерживается", substring = true).assertDoesNotExist()
    }

    @Test
    fun shortTapAndDragOutsideReleaseL1WithoutTyping() {
        val session = TypingSession(Language.RU, "(")
        var live by mutableStateOf(BoardLive())
        val taps = mutableListOf<TrainerAction>()
        val contacts = mutableListOf<Pair<TrainerAction, Boolean>>()
        rule.setContent {
            CorneTheme {
                CorneKeyboard(
                    model = keyboardModel(session, live),
                    highlightEnabled = true,
                    fingerColors = false,
                    keyScale = 1f,
                    errorNonce = 0,
                    lastWrong = null,
                    onAction = { _, action -> taps += action },
                    onContact = { action, down ->
                        contacts += action to down
                        if (action == TrainerAction.Lower) live = live.copy(screenLower = down)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.waitForIdle()
        val keyboard = rule.onNodeWithContentDescription("Клавиатура 48")
        val l1 = centerOf("Клавиша L1", keyboard)

        keyboard.performTouchInput {
            down(pointerId = 0, position = l1)
            advanceEventTime(20)
            up(pointerId = 0)
        }
        rule.waitForIdle()
        assertEquals(listOf(TrainerAction.Lower to true, TrainerAction.Lower to false), contacts)
        assertTrue(taps.isEmpty())
        assertFalse(live.screenLower)

        contacts.clear()
        keyboard.performTouchInput {
            down(pointerId = 0, position = l1)
            advanceEventTime(20)
            moveTo(pointerId = 0, position = Offset(1f, 1f))
            advanceEventTime(20)
            up(pointerId = 0)
        }
        rule.waitForIdle()
        assertEquals(TrainerAction.Lower to true, contacts.first())
        assertTrue(contacts.any { it == TrainerAction.Lower to false })
        assertTrue(taps.isEmpty())
        assertFalse(live.screenLower)
    }

    private fun centerOf(description: String, origin: SemanticsNodeInteraction): Offset {
        val root = origin.fetchSemanticsNode()
        val node = rule.onNodeWithContentDescription(description, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode()
        val topLeft = node.positionInRoot - root.positionInRoot
        return topLeft + Offset(node.size.width / 2f, node.size.height / 2f)
    }
}
