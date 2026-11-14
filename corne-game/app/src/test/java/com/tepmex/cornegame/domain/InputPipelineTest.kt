package com.tepmex.cornegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputPipelineTest {
    @Test
    fun virtualDeviceIsNotAPhysicalCornedeonAndDoesNotBorrowAnotherMap() {
        assertNull(ownKeyboardDevice(-1, setOf(18)))
        assertNull(ownKeyboardDevice(7, setOf(18)))
        assertEquals(18, ownKeyboardDevice(18, setOf(18)))
        val virtual = press(AndroidKeyCodes.SLASH, deviceId = -1, unicode = '/'.code, language = Language.RU)
        assertEquals(InputSource.UNKNOWN, virtual.source)
        assertEquals('·'.let { '.' }, charOf(virtual))
        val physical = press(AndroidKeyCodes.SLASH, deviceId = 18, unicode = '/'.code, language = Language.RU)
        assertEquals(InputSource.PHYSICAL, physical.source)
        assertEquals('.', charOf(physical))
    }

    @Test
    fun textPathKeepsReadyUnicodeAndDoesNotRunItThroughVial() {
        val step = stepHardware(
            InputMachine(),
            HardwareEvent(
                phase = HARDWARE_MULTIPLE,
                keyCode = AndroidKeyCodes.UNKNOWN,
                characters = "йжзэё",
                deviceId = -1,
                language = Language.EN,
            ),
        )
        assertEquals(InputSource.TEXT, step.source)
        assertEquals(listOf('й', 'ж', 'з', 'э', 'ё'), chars(step))
    }

    @Test
    fun physicalAndTextPathsDoNotDoubleCount() {
        val down = press(AndroidKeyCodes.Q, deviceId = 18, unicode = 'q'.code, language = Language.RU)
        assertEquals('й', charOf(down))
        val echoLatin = stepHardware(
            down.machine,
            HardwareEvent(phase = HARDWARE_MULTIPLE, keyCode = 0, characters = "q", deviceId = -1),
        )
        assertTrue(echoLatin.deliver.isEmpty())
        val echoCyrillic = stepHardware(
            down.machine,
            HardwareEvent(phase = HARDWARE_MULTIPLE, keyCode = 0, characters = "й", deviceId = -1),
        )
        assertTrue(echoCyrillic.deliver.isEmpty())
        val up = stepHardware(down.machine, HardwareEvent(phase = HARDWARE_UP, keyCode = AndroidKeyCodes.Q, deviceId = 18))
        assertTrue(up.deliver.isEmpty())
    }

    @Test
    fun shiftHeldAcrossSeveralLettersAndSeveralModifiersAtOnce() {
        var machine = InputMachine()
        machine = stepHardware(machine, modifier(AndroidKeyCodes.SHIFT_LEFT)).machine
        val upperA = stepHardware(machine, pressEvent(AndroidKeyCodes.A, language = Language.RU))
        machine = stepHardware(upperA.machine, HardwareEvent(phase = HARDWARE_UP, keyCode = AndroidKeyCodes.A)).machine
        val upperB = stepHardware(machine, pressEvent(AndroidKeyCodes.B, language = Language.EN))
        assertEquals('Ф', charOf(upperA))
        assertEquals('B', charOf(upperB))

        machine = InputMachine()
        machine = stepHardware(machine, modifier(AndroidKeyCodes.CTRL_LEFT)).machine
        machine = stepHardware(machine, modifier(AndroidKeyCodes.SHIFT_RIGHT)).machine
        machine = stepHardware(machine, modifier(AndroidKeyCodes.ALT_RIGHT)).machine
        val chord = stepHardware(machine, pressEvent(AndroidKeyCodes.Z))
        val shortcut = chord.deliver.single().action as TrainerAction.Shortcut
        assertEquals(AndroidKeyCodes.Z, shortcut.keyCode)
        assertEquals(setOf(KeyModifier.CTRL, KeyModifier.SHIFT, KeyModifier.ALT), shortcut.modifiers)
        val release = stepHardware(chord.machine, HardwareEvent(phase = HARDWARE_UP, keyCode = AndroidKeyCodes.CTRL_LEFT))
        assertTrue(release.deliver.isEmpty())
    }

    @Test
    fun rightModifierMatchesTheSameShortcutAsLeftAndRepeatDoesNotAddOne() {
        val left = shortcut(AndroidKeyCodes.CTRL_LEFT, AndroidKeyCodes.C)
        val right = shortcut(AndroidKeyCodes.CTRL_RIGHT, AndroidKeyCodes.C)
        assertEquals(left, right)
        var machine = InputMachine()
        machine = stepHardware(machine, modifier(AndroidKeyCodes.CTRL_LEFT)).machine
        val down = stepHardware(machine, pressEvent(AndroidKeyCodes.C))
        val repeat = stepHardware(down.machine, pressEvent(AndroidKeyCodes.C).copy(repeatCount = 3))
        assertTrue(repeat.deliver.isEmpty())
        val up = stepHardware(repeat.machine, HardwareEvent(phase = HARDWARE_UP, keyCode = AndroidKeyCodes.C))
        assertTrue(up.deliver.isEmpty())
    }

    @Test
    fun autorepeatIsIgnored() {
        val down = press(AndroidKeyCodes.A, deviceId = 2)
        val repeat = stepHardware(down.machine, pressEvent(AndroidKeyCodes.A).copy(repeatCount = 2, deviceId = 2))
        assertTrue(repeat.deliver.isEmpty())
    }

    @Test
    fun russianLettersDigitsShiftAndTheAwkwardMarks() {
        val plain = listOf(
            AndroidKeyCodes.Q to 'й',
            AndroidKeyCodes.W to 'ц',
            AndroidKeyCodes.E to 'у',
            AndroidKeyCodes.R to 'к',
            AndroidKeyCodes.T to 'е',
            AndroidKeyCodes.Y to 'н',
            AndroidKeyCodes.U to 'г',
            AndroidKeyCodes.I to 'ш',
            AndroidKeyCodes.O to 'щ',
            AndroidKeyCodes.P to 'з',
            AndroidKeyCodes.A to 'ф',
            AndroidKeyCodes.S to 'ы',
            AndroidKeyCodes.D to 'в',
            AndroidKeyCodes.F to 'а',
            AndroidKeyCodes.G to 'п',
            AndroidKeyCodes.H to 'р',
            AndroidKeyCodes.J to 'о',
            AndroidKeyCodes.K to 'л',
            AndroidKeyCodes.L to 'д',
            AndroidKeyCodes.SEMICOLON to 'ж',
            AndroidKeyCodes.APOSTROPHE to 'э',
            AndroidKeyCodes.Z to 'я',
            AndroidKeyCodes.X to 'ч',
            AndroidKeyCodes.C to 'с',
            AndroidKeyCodes.V to 'м',
            AndroidKeyCodes.B to 'и',
            AndroidKeyCodes.N to 'т',
            AndroidKeyCodes.M to 'ь',
            AndroidKeyCodes.COMMA to 'б',
            AndroidKeyCodes.PERIOD to 'ю',
            AndroidKeyCodes.SLASH to '.',
            AndroidKeyCodes.GRAVE to 'ё',
            AndroidKeyCodes.LEFT_BRACKET to 'х',
            AndroidKeyCodes.RIGHT_BRACKET to 'ъ',
        )
        plain.forEach { (code, expected) ->
            val glyph = glyphFor(Language.RU, code)
            assertEquals("$code plain", expected, glyph?.plain)
            if (expected.isLetter()) {
                assertEquals("$code shifted", expected.uppercaseChar(), glyph?.shifted)
            }
        }
        assertEquals('(', glyphFor(Language.RU, AndroidKeyCodes.DIGIT_9)?.shifted)
        assertEquals(')', glyphFor(Language.RU, AndroidKeyCodes.DIGIT_0)?.shifted)
        assertEquals('(', glyphFor(Language.EN, AndroidKeyCodes.DIGIT_9)?.shifted)
        assertEquals(')', glyphFor(Language.EN, AndroidKeyCodes.DIGIT_0)?.shifted)
        assertEquals('/', glyphFor(Language.RU, AndroidKeyCodes.NUMPAD_DIVIDE)?.plain)
        assertEquals('.', glyphFor(Language.RU, AndroidKeyCodes.NUMPAD_DOT)?.plain)
        assertEquals('ю', glyphFor(Language.RU, AndroidKeyCodes.PERIOD)?.plain)
        assertEquals(',', glyphFor(Language.RU, AndroidKeyCodes.SLASH)?.shifted)
        assertEquals('?', glyphFor(Language.EN, AndroidKeyCodes.SLASH)?.shifted)
        assertEquals('Ж', glyphFor(Language.RU, AndroidKeyCodes.SEMICOLON)?.shifted)
        assertEquals('Ё', glyphFor(Language.RU, AndroidKeyCodes.GRAVE)?.shifted)
        assertEquals('Х', glyphFor(Language.RU, AndroidKeyCodes.LEFT_BRACKET)?.shifted)
        assertEquals('Ъ', glyphFor(Language.RU, AndroidKeyCodes.RIGHT_BRACKET)?.shifted)
        assertEquals('Э', glyphFor(Language.RU, AndroidKeyCodes.APOSTROPHE)?.shifted)
        val shifted = press(AndroidKeyCodes.GRAVE, deviceId = 4, shift = true, language = Language.RU)
        assertEquals('Ё', charOf(shifted))
    }

    @Test
    fun englishBaseStaysLatinWhenTheEventUnicodeIsCyrillic() {
        val step = press(AndroidKeyCodes.Q, deviceId = 4, unicode = 'й'.code, language = Language.EN)
        assertEquals('q', charOf(step))
    }

    @Test
    fun functionKeysAreKeyPressesNotCharacters() {
        AndroidKeyCodes.F_KEYS.forEach { code ->
            val step = press(code, deviceId = 4)
            val action = step.deliver.single().action as TrainerAction.KeyPress
            assertEquals(code, action.keyCode)
        }
        val arrows = listOf(
            AndroidKeyCodes.DPAD_LEFT,
            AndroidKeyCodes.DPAD_RIGHT,
            AndroidKeyCodes.DPAD_UP,
            AndroidKeyCodes.DPAD_DOWN,
            AndroidKeyCodes.ESCAPE,
            AndroidKeyCodes.TAB,
            AndroidKeyCodes.FORWARD_DEL,
            AndroidKeyCodes.INSERT,
            AndroidKeyCodes.MOVE_HOME,
            AndroidKeyCodes.CAPS_LOCK,
            AndroidKeyCodes.SYSRQ,
        )
        arrows.forEach { code ->
            val action = press(code, deviceId = 2).deliver.single().action
            assertTrue(action is TrainerAction.KeyPress)
        }
        assertTrue(press(AndroidKeyCodes.DEL, deviceId = 2).deliver.single().action is TrainerAction.Backspace)
        assertTrue(press(AndroidKeyCodes.ENTER, deviceId = 2).deliver.single().action is TrainerAction.KeyPress)
    }

    @Test
    fun altShiftPropagatesAndDoesNotRepeatWhileHeld() {
        var machine = InputMachine()
        val alt = stepHardware(machine, modifier(AndroidKeyCodes.ALT_LEFT))
        assertFalse(alt.consumed)
        assertTrue(alt.deliver.isEmpty())
        machine = alt.machine
        val shift = stepHardware(machine, modifier(AndroidKeyCodes.SHIFT_LEFT))
        assertFalse(shift.consumed)
        assertTrue(shift.deliver.single().action is TrainerAction.AltShift)
        val again = stepHardware(shift.machine, modifier(AndroidKeyCodes.SHIFT_RIGHT))
        assertTrue(again.deliver.isEmpty())
    }
}

private fun pressEvent(keyCode: Int, language: Language = Language.EN) = HardwareEvent(
    phase = HARDWARE_DOWN,
    keyCode = keyCode,
    deviceId = 3,
    language = language,
)

private fun modifier(keyCode: Int) = HardwareEvent(
    phase = HARDWARE_DOWN,
    keyCode = keyCode,
    deviceId = 3,
    shift = keyCode == AndroidKeyCodes.SHIFT_LEFT || keyCode == AndroidKeyCodes.SHIFT_RIGHT,
    alt = keyCode == AndroidKeyCodes.ALT_LEFT || keyCode == AndroidKeyCodes.ALT_RIGHT,
    ctrl = keyCode == AndroidKeyCodes.CTRL_LEFT || keyCode == AndroidKeyCodes.CTRL_RIGHT,
)

private fun press(
    keyCode: Int,
    deviceId: Int,
    unicode: Int = 0,
    shift: Boolean = false,
    language: Language = Language.EN,
) = stepHardware(
    if (shift) {
        stepHardware(InputMachine(), modifier(AndroidKeyCodes.SHIFT_LEFT)).machine
    } else {
        InputMachine()
    },
    HardwareEvent(
        phase = HARDWARE_DOWN,
        keyCode = keyCode,
        deviceId = deviceId,
        unicodeChar = unicode,
        shift = shift,
        language = language,
    ),
)

private fun charOf(step: HardwareStep): Char = (step.deliver.single().action as TrainerAction.Character).value

private fun chars(step: HardwareStep): List<Char> =
    step.deliver.map { (it.action as TrainerAction.Character).value }

private fun shortcut(modifier: Int, key: Int): Set<KeyModifier> {
    var machine = stepHardware(InputMachine(), modifier(modifier)).machine
    val step = stepHardware(machine, pressEvent(key))
    return (step.deliver.single().action as TrainerAction.Shortcut).modifiers
}
