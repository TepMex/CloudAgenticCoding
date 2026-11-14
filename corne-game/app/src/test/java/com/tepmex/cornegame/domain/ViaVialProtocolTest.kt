package com.tepmex.cornegame.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViaVialProtocolTest {
    @Test
    fun viaPacket010009IsProtocolVersion9() {
        val short = byteArrayOf(0x01, 0x00, 0x09)
        assertEquals(ViaVersionParse.Ok(9), parseViaProtocolVersion(short))
        val report = ByteArray(32).also {
            it[0] = 0x01
            it[1] = 0x00
            it[2] = 0x09
        }
        assertEquals(ViaVersionParse.Ok(9), parseViaProtocolVersion(report))
        val littleEndianNine = byteArrayOf(0x01, 0x09, 0x00)
        assertEquals(ViaVersionParse.Ok(0x0900), parseViaProtocolVersion(littleEndianNine))
        assertNotEquals(ViaVersionParse.Ok(9), parseViaProtocolVersion(littleEndianNine))
    }

    @Test
    fun incompleteViaResponseIsAnErrorAndNotAVersion() {
        assertEquals(ViaVersionParse.Incomplete, parseViaProtocolVersion(byteArrayOf()))
        assertEquals(ViaVersionParse.Incomplete, parseViaProtocolVersion(byteArrayOf(0x01)))
        assertEquals(ViaVersionParse.Incomplete, parseViaProtocolVersion(byteArrayOf(0x01, 0x00)))
        val session = ScriptSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
            replies = listOf(byteArrayOf(0x01, 0x00)),
        )
        val result = runReadOnlyProbe(session)
        assertEquals(ProbeStatus.PROTOCOL_MISMATCH, result.status)
        assertNull(result.protocolVersion)
        assertFalse(DiagnosticFlag.VIA_VERIFIED in result.flags)
        assertEquals(1, session.payloads.size)
        assertTrue(session.closed)
        assertNull(firmwareFromProbe(result).activeLayersMask)
    }

    @Test
    fun malformedViaResponseIsAProtocolError() {
        val foreign = byteArrayOf(0x05, 0x00, 0x09)
        val parsed = parseViaProtocolVersion(foreign)
        assertTrue(parsed is ViaVersionParse.ProtocolError)
        assertEquals(ViaVersionParse.ProtocolError("версия VIA равна 0"), parseViaProtocolVersion(byteArrayOf(0x01, 0x00, 0x00)))
        val session = ScriptSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
            replies = listOf(foreign),
        )
        val result = runReadOnlyProbe(session)
        assertEquals(ProbeStatus.PROTOCOL_MISMATCH, result.status)
        assertNull(result.protocolVersion)
        assertEquals(1, session.payloads.size)
        assertTrue(session.closed)
        assertTrue(result.detail.contains("Ошибка протокола"))
    }

    @Test
    fun vialPacketStartingWith06000000IsProtocolVersion6() {
        val response = ByteArray(32).also { it[0] = 0x06 }
        val parsed = parseVialKeyboardId(response) as VialIdentityParse.Ok
        assertEquals(6L, parsed.identity.protocolVersion)
        assertFalse(response[0] == VIAL_PREFIX && response[1] == VIAL_GET_KEYBOARD_ID)
        val versionOnly = byteArrayOf(0x06, 0x00, 0x00, 0x00)
        val incomplete = parseVialKeyboardId(versionOnly) as VialIdentityParse.Incomplete
        assertEquals(6L, incomplete.protocolVersion)
        assertEquals(0, incomplete.uidLength)
    }

    @Test
    fun fullUid7c2d212912b8d39aParsesFromTheRealVilExport() {
        val export = vialUidBytes(vialExportUidDecimal(cornedeonVil().readText()))
        assertEquals("7C2D212912B8D39A", uidHex(export))
        val response = ByteArray(32).also { packet ->
            packet[0] = 0x06
            export.copyInto(packet, destinationOffset = 4)
        }
        val parsed = parseVialKeyboardId(response) as VialIdentityParse.Ok
        assertEquals(6L, parsed.identity.protocolVersion)
        assertEquals(export.toList(), parsed.identity.uidBytes.toList())
        val again = VialIdentity(parsed.identity.protocolVersion, parsed.identity.uidBytes)
        assertEquals(parsed.identity, again)
        assertEquals(parsed.identity.hashCode(), again.hashCode())
    }

    @Test
    fun comparingUsbUidWithVilDecimal11156463090065812860Matches() {
        val text = cornedeonVil().readText()
        val decimal = vialExportUidDecimal(text)
        assertEquals(CORNEDEON_VIAL_EXPORT_UID, decimal)
        assertTrue(decimal.toULong() > Long.MAX_VALUE.toULong())
        try {
            decimal.toLong()
            throw AssertionError("знаковый Long не должен принимать UID экспорта")
        } catch (_: NumberFormatException) {
            // Значение больше Long.MAX_VALUE.
        }
        val export = vialUidBytes(decimal)
        val device = ByteArray(VIAL_UID_LENGTH).also { export.copyInto(it) }
        assertEquals(UidMatchResult.MATCH, compareKeyboardUid(device, export))
        val probe = runReadOnlyProbe(
            ScriptSession(
                claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
                replies = listOf(viaNine(), vialReport(export)),
            ),
            exportUid = export,
        )
        assertEquals(ProbeStatus.OK, probe.status)
        assertEquals(UidMatchResult.MATCH, probe.uidComparison)
        assertTrue(DiagnosticFlag.UID_MATCH in probe.flags)
        assertTrue(probe.detail.contains("UID совпадает с экспортом Vial"))
        val report = usbDiagnosticsReport(devicePresent = true, probe = probe, activeLayersMask = null)
        assertTrue(report.contains("USB: CONNECTED"))
        assertTrue(report.contains("Raw HID: ACCESSIBLE"))
        assertTrue(report.contains("Interface: 1"))
        assertTrue(report.contains("VIA protocol: 9"))
        assertTrue(report.contains("Vial protocol: 6"))
        assertTrue(report.contains("Keyboard UID:\n7C2D212912B8D39A"))
        assertTrue(report.contains("Vial export UID:\n7C2D212912B8D39A"))
        assertTrue(report.contains("UID match: YES"))
        assertTrue(report.contains("UID совпадает с экспортом Vial"))
        assertTrue(report.contains("Physical layer telemetry:\nNOT IMPLEMENTED"))
        assertTrue(report.contains("Active layer mask: UNKNOWN"))
        assertFalse(report.contains("UID match: NO"))
        assertFalse(report.contains("2304"))
    }

    @Test
    fun changedUidIsMismatchAndDoesNotBlockTheProbe() {
        val export = vialUidBytes(vialExportUidDecimal(cornedeonVil().readText()))
        val other = export.copyOf().also { it[7] = (it[7].toInt() xor 0xFF).toByte() }
        assertEquals(UidMatchResult.MISMATCH, compareKeyboardUid(other, export))
        val probe = runReadOnlyProbe(
            ScriptSession(
                claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
                replies = listOf(viaNine(), vialReport(other)),
            ),
        )
        assertEquals(ProbeStatus.OK, probe.status)
        assertEquals(6L, probe.vialProtocolVersion)
        assertEquals(UidMatchResult.MISMATCH, probe.uidComparison)
        assertTrue(DiagnosticFlag.VIAL_VERIFIED in probe.flags)
        assertTrue(DiagnosticFlag.UID_MISMATCH in probe.flags)
        assertFalse(DiagnosticFlag.UID_MATCH in probe.flags)
        val report = usbDiagnosticsReport(devicePresent = true, probe = probe)
        assertTrue(report.contains("UID match: NO"))
        assertTrue(report.contains(uidHex(other)))
        assertTrue(report.contains(uidHex(export)))
        assertTrue(report.contains("Предупреждение"))
        assertTrue(report.contains("не блокируется"))
        assertNull(firmwareFromProbe(probe).activeLayersMask)
    }

    @Test
    fun partialUidStaysUnknownAndIsNotAMatch() {
        val export = cornedeonVialUidBytes()
        val partial = byteArrayOf(0x06, 0x00, 0x00, 0x00, 0x7C, 0x2D, 0x21, 0x29)
        val parsed = parseVialKeyboardId(partial) as VialIdentityParse.Incomplete
        assertEquals(6L, parsed.protocolVersion)
        assertEquals(4, parsed.uidLength)
        assertEquals("7C2D2129", parsed.uidHex)
        assertEquals(UidMatchResult.UNKNOWN, compareKeyboardUid(partial.copyOfRange(4, 8), export))
        val probe = runReadOnlyProbe(
            ScriptSession(
                claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
                replies = listOf(viaNine(), partial),
            ),
        )
        assertEquals(ProbeStatus.PARTIAL, probe.status)
        assertNull(probe.vial)
        assertEquals(UidMatchResult.UNKNOWN, probe.uidComparison)
        assertFalse(DiagnosticFlag.UID_MATCH in probe.flags)
        assertFalse(DiagnosticFlag.VIAL_VERIFIED in probe.flags)
        assertTrue(DiagnosticFlag.UID_UNKNOWN in probe.flags)
        val report = usbDiagnosticsReport(devicePresent = true, probe = probe)
        assertTrue(report.contains("UID match: UNKNOWN"))
        assertFalse(report.contains("UID match: YES"))
        assertFalse(report.contains("UID match: NO"))
    }

    @Test
    fun viaWithoutVialResponseIsPartialNotFullSuccess() {
        val session = ScriptSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
            replies = listOf(viaNine(), null),
        )
        val probe = runReadOnlyProbe(session)
        assertEquals(ProbeStatus.PARTIAL, probe.status)
        assertEquals(9, probe.protocolVersion)
        assertNull(probe.vial)
        assertNull(probe.vialProtocolVersion)
        assertEquals(UidMatchResult.UNKNOWN, probe.uidComparison)
        assertTrue(DiagnosticFlag.VIA_VERIFIED in probe.flags)
        assertTrue(DiagnosticFlag.RAW_HID_ACCESSIBLE in probe.flags)
        assertFalse(DiagnosticFlag.VIAL_VERIFIED in probe.flags)
        assertEquals(2, session.payloads.size)
        assertEquals(VIAL_PREFIX, session.payloads[1][0])
        assertTrue(session.closed)
        val report = usbDiagnosticsReport(devicePresent = true, probe = probe)
        assertTrue(report.contains("VIA protocol: 9"))
        assertTrue(report.contains("Vial protocol: —"))
        assertTrue(report.contains("частично, Vial не подтверждён"))
        assertFalse(report.contains("VIA и Vial разобраны"))
        val firmware = firmwareFromProbe(probe)
        assertNull(firmware.activeLayersMask)
        assertEquals(Telemetry.UNAVAILABLE, firmware.telemetry)
        assertEquals(Confidence.UNKNOWN, firmware.confidence)
    }

    @Test
    fun successfulVialResponseDoesNotInventALayerMask() {
        val export = cornedeonVialUidBytes()
        val session = ScriptSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
            replies = listOf(viaNine(), vialReport(export)),
        )
        val probe = runReadOnlyProbe(session)
        assertEquals(ProbeStatus.OK, probe.status)
        assertTrue(DiagnosticFlag.LAYER_TELEMETRY_UNAVAILABLE in probe.flags)
        val firmware = firmwareFromProbe(probe)
        assertNull(firmware.activeLayersMask)
        assertEquals(Telemetry.UNAVAILABLE, firmware.telemetry)
        assertNotEquals(Telemetry.AVAILABLE, firmware.telemetry)
        assertEquals(Confidence.UNKNOWN, firmware.confidence)
        val report = usbDiagnosticsReport(
            devicePresent = true,
            probe = probe,
            activeLayersMask = firmware.activeLayersMask,
        )
        assertTrue(report.contains("Active layer mask: UNKNOWN"))
        assertTrue(report.contains("NOT IMPLEMENTED"))
        assertFalse(report.contains("0x"))
        assertTrue(session.closed)
    }

    @Test
    fun copyableExchangeLogKeepsTheWholeResponseAndItsLength() {
        val via = viaNine().also { it[31] = 0x9A.toByte() }
        val uid = cornedeonVialUidBytes()
        val vial = vialReport(uid).also { it[31] = 0xAB.toByte() }
        val session = ScriptSession(
            claim = ClaimResult(true, ProbeStatus.OK, "ok", interfaceId = 1),
            replies = listOf(via, vial),
        )
        val probe = runReadOnlyProbe(session)
        val log = formatHidExchanges(probe.exchanges)
        assertTrue(log.contains("Request: VIA get_protocol_version"))
        assertTrue(log.contains("Request: Vial get_keyboard_id"))
        assertTrue(log.contains("TX: FE 00"))
        val viaRx = rxLine(log, "VIA get_protocol_version")
        val vialRx = rxLine(log, "Vial get_keyboard_id")
        assertTrue(viaRx.startsWith("RX (32 bytes): "))
        assertTrue(vialRx.startsWith("RX (32 bytes): "))
        assertEquals(32, viaRx.removePrefix("RX (32 bytes): ").split(" ").size)
        assertEquals(32, vialRx.removePrefix("RX (32 bytes): ").split(" ").size)
        assertEquals("9A", viaRx.substringAfterLast(" "))
        assertEquals("AB", vialRx.substringAfterLast(" "))
        assertTrue(vialRx.contains("7C 2D 21 29 12 B8 D3 9A"))
        assertFalse(log.contains("chars="))
        assertTrue(session.closed)
    }

    @Test
    fun zeroedVialBufferIsAProtocolErrorAndFePrefixIsOnlyTheRequest() {
        val request = vialKeyboardIdRequest()
        assertEquals(VIAL_PREFIX, request[0])
        assertEquals(VIAL_GET_KEYBOARD_ID, request[1])
        assertTrue(parseVialKeyboardId(ByteArray(32)) is VialIdentityParse.ProtocolError)
        assertTrue(parseVialKeyboardId(request) is VialIdentityParse.ProtocolError)
        assertTrue(parseVialKeyboardId(byteArrayOf(0x06, 0x00)) is VialIdentityParse.Incomplete)
    }
}

private fun cornedeonVil(): File {
    val candidates = listOf(
        File("../cornedeon_v2m.vil"),
        File("cornedeon_v2m.vil"),
        File("corne-game/cornedeon_v2m.vil"),
    )
    return candidates.firstOrNull { it.isFile }
        ?: error("cornedeon_v2m.vil не найден из ${File(".").absolutePath}")
}

private fun viaNine(): ByteArray = ByteArray(32).also {
    it[0] = VIA_GET_PROTOCOL_VERSION
    it[1] = 0x00
    it[2] = 0x09
}

private fun vialReport(uid: ByteArray): ByteArray = ByteArray(32).also { packet ->
    packet[0] = 0x06
    uid.copyInto(packet, destinationOffset = 4)
}

private fun rxLine(log: String, requestName: String): String {
    val block = log.substringAfter("Request: $requestName")
    return block.lineSequence().first { it.startsWith("RX (") }
}

private class ScriptSession(
    private val claim: ClaimResult,
    private val replies: List<ByteArray?>,
) : UsbSession {
    val payloads = mutableListOf<ByteArray>()
    var closed = false

    override fun claimRawHid(): ClaimResult = claim

    override fun exchange(payload: ByteArray): ByteArray? {
        val reply = replies.getOrNull(payloads.size)
        payloads += payload.copyOf()
        return reply?.copyOf()
    }

    override fun close() {
        closed = true
    }
}
