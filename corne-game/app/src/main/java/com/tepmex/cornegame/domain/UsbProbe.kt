package com.tepmex.cornegame.domain

/**
 * Разбор USB-сведений и единственные read-only запросы VIA/Vial, которые диагностика шлёт.
 * Запросы на запись EEPROM, keymap и layer_state здесь отсутствуют: этот обмен слой не отдаёт,
 * а неизвестные пакеты не отправляются.
 *
 * Живая Cornedeon v2m отвечает VID 0x4653 / PID 0x0007. PID 0x0009 — старый ориентир
 * дерева cornedeon_2mod, не фильтр и не единственный допустимый идентификатор.
 *
 * VIA `id_get_protocol_version` (QMK `quantum/via.c`) оставляет байт команды и пишет
 * `VIA_PROTOCOL_VERSION` как big-endian: `data[1] = version >> 8`, `data[2] = version & 0xFF`.
 * Пакет `01 00 09` — версия 9. Little-endian тех же байт даёт 2304 и к протоколу отношения не имеет.
 *
 * Vial `vial_get_keyboard_id` (vial-qmk `quantum/vial.c`) обнуляет буфер и пишет
 * `VIAL_PROTOCOL_VERSION` как uint32 little-endian в байты 0..3, затем 8 байт
 * `VIAL_KEYBOARD_UID` в байты 4..11. Префикс запроса `FE 00` в ответе не сохраняется.
 */

const val CORNEDEON_VENDOR_ID = 0x4653
const val CORNEDEON_PRODUCT_ID = 0x0007
const val CORNEDEON_LEGACY_PRODUCT_ID = 0x0009
const val VIA_REPORT_BYTES = 32
const val VIA_GET_PROTOCOL_VERSION: Byte = 0x01
const val VIAL_PREFIX: Byte = 0xFE.toByte()
const val VIAL_GET_KEYBOARD_ID: Byte = 0x00
const val VIAL_UID_LENGTH = 8
const val VIAL_VERSION_LENGTH = 4

/**
 * Поле `uid` в [cornedeon_v2m.vil](cornedeon_v2m.vil).
 * Число больше [Long.MAX_VALUE], поэтому хранится десятичной строкой, а не знаковым Long.
 */
const val CORNEDEON_VIAL_EXPORT_UID = "11156463090065812860"

/**
 * Реальные версии Vial — маленькие целые в младшем байте uint32. Сейчас у прошивки 6.
 * Потолок отсекает эхо запроса `FE 00` (как uint32 это 254) и не отвергает саму версию 6.
 */
const val VIAL_PROTOCOL_VERSION_MAX = 32L

private val CORNEDEON_PRODUCT_IDS = setOf(CORNEDEON_PRODUCT_ID, CORNEDEON_LEGACY_PRODUCT_ID)

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
    /** VIA разобран, идентичность Vial — нет. Это не полный успех. */
    PARTIAL,
    /** VIA и Vial разобраны. Сравнение UID и телеметрия слоёв живут отдельно. */
    OK,
}

enum class DiagnosticFlag {
    USB_CONNECTED,
    RAW_HID_ACCESSIBLE,
    VIA_VERIFIED,
    VIAL_VERIFIED,
    UID_MATCH,
    UID_MISMATCH,
    UID_UNKNOWN,
    LAYER_TELEMETRY_UNAVAILABLE,
}

enum class UidMatchResult {
    MATCH,
    MISMATCH,
    UNKNOWN,
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

/**
 * Идентичность, которую прошивка возвращает на `get_keyboard_id`.
 * [protocolVersion] — uint32, он помещается в неотрицательный [Long].
 * UID — ровно 8 байт. В знаковый Long он не упаковывается: экспорт Cornedeon больше [Long.MAX_VALUE].
 */
class VialIdentity(
    val protocolVersion: Long,
    uidBytes: ByteArray,
) {
    private val uidStorage: ByteArray = uidBytes.copyOf()
    val uidBytes: ByteArray get() = uidStorage.copyOf()

    init {
        require(protocolVersion in 1..VIAL_PROTOCOL_VERSION_MAX) {
            "версия протокола Vial вне диапазона: $protocolVersion"
        }
        require(uidStorage.size == VIAL_UID_LENGTH) {
            "UID Vial должен быть из $VIAL_UID_LENGTH байт"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VialIdentity) return false
        return protocolVersion == other.protocolVersion && uidStorage.contentEquals(other.uidStorage)
    }

    override fun hashCode(): Int = 31 * protocolVersion.hashCode() + uidStorage.contentHashCode()

    override fun toString(): String = "VialIdentity(protocolVersion=$protocolVersion, uid=${uidHex(uidStorage)})"
}

data class ProbeResult(
    val opened: Boolean,
    val status: ProbeStatus,
    val detail: String,
    val protocolVersion: Int? = null,
    val vial: VialIdentity? = null,
    val vialProtocolVersion: Long? = null,
    val partialUidHex: String? = null,
    val uidComparison: UidMatchResult = UidMatchResult.UNKNOWN,
    val interfaceId: Int? = null,
    val flags: Set<DiagnosticFlag> = emptySet(),
    val exchanges: List<HidExchangeRecord> = emptyList(),
)

data class HidExchangeRecord(
    val name: String,
    val requestHex: String,
    val responseHex: String?,
    val responseLength: Int?,
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
    if (vendorId == CORNEDEON_VENDOR_ID && productId in CORNEDEON_PRODUCT_IDS) return true
    val blob = listOfNotNull(manufacturer, productName, deviceName).joinToString(" ").lowercase()
    return "cornedeon" in blob || ("alko" in blob && "corne" in blob)
}

fun viaProtocolRequest(): ByteArray = ByteArray(VIA_REPORT_BYTES).also { it[0] = VIA_GET_PROTOCOL_VERSION }

fun vialKeyboardIdRequest(): ByteArray = ByteArray(VIA_REPORT_BYTES).also {
    it[0] = VIAL_PREFIX
    it[1] = VIAL_GET_KEYBOARD_ID
}

sealed class ViaVersionParse {
    data class Ok(val version: Int) : ViaVersionParse()
    data object Incomplete : ViaVersionParse()
    data class ProtocolError(val reason: String) : ViaVersionParse()
}

/**
 * Два байта версии — big-endian, как в QMK: старший байт в `response[1]`, младший в `response[2]`.
 * Меньше трёх байт — ответ оборван. Чужой первый байт или нулевая версия — ошибка протокола.
 */
fun parseViaProtocolVersion(response: ByteArray): ViaVersionParse {
    if (response.size < 3) return ViaVersionParse.Incomplete
    if (response[0] != VIA_GET_PROTOCOL_VERSION) {
        return ViaVersionParse.ProtocolError("первый байт не id_get_protocol_version (0x01)")
    }
    val version =
        ((response[1].toInt() and 0xFF) shl 8) or
            (response[2].toInt() and 0xFF)
    if (version <= 0) return ViaVersionParse.ProtocolError("версия VIA равна 0")
    return ViaVersionParse.Ok(version)
}

sealed class VialIdentityParse {
    data class Ok(val identity: VialIdentity) : VialIdentityParse()
    data class Incomplete(
        val protocolVersion: Long?,
        val uidHex: String?,
        val uidLength: Int,
    ) : VialIdentityParse()
    data class ProtocolError(val reason: String) : VialIdentityParse()
}

/**
 * Ответ `get_keyboard_id` не начинается с `FE 00`: прошивка затирает буфер запроса.
 * Версия — uint32 little-endian. UID короче 8 байт не считается идентичностью и не сравнивается как совпадение.
 */
fun parseVialKeyboardId(response: ByteArray): VialIdentityParse {
    if (response.size < VIAL_VERSION_LENGTH) {
        return VialIdentityParse.Incomplete(protocolVersion = null, uidHex = null, uidLength = 0)
    }
    val version = readUint32Le(response, 0)
    if (version == 0L || version > VIAL_PROTOCOL_VERSION_MAX) {
        return VialIdentityParse.ProtocolError(
            "версия протокола Vial $version вне диапазона 1..$VIAL_PROTOCOL_VERSION_MAX",
        )
    }
    val available = response.size - VIAL_VERSION_LENGTH
    if (available < VIAL_UID_LENGTH) {
        val partial = response.copyOfRange(VIAL_VERSION_LENGTH, response.size)
        return VialIdentityParse.Incomplete(
            protocolVersion = version,
            uidHex = if (partial.isEmpty()) null else uidHex(partial),
            uidLength = partial.size,
        )
    }
    val uid = response.copyOfRange(VIAL_VERSION_LENGTH, VIAL_VERSION_LENGTH + VIAL_UID_LENGTH)
    return VialIdentityParse.Ok(VialIdentity(version, uid))
}

/** Десятичное поле `uid` из текста `.vil`. JSON-число здесь не используется: оно не помещается в Long. */
fun vialExportUidDecimal(vilText: String): String {
    val match = VIL_UID.find(vilText)
        ?: throw IllegalArgumentException("В выгрузке Vial нет беззнакового поля uid")
    return match.groupValues[1]
}

/** Little-endian байты беззнакового 64-битного UID. Порядок тот же, что у `VIAL_KEYBOARD_UID` в прошивке. */
fun vialUidBytes(unsignedDecimal: String): ByteArray {
    val value = unsignedDecimal.trim().toULong()
    return ByteArray(VIAL_UID_LENGTH) { index ->
        ((value shr (8 * index)) and 0xFFu).toByte()
    }
}

fun cornedeonVialUidBytes(): ByteArray = vialUidBytes(CORNEDEON_VIAL_EXPORT_UID)

/**
 * Совпадение только при двух полных UID по 8 байт.
 * Короткий ответ остаётся [UidMatchResult.UNKNOWN], даже если его префикс совпал с экспортом.
 */
fun compareKeyboardUid(reported: ByteArray?, exportUid: ByteArray): UidMatchResult {
    if (reported == null || reported.size != VIAL_UID_LENGTH || exportUid.size != VIAL_UID_LENGTH) {
        return UidMatchResult.UNKNOWN
    }
    return if (reported.contentEquals(exportUid)) UidMatchResult.MATCH else UidMatchResult.MISMATCH
}

fun uidComparisonText(result: UidMatchResult, deviceHex: String?, exportHex: String): String = when (result) {
    UidMatchResult.MATCH -> "UID совпадает с экспортом Vial"
    UidMatchResult.MISMATCH ->
        "Предупреждение: UID не совпадает с экспортом Vial. " +
            "Keyboard UID: ${deviceHex ?: "—"} Vial export UID: $exportHex. " +
            "Диагностика других клавиатур Vial не блокируется."
    UidMatchResult.UNKNOWN -> "Сравнение UID неизвестно."
}

fun diagnosticFlags(
    opened: Boolean,
    rawHid: Boolean,
    viaVerified: Boolean,
    vialVerified: Boolean,
    uid: UidMatchResult,
): Set<DiagnosticFlag> {
    val flags = linkedSetOf<DiagnosticFlag>()
    if (opened) flags += DiagnosticFlag.USB_CONNECTED
    if (rawHid) flags += DiagnosticFlag.RAW_HID_ACCESSIBLE
    if (viaVerified) flags += DiagnosticFlag.VIA_VERIFIED
    if (vialVerified) flags += DiagnosticFlag.VIAL_VERIFIED
    when (uid) {
        UidMatchResult.MATCH -> flags += DiagnosticFlag.UID_MATCH
        UidMatchResult.MISMATCH -> flags += DiagnosticFlag.UID_MISMATCH
        UidMatchResult.UNKNOWN -> flags += DiagnosticFlag.UID_UNKNOWN
    }
    flags += DiagnosticFlag.LAYER_TELEMETRY_UNAVAILABLE
    return flags
}

fun usbDiagnosticsReport(
    devicePresent: Boolean,
    probe: ProbeResult,
    activeLayersMask: Int? = null,
): String {
    val exportHex = uidHex(cornedeonVialUidBytes())
    val deviceHex = probe.vial?.let { uidHex(it.uidBytes) }
    val shownDeviceHex = deviceHex ?: probe.partialUidHex
    val rawHid = when {
        DiagnosticFlag.RAW_HID_ACCESSIBLE in probe.flags -> "ACCESSIBLE"
        probe.status == ProbeStatus.NOT_RUN -> "—"
        else -> "NOT ACCESSIBLE"
    }
    val usb = if (devicePresent || DiagnosticFlag.USB_CONNECTED in probe.flags) "CONNECTED" else "NOT CONNECTED"
    val check = when (probe.status) {
        ProbeStatus.NOT_RUN -> "ещё не запускалась"
        ProbeStatus.OK -> "VIA и Vial разобраны"
        ProbeStatus.PARTIAL -> "частично, Vial не подтверждён"
        else -> probe.status.name
    }
    return buildString {
        append("USB: ")
        append(usb)
        append('\n')
        append("Raw HID: ")
        append(rawHid)
        append('\n')
        append("Interface: ")
        append(probe.interfaceId?.toString() ?: "—")
        append("\n\n")
        append("VIA protocol: ")
        append(probe.protocolVersion?.toString() ?: "—")
        append('\n')
        append("Vial protocol: ")
        append(probe.vialProtocolVersion?.toString() ?: "—")
        append("\n\n")
        append("Keyboard UID:\n")
        append(shownDeviceHex ?: "—")
        append('\n')
        if (probe.vial == null && probe.partialUidHex != null) {
            append("(получен неполный UID, сравнение неизвестно)\n")
        }
        append('\n')
        append("Vial export UID:\n")
        append(exportHex)
        append("\n\n")
        append("UID match: ")
        append(
            when (probe.uidComparison) {
                UidMatchResult.MATCH -> "YES"
                UidMatchResult.MISMATCH -> "NO"
                UidMatchResult.UNKNOWN -> "UNKNOWN"
            },
        )
        append('\n')
        append(uidComparisonText(probe.uidComparison, shownDeviceHex, exportHex))
        append("\n\n")
        append("Physical layer telemetry:\nNOT IMPLEMENTED\n")
        append("Active layer mask: ")
        append(activeLayersMask?.let { mask -> "0x${mask.toString(16)}" } ?: "UNKNOWN")
        append("\n\n")
        append("Проверка: ")
        append(check)
        append('\n')
    }
}

interface UsbSession {
    fun claimRawHid(): ClaimResult
    fun exchange(payload: ByteArray): ByteArray?
    fun close()
}

data class ClaimResult(
    val ok: Boolean,
    val status: ProbeStatus,
    val detail: String,
    val interfaceId: Int? = null,
)

/**
 * Сначала VIA get_protocol_version, затем Vial get_keyboard_id.
 * Оба запроса только читают. Если VIA не разобран, Vial не запрашивается.
 * layer_state не запрашивается и [FirmwareState.activeLayersMask] отсюда не заполняется.
 * Несовпадение UID с экспортом Cornedeon не отменяет уже разобранную идентичность Vial.
 */
fun runReadOnlyProbe(
    session: UsbSession,
    exportUid: ByteArray = cornedeonVialUidBytes(),
): ProbeResult {
    val exchanges = mutableListOf<HidExchangeRecord>()
    try {
        val claim = session.claimRawHid()
        if (!claim.ok) {
            return assembleProbe(
                opened = true,
                status = claim.status,
                detail = claim.detail,
                interfaceId = claim.interfaceId,
                exchanges = exchanges,
            )
        }
        val viaRequest = viaProtocolRequest()
        val versionBytes = session.exchange(viaRequest)
        exchanges += hidExchangeRecord("VIA get_protocol_version", viaRequest, versionBytes)
        if (versionBytes == null) {
            return assembleProbe(
                opened = true,
                status = ProbeStatus.IO_ERROR,
                detail = "Нет ответа на VIA get_protocol_version.",
                interfaceId = claim.interfaceId,
                rawHid = true,
                exchanges = exchanges,
            )
        }
        val viaVersion = when (val via = parseViaProtocolVersion(versionBytes)) {
            is ViaVersionParse.Ok -> via.version
            ViaVersionParse.Incomplete -> {
                return assembleProbe(
                    opened = true,
                    status = ProbeStatus.PROTOCOL_MISMATCH,
                    detail = "Неполный ответ VIA get_protocol_version (${versionBytes.size} байт).",
                    interfaceId = claim.interfaceId,
                    rawHid = true,
                    exchanges = exchanges,
                )
            }
            is ViaVersionParse.ProtocolError -> {
                return assembleProbe(
                    opened = true,
                    status = ProbeStatus.PROTOCOL_MISMATCH,
                    detail = "Ошибка протокола VIA get_protocol_version: ${via.reason}.",
                    interfaceId = claim.interfaceId,
                    rawHid = true,
                    exchanges = exchanges,
                )
            }
        }
        val vialRequest = vialKeyboardIdRequest()
        val idBytes = session.exchange(vialRequest)
        exchanges += hidExchangeRecord("Vial get_keyboard_id", vialRequest, idBytes)
        if (idBytes == null) {
            return assembleProbe(
                opened = true,
                status = ProbeStatus.PARTIAL,
                detail = "VIA protocol $viaVersion. Ответ Vial get_keyboard_id не получен.",
                interfaceId = claim.interfaceId,
                rawHid = true,
                viaVersion = viaVersion,
                exchanges = exchanges,
            )
        }
        return when (val parsed = parseVialKeyboardId(idBytes)) {
            is VialIdentityParse.Ok -> {
                val uid = compareKeyboardUid(parsed.identity.uidBytes, exportUid)
                val note = uidComparisonText(uid, uidHex(parsed.identity.uidBytes), uidHex(exportUid))
                assembleProbe(
                    opened = true,
                    status = ProbeStatus.OK,
                    detail = "VIA protocol $viaVersion. Vial protocol ${parsed.identity.protocolVersion}. " +
                        "$note Физический слой этим обменом не читается.",
                    interfaceId = claim.interfaceId,
                    rawHid = true,
                    viaVersion = viaVersion,
                    vial = parsed.identity,
                    uid = uid,
                    exchanges = exchanges,
                )
            }
            is VialIdentityParse.Incomplete -> assembleProbe(
                opened = true,
                status = ProbeStatus.PARTIAL,
                detail = "VIA protocol $viaVersion. Ответ Vial неполный: версия " +
                    "${parsed.protocolVersion ?: "не прочитана"}, UID ${parsed.uidLength} из $VIAL_UID_LENGTH байт.",
                interfaceId = claim.interfaceId,
                rawHid = true,
                viaVersion = viaVersion,
                vialProtocolVersion = parsed.protocolVersion,
                partialUidHex = parsed.uidHex,
                exchanges = exchanges,
            )
            is VialIdentityParse.ProtocolError -> assembleProbe(
                opened = true,
                status = ProbeStatus.PARTIAL,
                detail = "VIA protocol $viaVersion. Ошибка протокола Vial get_keyboard_id: ${parsed.reason}.",
                interfaceId = claim.interfaceId,
                rawHid = true,
                viaVersion = viaVersion,
                exchanges = exchanges,
            )
        }
    } finally {
        session.close()
    }
}

fun firmwareFromProbe(result: ProbeResult): FirmwareState = when (result.status) {
    ProbeStatus.OK, ProbeStatus.PARTIAL -> FirmwareState(
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

fun hidExchangeRecord(name: String, request: ByteArray, response: ByteArray?): HidExchangeRecord =
    HidExchangeRecord(
        name = name,
        requestHex = hex(request, request.size),
        responseHex = response?.let { hex(it, it.size) },
        responseLength = response?.size,
    )

fun formatHidExchange(record: HidExchangeRecord): String = buildString {
    append("Request: ")
    append(record.name)
    append('\n')
    append("TX: ")
    append(record.requestHex)
    append('\n')
    if (record.responseHex == null || record.responseLength == null) {
        append("RX: нет ответа")
    } else {
        append("RX (")
        append(record.responseLength)
        append(" bytes): ")
        append(record.responseHex)
    }
}

fun formatHidExchanges(records: List<HidExchangeRecord>): String =
    records.joinToString("\n\n") { formatHidExchange(it) }

fun hex(bytes: ByteArray, limit: Int = 8): String =
    bytes.take(limit).joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

fun uidHex(bytes: ByteArray): String =
    bytes.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }

internal fun readUint32Le(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (shift in 0 until 4) {
        value = value or ((bytes[offset + shift].toLong() and 0xFFL) shl (8 * shift))
    }
    return value
}

private fun assembleProbe(
    opened: Boolean,
    status: ProbeStatus,
    detail: String,
    interfaceId: Int? = null,
    rawHid: Boolean = false,
    viaVersion: Int? = null,
    vialProtocolVersion: Long? = null,
    vial: VialIdentity? = null,
    partialUidHex: String? = null,
    uid: UidMatchResult = UidMatchResult.UNKNOWN,
    exchanges: List<HidExchangeRecord> = emptyList(),
): ProbeResult {
    val resolvedUid = if (vial == null) UidMatchResult.UNKNOWN else uid
    return ProbeResult(
        opened = opened,
        status = status,
        detail = detail,
        protocolVersion = viaVersion,
        vial = vial,
        vialProtocolVersion = vialProtocolVersion ?: vial?.protocolVersion,
        partialUidHex = partialUidHex,
        uidComparison = resolvedUid,
        interfaceId = interfaceId,
        flags = diagnosticFlags(
            opened = opened,
            rawHid = rawHid,
            viaVerified = viaVersion != null,
            vialVerified = vial != null,
            uid = resolvedUid,
        ),
        exchanges = exchanges,
    )
}

private val VIL_UID = Regex(""""uid"\s*:\s*(\d+)""")

private const val USB_CLASS_HID = 3
