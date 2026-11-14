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
/** Живая Cornedeon v2m из USB-дескриптора. 0x0009 остаётся ориентиром прошивки 2mod. */
const val CORNEDEON_PRODUCT_ID_V2M = 0x0007
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
    INCOMPATIBLE_VERSION,
    TIMEOUT,
    NO_RESPONSE,
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
    val rawInterfaceId: Int? = null,
    val gentleClaim: String = "не выполнялся",
    val forceClaim: String = "не запрашивался",
    val exchangeNote: String = "не выполнялся",
    val viaNote: String = "не запрашивался",
    val vialNote: String = "не запрашивался",
    val recovery: String? = null,
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
 * Порядок [UsbDeviceConnection.claimInterface]. true в списке — это force=true.
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
 * Оба запроса только читают. Если ответ не похож на протокол, следующие команды не шлются.
 * layer_state не запрашивается. Соединение закрывается и при успехе, и при ошибке.
 */
fun runReadOnlyProbe(session: UsbSession, allowForce: Boolean = false): ProbeResult {
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
            return base.copy(
                exchangeNote = "не начинался: интерфейс не захвачен",
                recovery = recoveryFor(claim.status),
            )
        }
        return when (val via = session.exchange(viaProtocolRequest())) {
            HidReport.Timeout -> base.copy(
                status = ProbeStatus.TIMEOUT,
                detail = "Таймаут VIA get_protocol_version. Ответа не было.",
                exchangeNote = "таймаут",
                viaNote = "таймаут, ответа нет",
                recovery = recoveryFor(ProbeStatus.TIMEOUT),
            )
            HidReport.Empty -> base.copy(
                status = ProbeStatus.NO_RESPONSE,
                detail = "VIA get_protocol_version завершился без байт.",
                exchangeNote = "пустой ответ",
                viaNote = "пустой ответ",
                recovery = recoveryFor(ProbeStatus.NO_RESPONSE),
            )
            HidReport.Failed -> base.copy(
                status = ProbeStatus.IO_ERROR,
                detail = "Сбой обмена VIA get_protocol_version.",
                exchangeNote = "ошибка обмена",
                viaNote = "обмен не выполнен",
                recovery = recoveryFor(ProbeStatus.IO_ERROR),
            )
            is HidReport.Data -> readVialAfterVersion(base, via.bytes, session)
        }
    } finally {
        session.close()
    }
}

private fun readVialAfterVersion(base: ProbeResult, bytes: ByteArray, session: UsbSession): ProbeResult {
    val version = parseViaProtocolVersion(bytes)
    if (bytes.size >= 3 && bytes[0] == VIA_GET_PROTOCOL_VERSION && version == null) {
        return base.copy(
            status = ProbeStatus.INCOMPATIBLE_VERSION,
            detail = "VIA ответил 0x01, но версия вне поддерживаемого диапазона: ${hex(bytes)}.",
            exchangeNote = "ответ получен",
            viaNote = "несовместимая версия",
            recovery = "Другие команды не отправлялись. Раскладка не менялась.",
        )
    }
    if (version == null) {
        return base.copy(
            status = ProbeStatus.PROTOCOL_MISMATCH,
            detail = "Ответ не похож на VIA get_protocol_version: ${hex(bytes)}.",
            exchangeNote = "ответ получен",
            viaNote = "неправильный ответ",
            recovery = "Это не ответ VIA. Запросы записи не отправлялись.",
        )
    }
    val viaNote = "версия $version"
    return when (val vial = session.exchange(vialKeyboardIdRequest())) {
        HidReport.Timeout -> base.copy(
            status = ProbeStatus.OK,
            protocolVersion = version,
            detail = "VIA protocol $version. Vial get_keyboard_id: таймаут, ответа нет. Слой прошивки не читается.",
            exchangeNote = "VIA прочитан, Vial — таймаут",
            viaNote = viaNote,
            vialNote = "таймаут, ответа нет",
            recovery = recoveryFor(ProbeStatus.OK),
        )
        HidReport.Empty -> base.copy(
            status = ProbeStatus.OK,
            protocolVersion = version,
            detail = "VIA protocol $version. Vial get_keyboard_id завершился без байт. Слой прошивки не читается.",
            exchangeNote = "VIA прочитан, Vial пуст",
            viaNote = viaNote,
            vialNote = "пустой ответ",
            recovery = recoveryFor(ProbeStatus.OK),
        )
        HidReport.Failed -> base.copy(
            status = ProbeStatus.IO_ERROR,
            protocolVersion = version,
            detail = "VIA protocol $version. Сбой обмена Vial get_keyboard_id.",
            exchangeNote = "VIA прочитан, Vial — ошибка",
            viaNote = viaNote,
            vialNote = "ошибка обмена",
            recovery = recoveryFor(ProbeStatus.IO_ERROR),
        )
        is HidReport.Data -> {
            val id = parseVialKeyboardId(vial.bytes)
            if (id == null) {
                base.copy(
                    status = ProbeStatus.OK,
                    protocolVersion = version,
                    detail = "VIA protocol $version. Vial get_keyboard_id: неправильный ответ ${hex(vial.bytes)}. " +
                        "Слой прошивки не читается.",
                    exchangeNote = "VIA прочитан, Vial не подтверждён",
                    viaNote = viaNote,
                    vialNote = "неправильный ответ",
                    recovery = recoveryFor(ProbeStatus.OK),
                )
            } else {
                base.copy(
                    status = ProbeStatus.OK,
                    protocolVersion = version,
                    vialKeyboardId = id,
                    detail = "VIA protocol $version. Vial keyboard id $id. Слой прошивки этим обменом не читается.",
                    exchangeNote = "успешный обмен",
                    viaNote = viaNote,
                    vialNote = id,
                    recovery = recoveryFor(ProbeStatus.OK),
                )
            }
        }
    }
}

fun recoveryFor(status: ProbeStatus): String = when (status) {
    ProbeStatus.OK -> "Интерфейс отпущен, соединение закрыто. Если печать пропала, переподключите кабель."
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
        ProbeStatus.OK -> LinkStage.PROTOCOL_VERIFIED
        ProbeStatus.TIMEOUT,
        ProbeStatus.NO_RESPONSE,
        ProbeStatus.PROTOCOL_MISMATCH,
        ProbeStatus.INCOMPATIBLE_VERSION,
        ProbeStatus.IO_ERROR,
        -> LinkStage.PROTOCOL_UNVERIFIED
    }
    val verified = result.status == ProbeStatus.OK
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

fun hex(bytes: ByteArray, limit: Int = 8): String =
    bytes.take(limit).joinToString(" ") { byte -> "%02x".format(byte) }

private const val USB_CLASS_HID = 3
