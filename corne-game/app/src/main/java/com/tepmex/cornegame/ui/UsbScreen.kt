package com.tepmex.cornegame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.cornegame.domain.CORNEDEON_PRODUCT_ID
import com.tepmex.cornegame.domain.CORNEDEON_VENDOR_ID
import com.tepmex.cornegame.domain.FirmwareState
import com.tepmex.cornegame.domain.InterfaceFact
import com.tepmex.cornegame.domain.UsbDeviceSnapshot
import com.tepmex.cornegame.domain.UsbDiagnosticsController
import com.tepmex.cornegame.domain.looksLikeCornedeon

@Composable
fun UsbScreen(
    controller: UsbDiagnosticsController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboard by controller.dashboard.collectAsStateWithLifecycle()
    val firmware by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { controller.refresh() }
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
            "Ориентир прошивки Cornedeon 2mod: VID %04X PID %04X. Другой дескриптор тоже показывается. " +
                "Проверка только читает VIA/Vial и не меняет раскладку. Обычная клавиатура не захватывается."
                    .format(CORNEDEON_VENDOR_ID, CORNEDEON_PRODUCT_ID),
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
        Text("Проверка: ${dashboard.probe.status}", style = MaterialTheme.typography.titleMedium)
        Text(dashboard.probe.detail)
        dashboard.probe.protocolVersion?.let { version ->
            Text("VIA protocol: $version")
        }
        dashboard.probe.vialKeyboardId?.let { id ->
            Text("Vial keyboard id: $id", fontFamily = FontFamily.Monospace)
        }
        FirmwareBlock(firmware)
        if (dashboard.log.isNotEmpty()) {
            Text("Журнал", style = MaterialTheme.typography.titleMedium)
            dashboard.log.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
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
            "Телеметрия слоёв: ${firmware.telemetry}\n" +
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
