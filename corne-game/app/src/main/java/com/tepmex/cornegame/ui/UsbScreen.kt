package com.tepmex.cornegame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.cornegame.domain.CORNEDEON_PRODUCT_ID
import com.tepmex.cornegame.domain.CORNEDEON_PRODUCT_ID_V2M
import com.tepmex.cornegame.domain.CORNEDEON_VENDOR_ID
import com.tepmex.cornegame.domain.FirmwareState
import com.tepmex.cornegame.domain.InterfaceFact
import com.tepmex.cornegame.domain.UsbDashboard
import com.tepmex.cornegame.domain.UsbDeviceSnapshot
import com.tepmex.cornegame.domain.UsbDiagnosticsController
import com.tepmex.cornegame.domain.canOfferForceProbe
import com.tepmex.cornegame.domain.chooseRawInterface
import com.tepmex.cornegame.domain.layerTelemetryLine
import com.tepmex.cornegame.domain.looksLikeCornedeon

@Composable
fun UsbScreen(
    controller: UsbDiagnosticsController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboard by controller.dashboard.collectAsStateWithLifecycle()
    val firmware by controller.state.collectAsStateWithLifecycle()
    var askForce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { controller.refresh() }
    DisposableEffect(controller) {
        onDispose { controller.releaseActive() }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            TextButton(onClick = onBack) { Text("Назад") }
            Text("USB-диагностика", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Ориентиры: VID %04X, PID %04X (2mod) и PID %04X (v2m). Другой дескриптор тоже показывается. " +
                "Обычная проверка только читает VIA/Vial и не отключает драйвер. Boot-клавиатура не захватывается."
                    .format(CORNEDEON_VENDOR_ID, CORNEDEON_PRODUCT_ID, CORNEDEON_PRODUCT_ID_V2M),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (dashboard.devices.isEmpty()) {
            Text(dashboard.probe.detail)
        }
        dashboard.devices.forEach { device ->
            DeviceBlock(
                device = device,
                selected = device.key == dashboard.selectedKey,
                onSelect = { controller.select(device.key) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = controller::refresh) { Text("Обновить") }
            OutlinedButton(onClick = controller::requestPermission) { Text("Разрешение USB") }
        }
        Button(onClick = controller::probeSelected, modifier = Modifier.fillMaxWidth()) {
            Text("Проверить Raw HID")
        }
        if (canOfferForceProbe(dashboard)) {
            OutlinedButton(onClick = { askForce = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Расширенная проверка Raw HID")
            }
        }
        OutlinedButton(onClick = controller::releaseActive, modifier = Modifier.fillMaxWidth()) {
            Text("Закрыть USB-соединение")
        }
        ProbeBlock(dashboard)
        FirmwareBlock(firmware)
        if (askForce) {
            AlertDialog(
                onDismissRequest = {
                    askForce = false
                    controller.probeAdvanced(confirmed = false)
                },
                title = { Text("Принудительный захват Raw HID") },
                text = {
                    Text(
                        "claimInterface(force=true) может отсоединить драйвер ядра только от интерфейса Raw HID. " +
                            "Печать может на время пропасть. Boot-клавиатура не захватывается. " +
                            "После проверки интерфейс будет отпущен. Если печать не вернётся, переподключите кабель.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        askForce = false
                        controller.probeAdvanced(confirmed = true)
                    }) { Text("Захватить Raw HID") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        askForce = false
                        controller.probeAdvanced(confirmed = false)
                    }) { Text("Отмена") }
                },
            )
        }
        if (dashboard.log.isNotEmpty()) {
            Text("Журнал", style = MaterialTheme.typography.titleMedium)
            dashboard.log.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProbeBlock(dashboard: UsbDashboard) {
    val probe = dashboard.probe
    val device = dashboard.devices.find { it.key == dashboard.selectedKey }
    val raw = device?.let { chooseRawInterface(it.interfaces) }
    Text("Проверка", style = MaterialTheme.typography.titleMedium)
    Text(
        listOf(
            "USB permission: ${device?.permission?.name ?: "устройства нет"}",
            "Raw HID: ${raw?.let { "интерфейс ${it.id}, ${it.role}" } ?: "не найден"}",
            "claimInterface(force=false): ${probe.gentleClaim}",
            "claimInterface(force=true): ${probe.forceClaim}",
            "Обмен: ${probe.exchangeNote}",
            "VIA: ${probe.viaNote}",
            "Vial: ${probe.vialNote}",
            "Статус: ${probe.status}",
        ).joinToString("\n"),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(probe.detail)
    probe.protocolVersion?.let { version ->
        Text("VIA protocol: $version")
    }
    probe.vialKeyboardId?.let { id ->
        Text("Vial keyboard id: $id", fontFamily = FontFamily.Monospace)
    }
    probe.recovery?.let { recovery ->
        Text(recovery, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceBlock(
    device: UsbDeviceSnapshot,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val hint = looksLikeCornedeon(
        device.vendorId,
        device.productId,
        device.manufacturer,
        device.productName,
        device.deviceName,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = buildString {
                if (selected) append("● ")
                append(device.productName ?: device.deviceName)
                if (hint) append(" · похоже на Cornedeon")
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "VID %04X  PID %04X  разрешение %s".format(
                device.vendorId,
                device.productId,
                device.permission.name,
            ),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Имя: ${device.deviceName}\nПроизводитель: ${device.manufacturer ?: "недоступно"}\nПродукт: ${device.productName ?: "недоступно"}",
            style = MaterialTheme.typography.bodySmall,
        )
        device.interfaces.forEach { iface -> InterfaceBlock(iface) }
        if (!selected) {
            TextButton(onClick = onSelect) { Text("Выбрать") }
        }
    }
}

@Composable
private fun InterfaceBlock(iface: InterfaceFact) {
    Text(
        "Интерфейс ${iface.id}  class=${iface.classCode} subclass=${iface.subclass} protocol=${iface.protocol}  ${iface.role}",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
    iface.endpoints.forEach { endpoint ->
        Text(
            "  endpoint 0x%02X  ${endpoint.direction}  ${endpoint.type}  max=${endpoint.maxPacket}".format(endpoint.address),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FirmwareBlock(firmware: FirmwareState) {
    Text("Прошивка", style = MaterialTheme.typography.titleMedium)
    Text(
        "Подключение: ${if (firmware.connected) "да" else "нет"}\n" +
            "Состояние: ${layerTelemetryLine(firmware)}\n" +
            "Телеметрия: ${firmware.telemetry}\n" +
            "Маска слоёв: ${firmware.activeLayersMask?.let { "0x${it.toString(16)}" } ?: "неизвестна"}\n" +
            "Источник: ${firmware.source}\n" +
            "Достоверность: ${firmware.confidence}\n" +
            "Ошибка: ${firmware.error ?: "нет"}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Активный слой отсюда не читается. Нарисованная клавиатура не является состоянием прошивки.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
