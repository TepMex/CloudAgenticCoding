package com.tepmex.cornegame.domain

/**
 * Разбор USB-сведений и единственные read-only запросы VIA/Vial, которые диагностика шлёт.
 * Запросы на запись EEPROM, keymap и layer_state здесь отсутствуют.
 *
 * VID 0x4653 / PID 0x0009 — ориентир Cornedeon 2mod. PID 0x0007 — живая Cornedeon v2m.
 * Оба узнаются, ни один не является фильтром.
 *
 * VIA `id_get_protocol_version` (QMK `quantum/via.c`) пишет версию big-endian:
 * `data[1] = version >> 8`, `data[2] = version & 0xFF`. Пакет `01 00 09` — версия 9.
 *
 * Vial `vial_get_keyboard_id` (vial-qmk `quantum/vial.c`) затирает буфер запроса.
 * Байты 0..3 — версия протокола, uint32 little-endian. Байты 4..11 — UID.
 * Префикс запроса `FE 00` в ответе не сохраняется.
 *
 * `claimInterface(force=true)` планируется только для Raw HID и только после явного
 * подтверждения, следом за неудачным `force=false`. Boot-клавиатура в план не входит.
 */

const val CORNEDEON_VENDOR_ID = 0x4653
const val CORNEDEON_PRODUCT_ID = 0x0009
/** Живая Cornedeon v2m из USB-дескриптора. 0x0009 остаётся ориентиром прошивки 2mod. */
const val CORNEDEON_PRODUCT_ID_V2M = 0x0007
const val VIA_REPORT_BYTES = 32
const val VIA_GET_PROTOCOL_VERSION: Byte = 0x01
const val VIAL_PREFIX: Byte = 0xFE.toByte()
const val VIAL_GET_KEYBOARD_ID: Byte = 0x00
const val VIAL_UID_LENGTH = 8
const val VIAL_VERSION_LENGTH = 4

/**
 * Поле `uid` в cornedeon_v2m.vil.
 * Число больше [Long.MAX_VALUE], поэтому хранится десятичной строкой, а не знаковым Long.
 */
const val CORNEDEON_VIAL_EXPORT_UID = "11156463090065812860"

/**
 * Реальные версии Vial — маленькие целые в младшем байте uint32. Сейчас у прошивки 6.
 * Потолок отсекает эхо запроса `FE 00` (как uint32 это 254) и не отвергает саму версию 6.
 */
const val VIAL_PROTOCOL_VERSION_MAX = 32L

/** VIA-версия шире этого значения не считается совместимой с read-only проверкой. */
const val VIA_PROTOCOL_VERSION_MAX = 0x0FFF

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
    INCOMPATIBLE_VERSION,
    TIMEOUT,
    NO_RESPONSE,
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
 * UID — ровно 8 байт и в знаковый Long не упаковывается.
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
    val vialKeyboardId: String? = null,
    val rawInterfaceId: Int? = null,
    val gentleClaim: String = "не выполнялся",
    val forceClaim: String = "не запрашивался",
    val exchangeNote: String = "не выполнялся",
    val viaNote: String = "не запрашивался",
    val vialNote: String = "не запрашивался",
    val recovery: String? = null,
    val vial: VialIdentity? = null,
    val vialProtocolVersion: Long? = null,
    val partialUidHex: String? = null,
    val uidComparison: UidMatchResult = UidMatchResult.UNKNOWN,
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

/** Boot-клавиатуру и интерфейс без пары interrupt IN/OUT не выбираем. */
fun chooseRawInterface(interfaces: List<InterfaceFact>): InterfaceFact? =
    interfaces.firstOrNull { it.role == HidRole.RAW_HID }

/**
 * Совпадает ли живой интерфейс с выбранным дескриптором Raw HID.
 * Сравнение идёт по классу, протоколу и эндпоинтам, не по порядковому номеру.
 */
fun matchesRawCandidate(candidate: InterfaceFact, actual: InterfaceFact): Boolean =
    candidate.role == HidRole.RAW_HID &&
        actual.role == HidRole.RAW_HID &&
        candidate.id == actual.id &&
        candidate.classCode == actual.classCode &&
        candidate.subclass == actual.subclass &&
        candidate.protocol == actual.protocol &&
        candidate.endpoints == actual.endpoints

/**
 * Порядок [android.hardware.usb.UsbDeviceConnection.claimInterface]. true в списке — это force=true.
 * Boot-клавиатура и любой не-Raw HID дают пустой план: claimInterface не вызывается.
 * force=true есть в плане только после явного подтверждения и только следом за force=false.
 */
fun claimForces(role: HidRole, userConfirmedForce: Boolean): List<Boolean> {
    if (role != HidRole.RAW_HID) return emptyList()
    return if (userConfirmedForce) listOf(false, true) else listOf(false)
}

data class ClaimAttempt(val force: Boolean, val ok: Boolean)

/** Останавливается на первом успехе, поэтому force=true не вызывается, если хватило force=false. */
fun runClaimPlan(
    role: HidRole,
    userConfirmedForce: Boolean,
    tryClaim: (force: Boolean) -> Boolean,
): List<ClaimAttempt> {
    val log = mutableListOf<ClaimAttempt>()
    for (force in claimForces(role, userConfirmedForce)) {
        val ok = tryClaim(force)
        log += ClaimAttempt(force, ok)
        if (ok) break
    }
    return log
}

fun looksLikeCornedeon(
    vendorId: Int,
    productId: Int,
    manufacturer: String?,
    productName: String?,
    deviceName: String,
): Boolean {
    if (vendorId == CORNEDEON_VENDOR_ID &&
        (productId == CORNEDEON_PRODUCT_ID || productId == CORNEDEON_PRODUCT_ID_V2M)
    ) {
        return true
    }
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
    data class Incompatible(val version: Int) : ViaVersionParse()
}

/**
 * Два байта версии — big-endian, как в QMK: старший байт в `response[1]`, младший в `response[2]`.
 * Меньше трёх байт — ответ оборван. Чужой первый байт — ошибка протокола.
 * Нулевая или слишком большая версия при правильном байте команды — несовместимая версия.
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
    if (version > VIA_PROTOCOL_VERSION_MAX) return ViaVersionParse.Incompatible(version)
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
 * Версия — uint32 little-endian. UID короче 8 байт не считается идентичностью.
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
        append(probe.rawInterfaceId?.toString() ?: "—")
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
    /**
     * [allowForce] true только после явного подтверждения расширенной проверки.
     * Реализация всё равно не захватывает boot-клавиатуру и сначала пробует force=false.
     */
    fun claimRawHid(allowForce: Boolean): ClaimResult
    fun exchange(payload: ByteArray): HidReport
    fun close()
}

data class ClaimResult(
    val ok: Boolean,
    val status: ProbeStatus,
    val detail: String,
    val interfaceId: Int? = null,
    val gentleClaim: String = "не выполнялся",
    val forceClaim: String = "не запрашивался",
)

/**
 * Сначала VIA get_protocol_version, затем Vial get_keyboard_id.
 * Оба запроса только читают. Если VIA не разобран, Vial не запрашивается.
 * layer_state не запрашивается. Соединение закрывается и при успехе, и при ошибке.
 * Несовпадение UID с экспортом Cornedeon не отменяет уже разобранную идентичность Vial.
 */
fun runReadOnlyProbe(
    session: UsbSession,
    allowForce: Boolean = false,
    exportUid: ByteArray = cornedeonVialUidBytes(),
): ProbeResult {
    val exchanges = mutableListOf<HidExchangeRecord>()
    try {
        val claim = session.claimRawHid(allowForce)
        val base = ProbeResult(
            opened = true,
            status = claim.status,
            detail = claim.detail,
            rawInterfaceId = claim.interfaceId,
            gentleClaim = claim.gentleClaim,
            forceClaim = claim.forceClaim,
        )
        if (!claim.ok) {
            return finish(
                base.copy(
                    exchangeNote = "не начинался: интерфейс не захвачен",
                    recovery = recoveryFor(claim.status),
                ),
                rawHid = false,
                exchanges = exchanges,
            )
        }
        val viaRequest = viaProtocolRequest()
        return when (val via = session.exchange(viaRequest)) {
            HidReport.Timeout -> {
                exchanges += hidExchangeRecord("VIA get_protocol_version", viaRequest, null)
                finish(
                    base.copy(
                        status = ProbeStatus.TIMEOUT,
                        detail = "Таймаут VIA get_protocol_version. Ответа не было.",
                        exchangeNote = "таймаут",
                        viaNote = "таймаут, ответа нет",
                        recovery = recoveryFor(ProbeStatus.TIMEOUT),
                    ),
                    rawHid = true,
                    exchanges = exchanges,
                )
            }
            HidReport.Empty -> {
                exchanges += hidExchangeRecord("VIA get_protocol_version", viaRequest, null)
                finish(
                    base.copy(
                        status = ProbeStatus.NO_RESPONSE,
                        detail = "VIA get_protocol_version завершился без байт.",
                        exchangeNote = "пустой ответ",
                        viaNote = "пустой ответ",
                        recovery = recoveryFor(ProbeStatus.NO_RESPONSE),
                    ),
                    rawHid = true,
                    exchanges = exchanges,
                )
            }
            HidReport.Failed -> {
                exchanges += hidExchangeRecord("VIA get_protocol_version", viaRequest, null)
                finish(
                    base.copy(
                        status = ProbeStatus.IO_ERROR,
                        detail = "Сбой обмена VIA get_protocol_version.",
                        exchangeNote = "ошибка обмена",
                        viaNote = "обмен не выполнен",
                        recovery = recoveryFor(ProbeStatus.IO_ERROR),
                    ),
                    rawHid = true,
                    exchanges = exchanges,
                )
            }
            is HidReport.Data -> {
                exchanges += hidExchangeRecord("VIA get_protocol_version", viaRequest, via.bytes)
                readVialAfterVersion(base, via.bytes, session, exportUid, exchanges)
            }
        }
    } finally {
        session.close()
    }
}

private fun readVialAfterVersion(
    base: ProbeResult,
    bytes: ByteArray,
    session: UsbSession,
    exportUid: ByteArray,
    exchanges: MutableList<HidExchangeRecord>,
): ProbeResult {
    when (val via = parseViaProtocolVersion(bytes)) {
        is ViaVersionParse.Incompatible -> {
            return finish(
                base.copy(
                    status = ProbeStatus.INCOMPATIBLE_VERSION,
                    detail = "VIA ответил 0x01, но версия ${via.version} вне поддерживаемого диапазона: ${hex(bytes)}.",
                    exchangeNote = "ответ получен",
                    viaNote = "несовместимая версия",
                    recovery = "Другие команды не отправлялись. Раскладка не менялась.",
                ),
                rawHid = true,
                exchanges = exchanges,
            )
        }
        ViaVersionParse.Incomplete -> {
            return finish(
                base.copy(
                    status = ProbeStatus.PROTOCOL_MISMATCH,
                    detail = "Неполный ответ VIA get_protocol_version (${bytes.size} байт).",
                    exchangeNote = "ответ получен",
                    viaNote = "неполный ответ",
                    recovery = "Это не полный ответ VIA. Запросы записи не отправлялись.",
                ),
                rawHid = true,
                exchanges = exchanges,
            )
        }
        is ViaVersionParse.ProtocolError -> {
            return finish(
                base.copy(
                    status = ProbeStatus.PROTOCOL_MISMATCH,
                    detail = "Ошибка протокола VIA get_protocol_version: ${via.reason}.",
                    exchangeNote = "ответ получен",
                    viaNote = "неправильный ответ",
                    recovery = "Это не ответ VIA. Запросы записи не отправлялись.",
                ),
                rawHid = true,
                exchanges = exchanges,
            )
        }
        is ViaVersionParse.Ok -> return readVialIdentity(base, via.version, session, exportUid, exchanges)
    }
}

private fun readVialIdentity(
    base: ProbeResult,
    version: Int,
    session: UsbSession,
    exportUid: ByteArray,
    exchanges: MutableList<HidExchangeRecord>,
): ProbeResult {
    val viaNote = "версия $version"
    val vialRequest = vialKeyboardIdRequest()
    return when (val vial = session.exchange(vialRequest)) {
        HidReport.Timeout -> {
            exchanges += hidExchangeRecord("Vial get_keyboard_id", vialRequest, null)
            finish(
                base.copy(
                    status = ProbeStatus.PARTIAL,
                    protocolVersion = version,
                    detail = "VIA protocol $version. Ответ Vial get_keyboard_id не получен.",
                    exchangeNote = "VIA прочитан, Vial — таймаут",
                    viaNote = viaNote,
                    vialNote = "таймаут, ответа нет",
                    recovery = recoveryFor(ProbeStatus.PARTIAL),
                ),
                rawHid = true,
                exchanges = exchanges,
            )
        }
        HidReport.Empty -> {
            exchanges += hidExchangeRecord("Vial get_keyboard_id", vialRequest, null)
            finish(
                base.copy(
                    status = ProbeStatus.PARTIAL,
                    protocolVersion = version,
                    detail = "VIA protocol $version. Vial get_keyboard_id завершился без байт.",
                    exchangeNote = "VIA прочитан, Vial пуст",
                    viaNote = viaNote,
                    vialNote = "пустой ответ",
                    recovery = recoveryFor(ProbeStatus.PARTIAL),
                ),
                rawHid = true,
                exchanges = exchanges,
            )
        }
        HidReport.Failed -> {
            exchanges += hidExchangeRecord("Vial get_keyboard_id", vialRequest, null)
            finish(
                base.copy(
                    status = ProbeStatus.IO_ERROR,
                    protocolVersion = version,
                    detail = "VIA protocol $version. Сбой обмена Vial get_keyboard_id.",
                    exchangeNote = "VIA прочитан, Vial — ошибка",
                    viaNote = viaNote,
                    vialNote = "ошибка обмена",
                    recovery = recoveryFor(ProbeStatus.IO_ERROR),
                ),
                rawHid = true,
                exchanges = exchanges,
            )
        }
        is HidReport.Data -> {
            exchanges += hidExchangeRecord("Vial get_keyboard_id", vialRequest, vial.bytes)
            when (val parsed = parseVialKeyboardId(vial.bytes)) {
                is VialIdentityParse.Ok -> {
                    val uid = compareKeyboardUid(parsed.identity.uidBytes, exportUid)
                    val idHex = uidHex(parsed.identity.uidBytes)
                    val note = uidComparisonText(uid, idHex, uidHex(exportUid))
                    finish(
                        base.copy(
                            status = ProbeStatus.OK,
                            protocolVersion = version,
                            vialKeyboardId = idHex,
                            vial = parsed.identity,
                            vialProtocolVersion = parsed.identity.protocolVersion,
                            uidComparison = uid,
                            detail = "VIA protocol $version. Vial protocol ${parsed.identity.protocolVersion}. " +
                                "$note Физический слой этим обменом не читается.",
                            exchangeNote = "успешный обмен",
                            viaNote = viaNote,
                            vialNote = note,
                            recovery = recoveryFor(ProbeStatus.OK),
                        ),
                        rawHid = true,
                        exchanges = exchanges,
                    )
                }
                is VialIdentityParse.Incomplete -> finish(
                    base.copy(
                        status = ProbeStatus.PARTIAL,
                        protocolVersion = version,
                        vialProtocolVersion = parsed.protocolVersion,
                        partialUidHex = parsed.uidHex,
                        detail = "VIA protocol $version. Ответ Vial неполный: версия " +
                            "${parsed.protocolVersion ?: "не прочитана"}, UID ${parsed.uidLength} из $VIAL_UID_LENGTH байт.",
                        exchangeNote = "VIA прочитан, Vial не подтверждён",
                        viaNote = viaNote,
                        vialNote = if (parsed.protocolVersion == null) "неправильный ответ" else "неполный UID",
                        recovery = recoveryFor(ProbeStatus.PARTIAL),
                    ),
                    rawHid = true,
                    exchanges = exchanges,
                )
                is VialIdentityParse.ProtocolError -> finish(
                    base.copy(
                        status = ProbeStatus.PARTIAL,
                        protocolVersion = version,
                        detail = "VIA protocol $version. Ошибка протокола Vial get_keyboard_id: ${parsed.reason}.",
                        exchangeNote = "VIA прочитан, Vial не подтверждён",
                        viaNote = viaNote,
                        vialNote = "неправильный ответ",
                        recovery = recoveryFor(ProbeStatus.PARTIAL),
                    ),
                    rawHid = true,
                    exchanges = exchanges,
                )
            }
        }
    }
}

fun recoveryFor(status: ProbeStatus): String = when (status) {
    ProbeStatus.OK, ProbeStatus.PARTIAL ->
        "Интерфейс отпущен, соединение закрыто. Если печать пропала, переподключите кабель."
    ProbeStatus.CLAIM_FAILED -> "Интерфейс не остался захваченным. Обычная проверка не отключала драйвер. " +
        "Если печать нарушилась, переподключите клавиатуру."
    ProbeStatus.TIMEOUT, ProbeStatus.NO_RESPONSE, ProbeStatus.IO_ERROR ->
        "Соединение закрыто. Переподключите Cornedeon и повторите проверку."
    ProbeStatus.NO_PERMISSION -> "Разрешите USB и повторите. Клавиатура у системы не забиралась."
    ProbeStatus.NO_INTERFACE -> "Подходящий Raw HID не захватывался. Boot-клавиатура остаётся у системы."
    else -> "Соединение закрыто. При сбое печати переподключите кабель."
}

fun firmwareFromProbe(result: ProbeResult): FirmwareState {
    val stage = when (result.status) {
        ProbeStatus.NOT_RUN -> LinkStage.NOT_CHECKED
        ProbeStatus.NO_DEVICE -> LinkStage.NO_DEVICE
        ProbeStatus.NO_PERMISSION -> LinkStage.NO_PERMISSION
        ProbeStatus.NO_INTERFACE -> LinkStage.NO_RAW_HID
        ProbeStatus.CLAIM_FAILED -> LinkStage.RAW_HID_UNREACHABLE
        ProbeStatus.OK, ProbeStatus.PARTIAL -> LinkStage.PROTOCOL_VERIFIED
        ProbeStatus.TIMEOUT,
        ProbeStatus.NO_RESPONSE,
        ProbeStatus.PROTOCOL_MISMATCH,
        ProbeStatus.INCOMPATIBLE_VERSION,
        ProbeStatus.IO_ERROR,
        -> LinkStage.PROTOCOL_UNVERIFIED
    }
    val verified = result.status == ProbeStatus.OK || result.status == ProbeStatus.PARTIAL
    return FirmwareState(
        connected = result.opened,
        telemetry = if (verified) Telemetry.UNAVAILABLE else Telemetry.UNKNOWN,
        activeLayersMask = null,
        source = if (verified) "via-get-protocol-version" else "usb-probe",
        confidence = Confidence.UNKNOWN,
        error = if (verified) null else result.detail,
        stage = stage,
    )
}

/** Текст телеметрии. UNAVAILABLE произносится только после проверенного протокола. */
fun layerTelemetryLine(state: FirmwareState): String = when {
    state.telemetry == Telemetry.AVAILABLE &&
        state.confidence == Confidence.REPORTED &&
        state.activeLayersMask != null -> "телеметрия слоёв доступна"
    state.stage == LinkStage.PROTOCOL_VERIFIED && state.telemetry == Telemetry.UNAVAILABLE ->
        "телеметрия слоёв не поддерживается существующим протоколом"
    state.stage == LinkStage.RAW_HID_UNREACHABLE ->
        "Raw HID обнаружен, но недоступен. Обмен с прошивкой не проверялся"
    state.stage == LinkStage.NO_PERMISSION -> "USB-разрешение отсутствует"
    state.stage == LinkStage.NO_DEVICE -> "USB-устройство не обнаружено"
    state.stage == LinkStage.NO_RAW_HID -> "подходящий Raw HID не найден"
    state.stage == LinkStage.PROTOCOL_UNVERIFIED ->
        "обмен не подтвердил протокол VIA/Vial. Телеметрия слоёв не оценивалась"
    else -> "телеметрия слоёв не проверялась"
}

/** Кнопка расширенной проверки: обычный захват уже вернул false и Raw HID в дескрипторе есть. */
fun canOfferForceProbe(dashboard: UsbDashboard): Boolean {
    if (dashboard.probe.status != ProbeStatus.CLAIM_FAILED) return false
    val device = dashboard.devices.find { it.key == dashboard.selectedKey } ?: return false
    return chooseRawInterface(device.interfaces) != null
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

private fun finish(
    result: ProbeResult,
    rawHid: Boolean,
    exchanges: List<HidExchangeRecord>,
): ProbeResult {
    val uid = if (result.vial == null) UidMatchResult.UNKNOWN else result.uidComparison
    return result.copy(
        uidComparison = uid,
        exchanges = exchanges,
        flags = diagnosticFlags(
            opened = result.opened,
            rawHid = rawHid,
            viaVerified = result.protocolVersion != null,
            vialVerified = result.vial != null,
            uid = uid,
        ),
    )
}

private val VIL_UID = Regex(""""uid"\s*:\s*(\d+)""")

private const val USB_CLASS_HID = 3
