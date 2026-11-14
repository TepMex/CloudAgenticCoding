package com.tepmex.cornegame.ui

import com.tepmex.cornegame.data.FileSessionHistory
import com.tepmex.cornegame.data.FileSettingsStore
import com.tepmex.cornegame.domain.AndroidKeyCodes
import com.tepmex.cornegame.domain.BoardLive
import com.tepmex.cornegame.domain.Confidence
import com.tepmex.cornegame.domain.FirmwareState
import com.tepmex.cornegame.domain.HARDWARE_MULTIPLE
import com.tepmex.cornegame.domain.HardwareEvent
import com.tepmex.cornegame.domain.InputMachine
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.Latch
import com.tepmex.cornegame.domain.Telemetry
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.TypingSession
import com.tepmex.cornegame.domain.holdRecommendation
import com.tepmex.cornegame.domain.keyboardModel
import com.tepmex.cornegame.domain.physicalLayerLabel
import com.tepmex.cornegame.domain.stepHardware
import com.tepmex.cornegame.domain.virtualLayerName
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MomentaryLayerTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pressAndReleaseL1MovesBetweenLowerAndBaseWithoutLatching() {
        val viewModel = russianTrainer()
        val session = viewModel.state.value.session
        assertEquals("ц", keyboardModel(session, viewModel.state.value.board).leftRows[0][2].legend)
        viewModel.onKeyContact(TrainerAction.Lower, true)
        val held = keyboardModel(viewModel.state.value.session, viewModel.state.value.board)
        assertEquals("(", held.leftRows[0][2].legend)
        assertEquals("LOWER", virtualLayerName(viewModel.state.value.board))
        assertTrue(held.leftRows[3][4].held)
        assertEquals(KeyFill.HELD, keyFill(highlighted = false, held = true))
        assertEquals(KeyFill.HINT, keyFill(highlighted = true, held = false))
        assertNotEquals(KeyFill.HINT, keyFill(highlighted = false, held = true))
        assertEquals(0, viewModel.state.value.session.index)
        assertEquals(0, viewModel.state.value.session.correctPresses)

        viewModel.onKeyContact(TrainerAction.Lower, false)
        assertEquals(Latch.NONE, viewModel.state.value.session.latch)
        assertFalse(viewModel.state.value.board.screenLower)
        assertEquals("BASE", virtualLayerName(viewModel.state.value.board))
        assertEquals(
            "ц",
            keyboardModel(viewModel.state.value.session, viewModel.state.value.board).leftRows[0][2].legend,
        )
        assertEquals(0, viewModel.state.value.session.index)
    }

    @Test
    fun heldL1AndWTypesParenButWAloneDoesNot() {
        val viewModel = russianTrainer()
        val bare = keyboardModel(viewModel.state.value.session, viewModel.state.value.board)
        val w = bare.leftRows[0][2]
        assertEquals("ц", w.legend)
        viewModel.onScreenKey(w.keyCode, w.tap)
        assertEquals(0, viewModel.state.value.session.index)
        assertTrue(viewModel.state.value.session.errors > 0)

        viewModel.restart()
        viewModel.onScreenKey(w.keyCode, TrainerAction.Character('('))
        assertEquals(0, viewModel.state.value.session.index)
        assertEquals(Latch.NONE, viewModel.state.value.session.latch)

        viewModel.restart()
        viewModel.onKeyContact(TrainerAction.Lower, true)
        val layered = keyboardModel(viewModel.state.value.session, viewModel.state.value.board)
        val paren = layered.leftRows[0][2]
        assertEquals("(", paren.legend)
        assertEquals(0, viewModel.state.value.session.correctPresses)
        assertTrue(holdRecommendation(viewModel.state.value.session, viewModel.state.value.board).contains("L1"))
        assertTrue(holdRecommendation(viewModel.state.value.session, viewModel.state.value.board).contains("W"))
        viewModel.onScreenKey(paren.keyCode, paren.tap)
        assertEquals(1, viewModel.state.value.session.index)
        assertTrue(viewModel.state.value.session.nextChar?.isDigit() == true)
        viewModel.onKeyContact(TrainerAction.Lower, false)
        assertEquals(Latch.NONE, viewModel.state.value.session.latch)
        assertEquals(1, viewModel.state.value.session.index)
    }

    @Test
    fun l2AndAdjustFollowWhicheverThumbsAreStillDown() {
        val viewModel = russianTrainer()
        viewModel.onKeyContact(TrainerAction.Upper, true)
        val upper = keyboardModel(viewModel.state.value.session, viewModel.state.value.board)
        assertEquals("UPPER", virtualLayerName(viewModel.state.value.board))
        assertEquals("F1", upper.rightRows[2][0].legend)
        assertTrue(upper.rightRows[3][1].held)
        viewModel.onKeyContact(TrainerAction.Upper, false)
        assertEquals("BASE", virtualLayerName(viewModel.state.value.board))
        assertEquals(Latch.NONE, viewModel.state.value.session.latch)

        viewModel.onKeyContact(TrainerAction.Lower, true)
        viewModel.onKeyContact(TrainerAction.Upper, true)
        val adjust = keyboardModel(viewModel.state.value.session, viewModel.state.value.board)
        assertEquals("ADJUST", virtualLayerName(viewModel.state.value.board))
        assertEquals("Calc", adjust.leftRows[2][3].legend)
        assertEquals("·", adjust.leftRows[0][2].legend)
        assertEquals("L1", adjust.leftRows[3][4].legend)
        assertEquals("L2", adjust.rightRows[3][1].legend)
        assertTrue(adjust.leftRows[3][4].held)
        assertTrue(adjust.rightRows[3][1].held)
        viewModel.onKeyContact(TrainerAction.Lower, false)
        assertEquals("UPPER", virtualLayerName(viewModel.state.value.board))
        assertTrue(viewModel.state.value.board.screenUpper)
        viewModel.onKeyContact(TrainerAction.Upper, false)
        assertEquals("BASE", virtualLayerName(viewModel.state.value.board))
        assertEquals(0, viewModel.state.value.session.index)
    }

    @Test
    fun cancelledContactClearsHoldsAndDoesNotReplaceFirmwareLayer() {
        val viewModel = russianTrainer()
        viewModel.onKeyContact(TrainerAction.Lower, true)
        viewModel.onKeyContact(TrainerAction.Upper, true)
        viewModel.releaseScreenHolds()
        assertFalse(viewModel.state.value.board.screenLower)
        assertFalse(viewModel.state.value.board.screenUpper)
        assertEquals("BASE", virtualLayerName(viewModel.state.value.board))
        assertEquals("неизвестен", physicalLayerLabel(FirmwareState()))
        assertEquals("неизвестен", physicalLayerLabel(FirmwareState(stage = com.tepmex.cornegame.domain.LinkStage.PROTOCOL_VERIFIED)))
        val reported = FirmwareState(
            telemetry = Telemetry.AVAILABLE,
            confidence = Confidence.REPORTED,
            activeLayersMask = 0x2,
        )
        assertEquals("0x2", physicalLayerLabel(reported))
        assertEquals("неизвестен", physicalLayerLabel(FirmwareState(activeLayersMask = 0x2)))
    }

    @Test
    fun physicalUnicodeStillAdvancesTextWithoutClaimingL1() {
        val viewModel = russianTrainer()
        val before = viewModel.debugBaseline()
        val step = stepHardware(
            InputMachine(),
            HardwareEvent(
                phase = HARDWARE_MULTIPLE,
                keyCode = AndroidKeyCodes.UNKNOWN,
                deviceId = 4,
                characters = "(",
                language = Language.RU,
            ),
        )
        assertEquals(TrainerAction.Character('('), step.deliver.single().action)
        viewModel.onHardwareKey(
            before,
            HardwareKeyTrace(
                phase = HARDWARE_MULTIPLE,
                keyCode = AndroidKeyCodes.UNKNOWN,
                keyLabel = "UNKNOWN",
                deviceId = 4,
                characters = "(",
            ),
            step,
        )
        assertEquals(1, viewModel.state.value.session.index)
        assertFalse(viewModel.state.value.board.screenLower)
        assertEquals(Latch.NONE, viewModel.state.value.session.latch)
        assertEquals("неизвестен", physicalLayerLabel(FirmwareState()))
        assertEquals("BASE", virtualLayerName(viewModel.state.value.board))
    }

    @Test
    fun screenHoldDoesNotChangeASeparateTypingSessionLatch() {
        val session = TypingSession(Language.RU, "(")
        val shown = keyboardModel(session, BoardLive(screenLower = true))
        assertEquals("(", shown.leftRows[0][2].legend)
        assertEquals(Latch.NONE, session.latch)
        val untouched = keyboardModel(session.copy(latch = Latch.LOWER))
        assertEquals("ц", untouched.leftRows[0][2].legend)
    }

    private fun russianTrainer(): TrainViewModel {
        val dir = tempDir()
        val viewModel = TrainViewModel(
            FileSessionHistory(File(dir, "sessions.json")),
            FileSettingsStore(File(dir, "settings.json")),
            clock = { 1_000L },
            random = Random(1),
        )
        viewModel.setLanguage(Language.RU)
        check(viewModel.state.value.session.nextChar == '(')
        return viewModel
    }
}

private fun tempDir(): File = File.createTempFile("corne-layer", "").apply {
    delete()
    mkdirs()
}
