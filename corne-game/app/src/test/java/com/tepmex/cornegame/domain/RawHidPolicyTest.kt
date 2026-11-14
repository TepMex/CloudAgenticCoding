package com.tepmex.cornegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawHidPolicyTest {
    @Test
    fun cornedeonV2mDescriptorsLeaveBootAndUnknownInterfacesAlone() {
        val boot = InterfaceFact(0, classCode = 3, subclass = 1, protocol = 1, endpoints = listOf(endpoint(0x81, 8)))
        val raw = InterfaceFact(
            1,
            classCode = 3,
            subclass = 0,
            protocol = 0,
            endpoints = listOf(endpoint(0x82, 32), endpoint(0x03, 32)),
        )
        val other = InterfaceFact(2, classCode = 3, subclass = 0, protocol = 0, endpoints = listOf(endpoint(0x84, 32)))
        assertEquals(HidRole.BOOT_KEYBOARD, boot.role)
        assertEquals(HidRole.RAW_HID, raw.role)
        assertEquals(HidRole.OTHER, other.role)
        assertEquals(1, chooseRawInterface(listOf(boot, raw, other))?.id)
        assertTrue(matchesRawCandidate(raw, raw))
        assertFalse(matchesRawCandidate(raw, boot))
        assertFalse(matchesRawCandidate(raw, other))
        assertTrue(looksLikeCornedeon(CORNEDEON_VENDOR_ID, CORNEDEON_PRODUCT_ID_V2M, null, null, "usb"))
    }

    @Test
    fun forceIsPlannedOnlyAfterConfirmationAndNeverForBoot() {
        assertEquals(emptyList<Boolean>(), claimForces(HidRole.BOOT_KEYBOARD, userConfirmedForce = true))
        assertEquals(emptyList<Boolean>(), claimForces(HidRole.OTHER, userConfirmedForce = true))
        assertEquals(listOf(false), claimForces(HidRole.RAW_HID, userConfirmedForce = false))
        assertEquals(listOf(false, true), claimForces(HidRole.RAW_HID, userConfirmedForce = true))

        val calls = mutableListOf<Boolean>()
        val gentleFails = runClaimPlan(HidRole.RAW_HID, userConfirmedForce = true) { force ->
            calls += force
            false
        }
        assertEquals(listOf(false, true), calls)
        assertEquals(listOf(ClaimAttempt(false, false), ClaimAttempt(true, false)), gentleFails)

        val stopped = mutableListOf<Boolean>()
        runClaimPlan(HidRole.RAW_HID, userConfirmedForce = true) { force ->
            stopped += force
            !force
        }
        assertEquals(listOf(false), stopped)

        val boot = mutableListOf<Boolean>()
        runClaimPlan(HidRole.BOOT_KEYBOARD, userConfirmedForce = true) { force ->
            boot += force
            true
        }
        assertTrue(boot.isEmpty())
    }

    @Test
    fun declinedForceDoesNotOpenAndConfirmedForceIsExplicit() {
        val raw = InterfaceFact(
            1,
            classCode = 3,
            subclass = 0,
            protocol = 0,
            endpoints = listOf(endpoint(0x82, 32), endpoint(0x03, 32)),
        )
        val session = RecordingProbe(claim = ClaimResult(false, ProbeStatus.CLAIM_FAILED, "false", interfaceId = 1))
        val host = CountingUsb(listOf(device(interfaces = listOf(raw))), session)
        val controller = UsbDiagnosticsController(host, async = false)
        controller.refresh()
        controller.probeSelected()
        assertEquals(ProbeStatus.CLAIM_FAILED, controller.dashboard.value.probe.status)
        assertTrue(canOfferForceProbe(controller.dashboard.value))
        assertEquals(listOf(false), session.forces)
        val opens = host.opens

        controller.probeAdvanced(confirmed = false)
        assertEquals(opens, host.opens)
        assertEquals(listOf(false), session.forces)
        assertTrue(controller.dashboard.value.probe.forceClaim.contains("не вызывался"))
        assertEquals(Telemetry.UNKNOWN, controller.state.value.telemetry)

        controller.probeAdvanced(confirmed = true)
        assertEquals(listOf(false, true), session.forces)
        assertEquals(opens + 1, host.opens)
        assertTrue(session.closed)
    }

    @Test
    fun exchangeErrorsReleaseTheSessionAndDoNotCallTelemetryUnsupported() {
        val exploded = ClosingProbe(exchange = { throw IllegalStateException("cable") })
        val host = CountingUsb(listOf(device()), exploded)
        val controller = UsbDiagnosticsController(host, async = false)
        controller.refresh()
        controller.probeSelected()
        assertEquals(ProbeStatus.IO_ERROR, controller.dashboard.value.probe.status)
        assertTrue(exploded.closed)
        assertEquals(Telemetry.UNKNOWN, controller.state.value.telemetry)
        assertEquals(LinkStage.PROTOCOL_UNVERIFIED, controller.state.value.stage)

        val timeout = ClosingProbe(exchange = { HidReport.Timeout })
        val timed = runReadOnlyProbe(timeout)
        assertEquals(ProbeStatus.TIMEOUT, timed.status)
        assertTrue(timeout.closed)
        assertEquals(Telemetry.UNKNOWN, firmwareFromProbe(timed).telemetry)

        val empty = ClosingProbe(exchange = { HidReport.Empty })
        val blank = runReadOnlyProbe(empty)
        assertEquals(ProbeStatus.NO_RESPONSE, blank.status)
        assertTrue(empty.closed)

        val wrong = ClosingProbe(exchange = { HidReport.Data(byteArrayOf(0x11, 0, 0)) })
        val mismatch = runReadOnlyProbe(wrong)
        assertEquals(ProbeStatus.PROTOCOL_MISMATCH, mismatch.status)
        assertEquals(1, wrong.calls)
        assertTrue(wrong.closed)

        val incompatibleBytes = ByteArray(32).also {
            it[0] = VIA_GET_PROTOCOL_VERSION
            it[2] = 0x10
        }
        val incompatible = ClosingProbe(exchange = { HidReport.Data(incompatibleBytes) })
        val version = runReadOnlyProbe(incompatible)
        assertEquals(ProbeStatus.INCOMPATIBLE_VERSION, version.status)
        assertEquals(Telemetry.UNKNOWN, firmwareFromProbe(version).telemetry)
        assertNull(firmwareFromProbe(version).activeLayersMask)
    }

    @Test
    fun verifiedProtocolDoesNotInventALayerMask() {
        var step = 0
        val session = ClosingProbe(exchange = {
            val reply = if (step++ == 0) viaVersion(9) else byteArrayOf(0x11, 0, 0)
            HidReport.Data(reply)
        })
        val result = runReadOnlyProbe(session)
        assertEquals(ProbeStatus.OK, result.status)
        assertEquals(9, result.protocolVersion)
        assertNull(result.vialKeyboardId)
        assertEquals("неправильный ответ", result.vialNote)
        val firmware = firmwareFromProbe(result)
        assertEquals(Telemetry.UNAVAILABLE, firmware.telemetry)
        assertEquals(LinkStage.PROTOCOL_VERIFIED, firmware.stage)
        assertEquals(Confidence.UNKNOWN, firmware.confidence)
        assertNull(firmware.activeLayersMask)
        assertTrue(layerTelemetryLine(firmware).contains("не поддерживается"))
        assertTrue(session.closed)
    }
}

private fun endpoint(address: Int, packet: Int) = EndpointFact(address, attributes = 3, maxPacket = packet)

private fun viaVersion(version: Int): ByteArray = ByteArray(32).also {
    it[0] = VIA_GET_PROTOCOL_VERSION
    it[1] = (version and 0xFF).toByte()
    it[2] = ((version shr 8) and 0xFF).toByte()
}

private fun device(interfaces: List<InterfaceFact> = emptyList()) = UsbDeviceSnapshot(
    key = "usb-1",
    deviceName = "/dev/bus/usb/001/002",
    vendorId = CORNEDEON_VENDOR_ID,
    productId = CORNEDEON_PRODUCT_ID_V2M,
    manufacturer = "alko",
    productName = "Cornedeon v2m",
    permission = UsbPermission.GRANTED,
    interfaces = interfaces,
)

private class CountingUsb(
    private val devices: List<UsbDeviceSnapshot>,
    private val session: UsbSession,
) : UsbHardware {
    var opens = 0
    override fun listDevices(): List<UsbDeviceSnapshot> = devices
    override fun requestPermission(deviceKey: String, onResult: (Boolean) -> Unit) = onResult(true)
    override fun open(deviceKey: String): UsbSession? {
        opens += 1
        return session
    }
}

private class RecordingProbe(private val claim: ClaimResult) : UsbSession {
    val forces = mutableListOf<Boolean>()
    var closed = false
    override fun claimRawHid(allowForce: Boolean): ClaimResult {
        forces += allowForce
        return claim
    }

    override fun exchange(payload: ByteArray): HidReport = HidReport.Failed
    override fun close() {
        closed = true
    }
}

private class ClosingProbe(private val exchange: () -> HidReport) : UsbSession {
    var closed = false
    var calls = 0
    override fun claimRawHid(allowForce: Boolean) = ClaimResult(true, ProbeStatus.OK, "ok", gentleClaim = "true")
    override fun exchange(payload: ByteArray): HidReport {
        calls += 1
        return exchange()
    }

    override fun close() {
        closed = true
    }
}
