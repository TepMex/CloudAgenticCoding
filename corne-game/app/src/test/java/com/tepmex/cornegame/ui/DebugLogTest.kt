package com.tepmex.cornegame.ui

import com.tepmex.cornegame.data.AppSettings
import com.tepmex.cornegame.data.FileSessionHistory
import com.tepmex.cornegame.data.FileSettingsStore
import com.tepmex.cornegame.domain.HARDWARE_DOWN
import com.tepmex.cornegame.domain.HARDWARE_MULTIPLE
import com.tepmex.cornegame.domain.HARDWARE_UP
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.Latch
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.TypingSession
import com.tepmex.cornegame.domain.stepHardware
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugLogTest {
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
    fun keyLineKeepsCodeUnicodeFlagsAndDevice() {
        val line = formatKey(
            HardwareKeyTrace(
                phase = HARDWARE_DOWN,
                keyCode = 45,
                keyLabel = "KEYCODE_Q",
                scanCode = 16,
                unicodeChar = 'q'.code,
                unicodeBare = 'q'.code,
                shift = false,
                repeatCount = 0,
                deviceId = 7,
                deviceName = "Cornedeon",
                source = 0x101,
                flags = 0x8 or 0x20,
                displayLabel = 'q',
            ),
        )
        assertTrue(line.contains("KEY down code=45 KEYCODE_Q scan=16"))
        assertTrue(line.contains("uni=${'q'.code}'q'"))
        assertTrue(line.contains("flags=0x28 SYSTEM,CANCELED"))
        assertTrue(line.contains("dev=7 \"Cornedeon\""))
        assertEquals("0", showUnicode(0))
        assertEquals("legends=empty", legendSample(LegendTable()))
    }

    @Test
    fun releaseWithEmptyUnicodeStillAdvancesAndLogsTheHighlight() {
        val viewModel = viewModel()
        val expected = viewModel.state.value.session.nextChar
        check(expected != null)
        val beforeDown = viewModel.debugBaseline()
        val down = stepHardware(
            phase = HARDWARE_DOWN,
            keyCode = 45,
            unicodeChar = expected.code,
            shiftDown = false,
            altDown = false,
            repeatCount = 0,
            pending = emptyMap(),
        )
        viewModel.onHardwareKey(
            beforeDown,
            HardwareKeyTrace(
                phase = HARDWARE_DOWN,
                keyCode = 45,
                keyLabel = "KEYCODE_Q",
                unicodeChar = expected.code,
            ),
            down,
        )
        assertEquals(0, viewModel.state.value.session.index)

        val beforeUp = viewModel.debugBaseline()
        val up = stepHardware(
            phase = HARDWARE_UP,
            keyCode = 45,
            unicodeChar = 0,
            shiftDown = false,
            altDown = false,
            repeatCount = 0,
            pending = down.pending,
        )
        viewModel.onHardwareKey(
            beforeUp,
            HardwareKeyTrace(
                phase = HARDWARE_UP,
                keyCode = 45,
                keyLabel = "KEYCODE_Q",
                unicodeChar = 0,
            ),
            up,
        )

        assertEquals(1, viewModel.state.value.session.index)
        val text = viewModel.debugLog.value.joinToString("\n")
        assertTrue(text.contains("KEY down code=45 KEYCODE_Q"))
        assertTrue(text.contains("pending=45:char"))
        assertTrue(text.contains("KEY up code=45 KEYCODE_Q"))
        assertTrue(text.contains("uni=0"))
        assertTrue(text.contains("deliver=char"))
        assertTrue(text.contains("action=char"))
        assertTrue(text.contains("OK"))
        assertTrue(text.contains("idx 0→1"))
        assertTrue(text.contains("hi="))
        assertTrue(text.contains("cap=\""))
    }

    @Test
    fun unknownMultipleCharacterAdvancesTheLesson() {
        val viewModel = viewModel()
        val expected = viewModel.state.value.session.nextChar
        check(expected != null)
        val before = viewModel.debugBaseline()
        val step = stepHardware(
            phase = HARDWARE_MULTIPLE,
            keyCode = 0,
            unicodeChar = 0,
            shiftDown = false,
            altDown = false,
            repeatCount = 0,
            pending = emptyMap(),
            characters = expected.toString(),
        )
        viewModel.onHardwareKey(
            before,
            HardwareKeyTrace(
                phase = HARDWARE_MULTIPLE,
                keyCode = 0,
                keyLabel = "KEYCODE_UNKNOWN",
                characters = expected.toString(),
            ),
            step,
        )
        assertEquals(1, viewModel.state.value.session.index)
        val text = viewModel.debugLog.value.joinToString("\n")
        assertTrue(text.contains("KEY multiple code=0 KEYCODE_UNKNOWN"))
        assertTrue(text.contains("chars=\"$expected\""))
        assertTrue(text.contains("deliver=char"))
        assertTrue(text.contains("action=char"))
        assertTrue(text.contains("OK"))
        assertTrue(text.contains("idx 0→1"))
    }

    @Test
    fun shiftDownLogsModifierAndCaptionWithoutConsumingTheKey() {
        val viewModel = viewModel()
        val before = viewModel.debugBaseline()
        viewModel.onHardwareModifiers(shift = true, alt = false)
        val step = stepHardware(
            phase = HARDWARE_DOWN,
            keyCode = 59,
            unicodeChar = 0,
            shiftDown = true,
            altDown = false,
            repeatCount = 0,
            pending = emptyMap(),
        )
        assertFalse(step.consumed)
        viewModel.onHardwareKey(
            before,
            HardwareKeyTrace(
                phase = HARDWARE_DOWN,
                keyCode = 59,
                keyLabel = "KEYCODE_SHIFT_LEFT",
                shift = true,
            ),
            step,
        )
        assertTrue(viewModel.state.value.board.hardwareShift)
        val text = viewModel.debugLog.value.joinToString("\n")
        assertTrue(text.contains("KEY down code=59 KEYCODE_SHIFT_LEFT"))
        assertTrue(text.contains("STEP consumed=0 deliver=-"))
        assertTrue(text.contains("Δ"))
        assertTrue(text.contains("hwS1"))
        assertTrue(text.contains("cap=\""))
    }

    @Test
    fun mistakeAndScreenLatchAndIgnoredKeyAreNamed() {
        val viewModel = viewModel()
        viewModel.onTrainerAction(TrainerAction.Character('!'))
        assertTrue(viewModel.debugLog.value.last().contains("ACTION screen char'!' MISS"))
        assertTrue(viewModel.debugLog.value.last().contains("flash"))
        assertEquals(0, viewModel.state.value.session.index)

        viewModel.onKeyContact(TrainerAction.Lower, true)
        viewModel.onKeyContact(TrainerAction.Lower, false)
        val text = viewModel.debugLog.value.joinToString("\n")
        assertTrue(text.contains("TOUCH down L1"))
        assertTrue(text.contains("TOUCH up L1 SHORT"))
        assertTrue(text.contains("ACTION screen L1 OK"))
        assertTrue(text.contains("latch NONE→LOWER"))
        assertEquals(Latch.LOWER, viewModel.state.value.session.latch)

        viewModel.onPointer("down", 4, "q", TrainerAction.Character('q'), null)
        assertTrue(viewModel.debugLog.value.last().contains("POINTER down id=4 legend=q action=char'q'"))

        viewModel.openSettings()
        val index = viewModel.state.value.session.index
        viewModel.onTrainerAction(TrainerAction.Character('a'))
        assertEquals(index, viewModel.state.value.session.index)
        assertTrue(viewModel.debugLog.value.last().contains("IGNORED screen=Settings"))
    }

    @Test
    fun heldLayerTypesWithoutLatchingOnRelease() {
        val viewModel = viewModel()
        viewModel.onKeyContact(TrainerAction.Lower, true)
        val next = viewModel.state.value.session.nextChar
        check(next != null)
        viewModel.onTrainerAction(TrainerAction.Character(next))
        viewModel.onKeyContact(TrainerAction.Lower, false)
        assertEquals(Latch.NONE, viewModel.state.value.session.latch)
        assertTrue(viewModel.debugLog.value.any { it.contains("TOUCH up L1 HOLD-USED") })
    }

    @Test
    fun keyboardProbeIsLoggedOnceAndClearKeepsTheSession() {
        val viewModel = viewModel()
        val target = viewModel.state.value.session.target
        viewModel.noteKeyboard(-1, false, null, LegendTable(), cleared = true)
        viewModel.noteKeyboard(-1, false, null, LegendTable(), cleared = true)
        assertEquals(1, viewModel.debugLog.value.count { "KBD " in it })
        viewModel.clearDebug()
        assertEquals(1, viewModel.debugLog.value.size)
        assertTrue(viewModel.debugLog.value.single().contains("CLEAR"))
        assertEquals(target, viewModel.state.value.session.target)
    }

    @Test
    fun logDropsLinesPastTheLimit() {
        val viewModel = viewModel()
        repeat(DEBUG_LOG_LIMIT + 25) {
            viewModel.onPointer("down", 1, "q", TrainerAction.Character('q'), null)
        }
        assertEquals(DEBUG_LOG_LIMIT, viewModel.debugLog.value.size)
        assertTrue(viewModel.debugLog.value.first().contains("POINTER"))
    }

    @Test
    fun snapshotNamesTheLayerTheScreenIsAskingFor() {
        val state = TrainUiState(
            settings = AppSettings(),
            session = TypingSession(Language.EN, "(a)"),
            screen = AppScreen.Train,
            nowMs = 0,
            errorNonce = 0,
            lastWrong = null,
            history = emptyList(),
        )
        val line = uiSnapshot(state)
        assertTrue(line.contains("idx=0/3"))
        assertTrue(line.contains("next='('"))
        assertTrue(line.contains("нужен L1"))
        assertTrue(line.contains("hi=L1"))
        val moved = state.copy(
            session = state.session.copy(index = 1),
            errorNonce = 1,
            lastWrong = 'x',
        )
        val delta = uiDelta(state, moved)
        assertTrue(delta.contains("idx 0→1"))
        assertTrue(delta.contains("flash"))
        assertTrue(delta.contains("wrong -→'x'"))
        val hardware = abbreviateDebugLine(
            "12:00:00.000 UI action=char'(' OK Δ idx 0→1 | idx=1/3 next='a'",
        )
        assertEquals("12:00:00.000 UI action=char'(' OK Δ idx 0→1", hardware)
        val screen = abbreviateDebugLine(
            "12:00:00.000 ACTION screen char'(' OK | Δ idx 0→1 hi L1→( | idx=1/3 next='a'",
        )
        assertEquals("12:00:00.000 ACTION screen char'(' OK | Δ idx 0→1 hi L1→(", screen)
    }

    private fun viewModel(): TrainViewModel {
        val dir = File.createTempFile("corne-log", "dir").apply {
            delete()
            mkdirs()
        }
        return TrainViewModel(
            FileSessionHistory(File(dir, "sessions.json")),
            FileSettingsStore(File(dir, "settings.json")),
            clock = { 1_000L },
            random = Random(1),
        )
    }
}
