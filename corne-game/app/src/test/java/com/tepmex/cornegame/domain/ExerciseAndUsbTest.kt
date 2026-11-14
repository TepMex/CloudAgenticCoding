package com.tepmex.cornegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAndUsbTest {
    @Test
    fun serviceDrillUsesOnlyKeysThatExistOnTheBoard() {
        val steps = serviceExercises()
        val codes = steps.map { it.keyCode }.toSet()
        assertTrue(AndroidKeyCodes.F_KEYS.all { it in codes })
        assertTrue(AndroidKeyCodes.ESCAPE in codes)
        assertTrue(AndroidKeyCodes.DEL in codes)
        assertTrue(AndroidKeyCodes.NUMPAD_DIVIDE in codes)
        assertTrue(AndroidKeyCodes.NUMPAD_1 in codes)
        assertFalse(0 in codes)
        steps.forEach { step ->
            assertTrue(step.keyIds.isNotEmpty())
            step.keyIds.forEach { id ->
                val placed = boardPlacements().filter { it.id == id && it.layer == step.recommendedLayer }
                assertTrue(placed.none { it.empty })
            }
            assertTrue(placementsFor(step.keyCode).isNotEmpty())
        }
        assertTrue(steps.none { it.title == "·" })
        val f5 = steps.first { it.keyCode == AndroidKeyCodes.F5 }
        assertEquals(Latch.UPPER, f5.recommendedLayer)
        assertTrue(acceptanceLine(f5.recommendedLayer, FirmwareState()).contains("не подтвердила"))
        assertNull(FirmwareState().activeLayersMask)
    }

    @Test
    fun shortcutsMatchOnlyWhileModifiersAreHeld() {
        val session = ActionSession(DrillKind.SHORTCUT, shortcutExercises())
        val ctrlC = session.steps.first { it.title == "Ctrl+C" } as ExerciseStep.Shortcut
        assertEquals(setOf(KeyModifier.CTRL), ctrlC.modifiers)
        val wrong = Resolved(
            TrainerAction.Shortcut(AndroidKeyCodes.C, setOf(KeyModifier.CTRL, KeyModifier.SHIFT)),
            AndroidKeyCodes.C,
            setOf(KeyModifier.CTRL, KeyModifier.SHIFT),
            InputSource.PHYSICAL,
        )
        val (missed, miss) = session.apply(wrong, 1_000)
        assertTrue(miss.mistake)
        assertEquals(0, missed.index)

        var machine = InputMachine()
        machine = stepHardware(machine, HardwareEvent(phase = HARDWARE_DOWN, keyCode = AndroidKeyCodes.CTRL_LEFT, deviceId = 2)).machine
        val down = stepHardware(machine, HardwareEvent(phase = HARDWARE_DOWN, keyCode = AndroidKeyCodes.C, deviceId = 2))
        val repeat = stepHardware(down.machine, HardwareEvent(phase = HARDWARE_DOWN, keyCode = AndroidKeyCodes.C, deviceId = 2, repeatCount = 2))
        val upKey = stepHardware(repeat.machine, HardwareEvent(phase = HARDWARE_UP, keyCode = AndroidKeyCodes.C, deviceId = 2))
        val upMod = stepHardware(upKey.machine, HardwareEvent(phase = HARDWARE_UP, keyCode = AndroidKeyCodes.CTRL_LEFT, deviceId = 2))
        assertEquals(1, down.deliver.size)
        assertTrue(repeat.deliver.isEmpty())
        assertTrue(upKey.deliver.isEmpty())
        assertTrue(upMod.deliver.isEmpty())
        val (hit, effect) = missed.apply(down.deliver.single(), 2_000)
        assertFalse(effect.mistake)
        assertEquals(1, hit.index)

        val shiftF10 = hit.steps.first { it.title == "Shift+F10" } as ExerciseStep.Shortcut
        var held = InputMachine()
        held = stepHardware(held, HardwareEvent(phase = HARDWARE_DOWN, keyCode = AndroidKeyCodes.SHIFT_LEFT, deviceId = 2)).machine
        val f10 = stepHardware(held, HardwareEvent(phase = HARDWARE_DOWN, keyCode = AndroidKeyCodes.F10, deviceId = 2))
        assertTrue(matches(shiftF10, f10.deliver.single()))
        val altEnter = hit.steps.first { it.title == "Alt+Enter" } as ExerciseStep.Shortcut
        assertEquals(setOf(KeyModifier.ALT), altEnter.modifiers)
        assertTrue(altEnter.interceptNote != null)
    }

    @Test
    fun textStepStillMatchesACharacterAndServiceKeyDoesNotAdvanceTextWpm() {
        val text = ExerciseStep.Text("a")
        val good = Resolved(TrainerAction.Character('a'), AndroidKeyCodes.A, emptySet(), InputSource.TEXT)
        val bad = Resolved(TrainerAction.KeyPress(AndroidKeyCodes.F1), AndroidKeyCodes.F1, emptySet(), InputSource.PHYSICAL)
        assertTrue(matches(text, good))
        assertFalse(matches(text, bad))
        val typing = TypingSession(Language.EN, "a")
        val (next, effect) = typing.apply(TrainerAction.KeyPress(AndroidKeyCodes.F5), 1_000)
        assertFalse(effect.mistake)
        assertEquals(0, next.index)
        assertEquals(0.0, next.stats(2_000).wpm, 0.001)
    }

    @Test
    fun usbDashboardCoversMissingDeviceDenialAndAReadOnlyProbe() {
        val missing = UsbDiagnosticsController(FakeUsb(emptyList()), async = false)
        missing.refresh()
        assertEquals(ProbeStatus.NO_DEVICE, missing.dashboard.value.probe.status)
        assertFalse(missing.state.value.connected)
        assertNull(missing.state.value.activeLayersMask)

        val denied = snapshot(permission = UsbPermission.DENIED)
        val deniedController = UsbDiagnosticsController(FakeUsb(listOf(denied)), async = false)
        deniedController.refresh()
        deniedController.probeSelected()
        assertEquals(ProbeStatus.NO_PERMISSION, deniedController.dashboard.value.probe.status)
        assertNull(deniedController.state.value.activeLayersMask)

        val opened = FakeUsb(listOf(snapshot(permission = UsbPermission.GRANTED)), openResult = null)
        val cannotOpen = UsbDiagnosticsController(opened, async = false)
        cannotOpen.refresh()
        cannotOpen.probeSelected()
        assertEquals(ProbeStatus.IO_ERROR, cannotOpen.dashboard.value.probe.status)

        val claimed = RecordingSession(claim = ClaimResult(false, ProbeStatus.CLAIM_FAILED, "занят"))
        val claimHost = FakeUsb(listOf(snapshot(permission = UsbPermission.GRANTED)), claimed)
        val claimController = UsbDiagnosticsController(claimHost, async = false)
        claimController.refresh()
        claimController.probeSelected()
        assertEquals(ProbeStatus.CLAIM_FAILED, claimController.dashboard.value.probe.status)
        assertTrue(claimed.payloads.isEmpty())
        assertNull(claimController.state.value.activeLayersMask)

        val success = RecordingSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok"),
            replies = listOf(viaVersion(9), vialId()),
        )
        val okHost = FakeUsb(listOf(snapshot(permission = UsbPermission.GRANTED)), success)
        val ok = UsbDiagnosticsController(okHost, async = false)
        ok.refresh()
        ok.probeSelected()
        assertEquals(ProbeStatus.OK, ok.dashboard.value.probe.status)
        assertEquals(9, ok.dashboard.value.probe.protocolVersion)
        assertEquals(6L, ok.dashboard.value.probe.vialProtocolVersion)
        assertEquals(UidMatchResult.MATCH, ok.dashboard.value.probe.uidComparison)
        assertTrue(DiagnosticFlag.VIA_VERIFIED in ok.dashboard.value.probe.flags)
        assertTrue(DiagnosticFlag.VIAL_VERIFIED in ok.dashboard.value.probe.flags)
        assertTrue(DiagnosticFlag.LAYER_TELEMETRY_UNAVAILABLE in ok.dashboard.value.probe.flags)
        assertEquals(2, success.payloads.size)
        assertEquals(VIA_GET_PROTOCOL_VERSION, success.payloads[0][0])
        assertEquals(VIAL_PREFIX, success.payloads[1][0])
        assertEquals(VIAL_GET_KEYBOARD_ID, success.payloads[1][1])
        assertEquals(Telemetry.UNAVAILABLE, ok.state.value.telemetry)
        assertEquals(Confidence.UNKNOWN, ok.state.value.confidence)
        assertNull(ok.state.value.activeLayersMask)
        assertTrue(ok.state.value.connected)
        assertTrue(success.closed)
        assertTrue(ok.dashboard.value.log.any { it.contains("RX (32 bytes):") })
    }

    @Test
    fun probeRejectsAForeignPacketAndDoesNotChooseTheBootKeyboard() {
        assertTrue(parseViaProtocolVersion(ByteArray(32)) is ViaVersionParse.ProtocolError)
        assertTrue(parseViaProtocolVersion(byteArrayOf(0x05, 0x09, 0x00)) is ViaVersionParse.ProtocolError)
        assertEquals(ViaVersionParse.Ok(9), parseViaProtocolVersion(viaVersion(9)))
        val boot = InterfaceFact(
            id = 0,
            classCode = 3,
            subclass = 1,
            protocol = 1,
            endpoints = listOf(endpoint(0x81, 8)),
        )
        val raw = InterfaceFact(
            id = 1,
            classCode = 3,
            subclass = 0,
            protocol = 0,
            endpoints = listOf(endpoint(0x83, 32), endpoint(0x03, 32)),
        )
        assertEquals(raw.id, chooseRawInterface(listOf(boot, raw))?.id)
        assertNull(chooseRawInterface(listOf(boot)))
        assertEquals("IN", endpoint(0x81, 32).direction)
        assertEquals("OUT", endpoint(0x03, 32).direction)
        assertEquals("INTERRUPT", endpoint(0x81, 32).type)
        val mismatch = RecordingSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok"),
            replies = listOf(byteArrayOf(0x11, 0x00, 0x00)),
        )
        val result = runReadOnlyProbe(mismatch)
        assertEquals(ProbeStatus.PROTOCOL_MISMATCH, result.status)
        assertEquals(1, mismatch.payloads.size)
        assertTrue(
            looksLikeCornedeon(CORNEDEON_VENDOR_ID, CORNEDEON_PRODUCT_ID, null, null, "/dev/bus/usb/001/002"),
        )
        assertTrue(
            looksLikeCornedeon(CORNEDEON_VENDOR_ID, CORNEDEON_LEGACY_PRODUCT_ID, null, null, "/dev/bus/usb/001/002"),
        )
        assertTrue(looksLikeCornedeon(0x1234, 0x1, "alko", "Cornedeon v2m", "usb"))
        assertFalse(looksLikeCornedeon(0x1234, 0x1, "Other", "Keyboard", "usb"))
    }
}

private fun viaVersion(version: Int): ByteArray = ByteArray(32).also {
    it[0] = VIA_GET_PROTOCOL_VERSION
    it[1] = ((version shr 8) and 0xFF).toByte()
    it[2] = (version and 0xFF).toByte()
}

private fun vialId(): ByteArray = ByteArray(32).also { packet ->
    packet[0] = 6
    cornedeonVialUidBytes().copyInto(packet, destinationOffset = 4)
}

private fun endpoint(address: Int, packet: Int) = EndpointFact(address, attributes = 3, maxPacket = packet)

private fun snapshot(permission: UsbPermission) = UsbDeviceSnapshot(
    key = "usb-1",
    deviceName = "usb-1",
    vendorId = CORNEDEON_VENDOR_ID,
    productId = CORNEDEON_PRODUCT_ID,
    manufacturer = "alko",
    productName = "Cornedeon v2m",
    permission = permission,
    interfaces = emptyList(),
)

private class FakeUsb(
    private val devices: List<UsbDeviceSnapshot>,
    private val session: UsbSession? = null,
    private val openResult: UsbSession? = session,
) : UsbHardware {
    override fun listDevices(): List<UsbDeviceSnapshot> = devices

    override fun requestPermission(deviceKey: String, onResult: (Boolean) -> Unit) {
        onResult(false)
    }

    override fun open(deviceKey: String): UsbSession? = openResult
}

private class RecordingSession(
    private val claim: ClaimResult,
    private val replies: List<ByteArray> = emptyList(),
) : UsbSession {
    val payloads = mutableListOf<ByteArray>()
    var closed = false
    override fun claimRawHid(): ClaimResult = claim

    override fun exchange(payload: ByteArray): ByteArray? {
        val reply = replies.getOrNull(payloads.size)
        payloads += payload
        return reply
    }

    override fun close() {
        closed = true
    }
}
