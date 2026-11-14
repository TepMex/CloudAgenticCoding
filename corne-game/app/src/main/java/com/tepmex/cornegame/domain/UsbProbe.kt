package com.tepmex.cornegame.domain

/**
 * Разбор USB-сведений и единственные read-only запросы VIA/Vial, которые диагностика шлёт.
 * Запросы на запись EEPROM, keymap и layer_state здесь отсутствуют: штатный VIA слой не отдаёт,
 * а неизвестные пакеты не отправляются.
 *
 * VID 0x4653 / PID 0x0009 — ориентир Cornedeon 2mod, не единственный допустимый идентификатор.
 */

const val CORNEDEON_VENDOR_ID = 0x4653
const val CORNEDEON_PRODUCT_ID = 0x0009
const val VIA_REPORT_BYTES = 32
const val VIA_GET_PROTOCOL_VERSION: Byte = 0x01
const val VIAL_PREFIX: Byte = 0xFE.toByte()
const val VIAL_GET_KEYBOARD_ID: Byte = 0x00

enum class UsbPermission {
    UNKNOWN,
    GRANTED,
    DENIED,
}

enum class HidRole {
    BOOT_KEYBOARD,
    RAW_HID,
    OTHER,
}

enum class ProbeStatus {
    NOT_RUN,
    NO_DEVICE,
    NO_PERMISSION,
    NO_INTERFACE,
    CLAIM_FAILED,
    PROTOCOL_MISMATCH,
    IO_ERROR,
    OK,
}

data class EndpointFact(
    val address: Int,
    val attributes: Int,
    val maxPacket: Int,
) {
    val direction: String get() = endpointDirection(address)
    val type: String get() = endpointType(attributes)
}

data class InterfaceFact(
    val id: Int,
    val classCode: Int,
    val subclass: Int,
    val protocol: Int,
    val endpoints: List<EndpointFact>,
) {
    val role: HidRole get() = hidRole(classCode, subclass, protocol, endpoints)
}

data class UsbDeviceSnapshot(
    val key: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val productName: String?,
    val permission: UsbPermission,
    val interfaces: List<InterfaceFact>,
    val openError: String? = null,
)

data class ProbeResult(
    val opened: Boolean,
    val status: ProbeStatus,
    val detail: String,
    val protocolVersion: Int? = null,
    val vialKeyboardId: String? = null,
)

data class UsbDashboard(
    val devices: List<UsbDeviceSnapshot> = emptyList(),
    val selectedKey: String? = null,
    val probe: ProbeResult = ProbeResult(
        opened = false,
        status = ProbeStatus.NOT_RUN,
        detail = "Проверка ещё не запускалась.",
    ),
    val firmware: FirmwareState = FirmwareState(),
    val log: List<String> = emptyList(),
)

fun endpointDirection(address: Int): String = if (address and 0x80 != 0) "IN" else "OUT"

fun endpointType(attributes: Int): String = when (attributes and 0x03) {
    0 -> "CONTROL"
    1 -> "ISOCHRONOUS"
    2 -> "BULK"
    3 -> "INTERRUPT"
    else -> "OTHER"
}

fun hidRole(classCode: Int, subclass: Int, protocol: Int, endpoints: List<EndpointFact>): HidRole {
    if (classCode != USB_CLASS_HID) return HidRole.OTHER
    if (subclass == 1 && protocol == 1) return HidRole.BOOT_KEYBOARD
    val interruptIn = endpoints.any { it.type == "INTERRUPT" && it.direction == "IN" && it.maxPacket >= VIA_REPORT_BYTES }
    val interruptOut = endpoints.any { it.type == "INTERRUPT" && it.direction == "OUT" && it.maxPacket >= VIA_REPORT_BYTES }
    return if (subclass == 0 && protocol == 0 && interruptIn && interruptOut) HidRole.RAW_HID else HidRole.OTHER
}

/** Boot-клавиатуру не выбираем: её захват сломал бы обычный ввод. force=true здесь не используется. */
fun chooseRawInterface(interfaces: List<InterfaceFact>): InterfaceFact? =
    interfaces.firstOrNull { it.role == HidRole.RAW_HID }

fun looksLikeCornedeon(
    vendorId: Int,
    productId: Int,
    manufacturer: String?,
    productName: String?,
    deviceName: String,
): Boolean {
    if (vendorId == CORNEDEON_VENDOR_ID && productId == CORNEDEON_PRODUCT_ID) return true
    val blob = listOfNotNull(manufacturer, productName, deviceName).joinToString(" ").lowercase()
    return "cornedeon" in blob || ("alko" in blob && "corne" in blob)
}

fun viaProtocolRequest(): ByteArray = ByteArray(VIA_REPORT_BYTES).also { it[0] = VIA_GET_PROTOCOL_VERSION }

fun vialKeyboardIdRequest(): ByteArray = ByteArray(VIA_REPORT_BYTES).also {
    it[0] = VIAL_PREFIX
    it[1] = VIAL_GET_KEYBOARD_ID
}

fun parseViaProtocolVersion(response: ByteArray): Int? {
    if (response.size < 3) return null
    if (response[0] != VIA_GET_PROTOCOL_VERSION) return null
    if (response.all { it == 0.toByte() }) return null
    val version = (response[1].toInt() and 0xFF) or ((response[2].toInt() and 0xFF) shl 8)
    if (version <= 0 || version > 0x0FFF) return null
    return version
}

fun parseVialKeyboardId(response: ByteArray): String? {
    if (response.size < 10) return null
    if (response[0] != VIAL_PREFIX || response[1] != VIAL_GET_KEYBOARD_ID) return null
    if (response.copyOfRange(2, 10).all { it == 0.toByte() }) return null
    return response.copyOfRange(2, 10).joinToString("") { byte -> "%02x".format(byte) }
}

interface UsbSession {
    fun claimRawHid(): ClaimResult
    fun exchange(payload: ByteArray): ByteArray?
    fun close()
}

data class ClaimResult(val ok: Boolean, val status: ProbeStatus, val detail: String)

/**
 * Сначала VIA get_protocol_version, затем Vial get_keyboard_id.
 * Оба запроса только читают. Если ответ не похож на протокол, следующие команды не шлются.
 * layer_state не запрашивается.
 */
fun runReadOnlyProbe(session: UsbSession): ProbeResult {
    try {
        val claim = session.claimRawHid()
        if (!claim.ok) {
            return ProbeResult(opened = true, status = claim.status, detail = claim.detail)
        }
        val versionBytes = session.exchange(viaProtocolRequest())
            ?: return ProbeResult(
                opened = true,
                status = ProbeStatus.IO_ERROR,
                detail = "Нет ответа на VIA get_protocol_version.",
            )
        val version = parseViaProtocolVersion(versionBytes)
            ?: return ProbeResult(
                opened = true,
                status = ProbeStatus.PROTOCOL_MISMATCH,
                detail = "Ответ не похож на VIA get_protocol_version: ${hex(versionBytes)}.",
            )
        val idBytes = session.exchange(vialKeyboardIdRequest())
        val vialId = idBytes?.let(::parseVialKeyboardId)
        val idNote = if (vialId != null) {
            "Vial keyboard id $vialId."
        } else {
            "Vial get_keyboard_id не подтверждён."
        }
        return ProbeResult(
            opened = true,
            status = ProbeStatus.OK,
            detail = "VIA protocol $version. $idNote Слой прошивки этим обменом не читается.",
            protocolVersion = version,
            vialKeyboardId = vialId,
        )
    } finally {
        session.close()
    }
}

fun firmwareFromProbe(result: ProbeResult): FirmwareState = when (result.status) {
    ProbeStatus.OK -> FirmwareState(
        connected = true,
        telemetry = Telemetry.UNAVAILABLE,
        activeLayersMask = null,
        source = "via-get-protocol-version",
        confidence = Confidence.UNKNOWN,
        error = null,
    )
    ProbeStatus.NOT_RUN, ProbeStatus.NO_DEVICE -> FirmwareState(
        connected = false,
        telemetry = Telemetry.UNKNOWN,
        activeLayersMask = null,
        source = "usb",
        confidence = Confidence.UNKNOWN,
        error = result.detail,
    )
    else -> FirmwareState(
        connected = result.opened,
        telemetry = Telemetry.UNAVAILABLE,
        activeLayersMask = null,
        source = "usb-probe",
        confidence = Confidence.UNKNOWN,
        error = result.detail,
    )
}

fun hex(bytes: ByteArray, limit: Int = 8): String =
    bytes.take(limit).joinToString(" ") { byte -> "%02x".format(byte) }

private const val USB_CLASS_HID = 3
