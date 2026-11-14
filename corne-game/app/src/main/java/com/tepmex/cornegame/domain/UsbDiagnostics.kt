package com.tepmex.cornegame.domain

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
                _firmware.value = FirmwareState(error = "Устройство не подключено.")
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

    fun probeSelected() {
        run {
            val current = _dashboard.value
            val device = current.devices.find { it.key == current.selectedKey }
            if (device == null) {
                val result = ProbeResult(
                    opened = false,
                    status = ProbeStatus.NO_DEVICE,
                    detail = "Нет выбранного USB-устройства.",
                )
                publish(current.copy(probe = result), firmwareFromProbe(result))
                return@run
            }
            if (device.permission != UsbPermission.GRANTED) {
                val result = ProbeResult(
                    opened = false,
                    status = ProbeStatus.NO_PERMISSION,
                    detail = "Нет разрешения Android на USB. Запросите доступ и повторите проверку.",
                )
                publish(current.copy(probe = result), firmwareFromProbe(result))
                return@run
            }
            val session = runCatching { hardware.open(device.key) }.getOrNull()
            if (session == null) {
                val result = ProbeResult(
                    opened = false,
                    status = ProbeStatus.IO_ERROR,
                    detail = "Соединение не открылось. Устройство могло отключиться.",
                )
                publish(current.copy(probe = result), firmwareFromProbe(result))
                return@run
            }
            val result = runCatching { runReadOnlyProbe(session) }.getOrElse { error ->
                runCatching { session.close() }
                ProbeResult(
                    opened = true,
                    status = ProbeStatus.IO_ERROR,
                    detail = error.message ?: "Ошибка обмена.",
                )
            }
            publish(
                _dashboard.value.copy(probe = result, log = (_dashboard.value.log + result.detail).takeLast(20)),
                firmwareFromProbe(result),
            )
        }
    }

    fun close() {
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
