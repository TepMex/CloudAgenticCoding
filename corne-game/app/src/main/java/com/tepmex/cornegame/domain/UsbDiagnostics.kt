package com.tepmex.cornegame.domain

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface UsbHardware {
    fun listDevices(): List<UsbDeviceSnapshot>
    fun requestPermission(deviceKey: String, onResult: (Boolean) -> Unit)
    fun open(deviceKey: String): UsbSession?
}

/**
 * Экран диагностики говорит с USB только через [UsbHardware].
 * Состояние слоёв остаётся неизвестным: удачный VIA-запрос не выдумывает layer_state.
 */
class UsbDiagnosticsController(
    private val hardware: UsbHardware,
    private val async: Boolean = true,
) : FirmwareStateProvider {
    private val _dashboard = MutableStateFlow(UsbDashboard())
    val dashboard: StateFlow<UsbDashboard> = _dashboard.asStateFlow()
    private val _firmware = MutableStateFlow(FirmwareState())
    override val state: StateFlow<FirmwareState> = _firmware.asStateFlow()
    private val queue = ArrayDeque<() -> Unit>()
    private val lock = ReentrantLock()
    private val ready = lock.newCondition()
    private var closed = false
    private val active = AtomicReference<UsbSession?>(null)
    private val worker = Thread {
        try {
            while (true) {
                val task = nextTask() ?: break
                task()
            }
        } catch (_: InterruptedException) {
            // close() останавливает фоновый опрос.
        }
    }.also {
        it.isDaemon = true
        it.name = "usb-diagnostics"
        if (async) it.start()
    }

    fun refresh() {
        run {
            val devices = runCatching { hardware.listDevices() }.getOrElse { error ->
                publish(
                    _dashboard.value.copy(
                        devices = emptyList(),
                        probe = ProbeResult(false, ProbeStatus.IO_ERROR, error.message ?: "Не удалось прочитать USB."),
                    ),
                )
                return@run
            }
            _dashboard.update { current ->
                val selected = current.selectedKey?.takeIf { key -> devices.any { it.key == key } }
                    ?: devices.firstOrNull { device ->
                        looksLikeCornedeon(
                            device.vendorId,
                            device.productId,
                            device.manufacturer,
                            device.productName,
                            device.deviceName,
                        )
                    }?.key
                    ?: devices.firstOrNull()?.key
                val note = when {
                    devices.isEmpty() -> ProbeResult(
                        opened = false,
                        status = ProbeStatus.NO_DEVICE,
                        detail = "USB-устройства не найдены. Подключите Cornedeon по USB-C или OTG.",
                    )
                    current.probe.status == ProbeStatus.NO_DEVICE -> ProbeResult(
                        opened = false,
                        status = ProbeStatus.NOT_RUN,
                        detail = "Устройство найдено. Запросите разрешение и запустите проверку.",
                    )
                    else -> current.probe
                }
                current.copy(devices = devices, selectedKey = selected, probe = note)
            }
            if (devices.isEmpty()) {
                _firmware.value = FirmwareState(
                    connected = false,
                    telemetry = Telemetry.UNKNOWN,
                    stage = LinkStage.NO_DEVICE,
                    source = "usb",
                    error = "Устройство не подключено.",
                )
            }
        }
    }

    fun select(key: String) {
        _dashboard.update { it.copy(selectedKey = key) }
    }

    fun requestPermission() {
        val key = _dashboard.value.selectedKey
        if (key == null) {
            refresh()
            return
        }
        hardware.requestPermission(key) { granted ->
            run {
                refresh()
                if (!granted) {
                    val result = ProbeResult(
                        opened = false,
                        status = ProbeStatus.NO_PERMISSION,
                        detail = "Android не дал доступ к USB.",
                    )
                    publish(_dashboard.value.copy(probe = result), firmwareFromProbe(result))
                }
            }
        }
    }

    /** Обычная проверка: только claimInterface(force=false). */
    fun probeSelected() {
        probe(allowForce = false)
    }

    /**
     * Расширенная проверка. Без [confirmed] соединение не открывается и force=true не вызывается.
     * Вызов имеет смысл только после CLAIM_FAILED и найденного Raw HID.
     */
    fun probeAdvanced(confirmed: Boolean) {
        run {
            if (!canOfferForceProbe(_dashboard.value)) return@run
            if (!confirmed) {
                val current = _dashboard.value.probe
                val declined = current.copy(
                    forceClaim = "отменён пользователем, force=true не вызывался",
                    detail = "Принудительный захват отменён. Интерфейс не захватывался с force=true.",
                    recovery = "Обычная проверка остаётся безопасной. Драйвер ядра не отключался.",
                )
                publish(_dashboard.value.copy(probe = declined))
                return@run
            }
            probeBody(allowForce = true)
        }
    }

    /** Отпустить интерфейс, если проверка ещё держит соединение. Экран диагностики его не хранит. */
    fun releaseActive() {
        active.getAndSet(null)?.let { session -> runCatching { session.close() } }
    }

    fun close() {
        releaseActive()
        lock.lock()
        try {
            closed = true
            queue.clear()
            ready.signalAll()
        } finally {
            lock.unlock()
        }
        worker.interrupt()
    }

    private fun probe(allowForce: Boolean) {
        run { probeBody(allowForce) }
    }

    private fun probeBody(allowForce: Boolean) {
        val current = _dashboard.value
        val device = current.devices.find { it.key == current.selectedKey }
        if (device == null) {
            val result = ProbeResult(
                opened = false,
                status = ProbeStatus.NO_DEVICE,
                detail = "Нет выбранного USB-устройства.",
                recovery = recoveryFor(ProbeStatus.NO_DEVICE),
            )
            publish(current.copy(probe = result), firmwareFromProbe(result))
            return
        }
        if (device.permission != UsbPermission.GRANTED) {
            val result = ProbeResult(
                opened = false,
                status = ProbeStatus.NO_PERMISSION,
                detail = "Нет разрешения Android на USB. Запросите доступ и повторите проверку.",
                recovery = recoveryFor(ProbeStatus.NO_PERMISSION),
            )
            publish(current.copy(probe = result), firmwareFromProbe(result))
            return
        }
        val session = runCatching { hardware.open(device.key) }.getOrNull()
        if (session == null) {
            val result = ProbeResult(
                opened = false,
                status = ProbeStatus.IO_ERROR,
                detail = "Соединение не открылось. Устройство могло отключиться.",
                recovery = recoveryFor(ProbeStatus.IO_ERROR),
            )
            publish(current.copy(probe = result), firmwareFromProbe(result))
            return
        }
        active.set(session)
        val result = try {
            runReadOnlyProbe(session, allowForce)
        } catch (error: Exception) {
            runCatching { session.close() }
            ProbeResult(
                opened = true,
                status = ProbeStatus.IO_ERROR,
                detail = error.message ?: "Ошибка обмена.",
                exchangeNote = "исключение, соединение закрывается",
                recovery = recoveryFor(ProbeStatus.IO_ERROR),
            )
        } finally {
            runCatching { session.close() }
            active.compareAndSet(session, null)
        }
        publish(
            _dashboard.value.copy(probe = result, log = (_dashboard.value.log + result.detail).takeLast(20)),
            firmwareFromProbe(result),
        )
    }

    private fun nextTask(): (() -> Unit)? {
        lock.lock()
        try {
            while (queue.isEmpty() && !closed) ready.await()
            if (closed) return null
            return queue.removeFirst()
        } finally {
            lock.unlock()
        }
    }

    private fun publish(dashboard: UsbDashboard, firmware: FirmwareState = _firmware.value) {
        _dashboard.value = dashboard
        _firmware.value = firmware
    }

    private fun run(block: () -> Unit) {
        if (!async) {
            block()
            return
        }
        lock.lock()
        try {
            queue.addLast(block)
            ready.signal()
        } finally {
            lock.unlock()
        }
    }
}
