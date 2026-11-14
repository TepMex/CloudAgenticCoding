package com.tepmex.cornegame.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Доступность телеметрии слоёв. AVAILABLE значит, что прошивка реально прислала маску.
 * Сейчас штатный VIA её не отдаёт, поэтому после удачного чтения версии остаётся UNAVAILABLE.
 */
enum class Telemetry {
    UNKNOWN,
    UNAVAILABLE,
    AVAILABLE,
}

/** REPORTED — значение пришло от прошивки. UNKNOWN — его нет, и рисовать слой как факт нельзя. */
enum class Confidence {
    UNKNOWN,
    REPORTED,
}

/**
 * Насколько далеко дошёл обмен. [Telemetry.UNAVAILABLE] ставится только после
 * проверенного протокола, который сам layer_state не отдаёт.
 */
enum class LinkStage {
    NOT_CHECKED,
    NO_DEVICE,
    NO_PERMISSION,
    NO_RAW_HID,
    RAW_HID_UNREACHABLE,
    PROTOCOL_UNVERIFIED,
    PROTOCOL_VERIFIED,
}

data class FirmwareState(
    val connected: Boolean = false,
    val telemetry: Telemetry = Telemetry.UNKNOWN,
    val activeLayersMask: Int? = null,
    val source: String = "none",
    val confidence: Confidence = Confidence.UNKNOWN,
    val error: String? = null,
    val stage: LinkStage = LinkStage.NOT_CHECKED,
)

/**
 * Будущий обмен с прошивкой, которая сама сообщит layer_state.
 * Сейчас реализация от USB-диагностики маску не заполняет.
 */
interface FirmwareStateProvider {
    val state: StateFlow<FirmwareState>
}

class UnavailableFirmware : FirmwareStateProvider {
    private val fixed = MutableStateFlow(FirmwareState(telemetry = Telemetry.UNAVAILABLE))
    override val state: StateFlow<FirmwareState> = fixed.asStateFlow()
}
