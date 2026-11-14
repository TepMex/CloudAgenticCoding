package com.tepmex.cornegame.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.tepmex.cornegame.domain.ClaimResult
import com.tepmex.cornegame.domain.EndpointFact
import com.tepmex.cornegame.domain.InterfaceFact
import com.tepmex.cornegame.domain.ProbeStatus
import com.tepmex.cornegame.domain.UsbDeviceSnapshot
import com.tepmex.cornegame.domain.UsbHardware
import com.tepmex.cornegame.domain.UsbPermission
import com.tepmex.cornegame.domain.UsbSession
import com.tepmex.cornegame.domain.VIA_REPORT_BYTES
import com.tepmex.cornegame.domain.chooseRawInterface

/**
 * USB Host для диагностики. Boot-интерфейс клавиатуры не захватывается,
 * claimInterface вызывается только с force=false и только у кандидата Raw HID.
 * После запроса интерфейс отпускается.
 */
class AndroidUsbHardware(private val context: Context) : UsbHardware {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private var pending: ((Boolean) -> Unit)? = null
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val callback = pending
            pending = null
            callback?.invoke(granted)
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter(permissionAction)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
        pending = null
    }

    override fun listDevices(): List<UsbDeviceSnapshot> {
        val manager = usb ?: return emptyList()
        return manager.deviceList.values.map { device -> snapshot(manager, device) }
    }

    override fun requestPermission(deviceKey: String, onResult: (Boolean) -> Unit) {
        val manager = usb
        val device = manager?.deviceList?.values?.find { it.deviceName == deviceKey }
        if (manager == null || device == null) {
            onResult(false)
            return
        }
        if (manager.hasPermission(device)) {
            onResult(true)
            return
        }
        pending = onResult
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(permissionAction).setPackage(context.packageName),
            flags,
        )
        manager.requestPermission(device, intent)
    }

    override fun open(deviceKey: String): UsbSession? {
        val manager = usb ?: return null
        val device = manager.deviceList.values.find { it.deviceName == deviceKey } ?: return null
        if (!manager.hasPermission(device)) return null
        val connection = manager.openDevice(device) ?: return null
        return AndroidUsbSession(device, connection)
    }

    private fun snapshot(manager: UsbManager, device: UsbDevice): UsbDeviceSnapshot {
        val granted = manager.hasPermission(device)
        val manufacturer = if (granted) runCatching { device.manufacturerName }.getOrNull() else null
        val product = if (granted) runCatching { device.productName }.getOrNull() else null
        return UsbDeviceSnapshot(
            key = device.deviceName,
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            manufacturer = manufacturer,
            productName = product,
            permission = if (granted) UsbPermission.GRANTED else UsbPermission.UNKNOWN,
            interfaces = (0 until device.interfaceCount).map { index ->
                val iface = device.getInterface(index)
                InterfaceFact(
                    id = iface.id,
                    classCode = iface.interfaceClass,
                    subclass = iface.interfaceSubclass,
                    protocol = iface.interfaceProtocol,
                    endpoints = (0 until iface.endpointCount).map { endpointIndex ->
                        val endpoint = iface.getEndpoint(endpointIndex)
                        EndpointFact(endpoint.address, endpoint.attributes, endpoint.maxPacketSize)
                    },
                )
            },
        )
    }
}

private class AndroidUsbSession(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
) : UsbSession {
    private var claimed: UsbInterface? = null
    private var closed = false

    override fun claimRawHid(): ClaimResult {
        val facts = (0 until device.interfaceCount).map { index ->
            val iface = device.getInterface(index)
            InterfaceFact(
                id = iface.id,
                classCode = iface.interfaceClass,
                subclass = iface.interfaceSubclass,
                protocol = iface.interfaceProtocol,
                endpoints = (0 until iface.endpointCount).map { endpointIndex ->
                    val endpoint = iface.getEndpoint(endpointIndex)
                    EndpointFact(endpoint.address, endpoint.attributes, endpoint.maxPacketSize)
                },
            )
        }
        val chosen = chooseRawInterface(facts)
            ?: return ClaimResult(
                ok = false,
                status = ProbeStatus.NO_INTERFACE,
                detail = "Нет отдельного Raw HID (interrupt IN и OUT, пакет от 32 байт). " +
                    "Рабочая boot-клавиатура не захватывается.",
            )
        val iface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .first { it.id == chosen.id }
        val claimedOk = connection.claimInterface(iface, false)
        if (!claimedOk) {
            return ClaimResult(
                ok = false,
                status = ProbeStatus.CLAIM_FAILED,
                detail = "Интерфейс ${iface.id} занят. Захват с force=true не выполняется, чтобы не отбирать клавиатуру у системы.",
            )
        }
        claimed = iface
        return ClaimResult(ok = true, status = ProbeStatus.OK, detail = "Интерфейс ${iface.id} открыт для чтения.")
    }

    override fun exchange(payload: ByteArray): ByteArray? {
        val iface = claimed ?: return null
        val out = endpoints(iface).firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        val incoming = endpoints(iface).firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        if (out == null || incoming == null) return null
        val sent = connection.bulkTransfer(out, payload, payload.size, TIMEOUT_MS)
        if (sent < 0) return null
        val buffer = ByteArray(VIA_REPORT_BYTES)
        val got = connection.bulkTransfer(incoming, buffer, buffer.size, TIMEOUT_MS)
        if (got <= 0) return null
        return buffer.copyOf(got.coerceAtMost(buffer.size))
    }

    override fun close() {
        if (closed) return
        closed = true
        claimed?.let { iface -> runCatching { connection.releaseInterface(iface) } }
        claimed = null
        runCatching { connection.close() }
    }

    private fun endpoints(iface: UsbInterface) =
        (0 until iface.endpointCount).map { iface.getEndpoint(it) }

    private companion object {
        const val TIMEOUT_MS = 400
    }
}
