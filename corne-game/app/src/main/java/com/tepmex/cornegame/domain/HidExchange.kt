package com.tepmex.cornegame.domain

import java.nio.ByteBuffer

const val OUT_ENDPOINT = "out"
const val IN_ENDPOINT = "in"

/**
 * Один поставленный в очередь USB-запрос. [token] — тот же объект, который
 * [HidPort.await] возвращает, когда завершился именно этот запрос.
 */
interface HidTransfer {
    val token: Any
    fun queue(buffer: ByteBuffer, length: Int): Boolean
    fun cancel()
    fun close()
}

interface HidPort {
    fun open(endpointKey: String): HidTransfer
    fun await(timeoutMs: Long): Any?
}

/** Чем кончился один interrupt-отчёт. Пустой буфер и таймаут — разные исходы. */
sealed class HidReport {
    data class Data(val bytes: ByteArray) : HidReport()
    data object Timeout : HidReport()
    data object Empty : HidReport()
    data object Failed : HidReport()
}

/**
 * Interrupt-отчёт: сначала OUT, затем IN. Оба буфера прямые и начинаются с индекса 0.
 * Новая [android.hardware.usb.UsbRequest.queue] сдвигает position на число байт,
 * старая ставит position в это число. Таймаут отменяет запрос до close.
 * Пустой ответ — не успех и не таймаут.
 */
fun exchangeHidReport(
    port: HidPort,
    outKey: String,
    inKey: String,
    payload: ByteArray,
    responseLength: Int,
    timeoutMs: Long,
): HidReport {
    val sent = transferOut(port, outKey, payload, timeoutMs)
    if (sent !is HidReport.Data) return sent
    return transferIn(port, inKey, responseLength, timeoutMs)
}

internal fun bytesRead(buffer: ByteBuffer): ByteArray? {
    val count = buffer.position()
    if (count <= 0) return null
    val view = buffer.duplicate()
    view.position(0)
    view.limit(count)
    return ByteArray(count).also { view.get(it) }
}

private fun transferOut(port: HidPort, key: String, payload: ByteArray, timeoutMs: Long): HidReport {
    val transfer = port.open(key)
    var queued = false
    var done = false
    try {
        val buffer = ByteBuffer.allocateDirect(payload.size)
        buffer.put(payload)
        buffer.flip()
        queued = transfer.queue(buffer, payload.size)
        if (!queued) return HidReport.Failed
        if (port.await(timeoutMs) !== transfer.token) return HidReport.Timeout
        done = true
        return HidReport.Data(payload)
    } finally {
        if (queued && !done) transfer.cancel()
        transfer.close()
    }
}

private fun transferIn(port: HidPort, key: String, length: Int, timeoutMs: Long): HidReport {
    val transfer = port.open(key)
    var queued = false
    var done = false
    try {
        val buffer = ByteBuffer.allocateDirect(length)
        queued = transfer.queue(buffer, length)
        if (!queued) return HidReport.Failed
        if (port.await(timeoutMs) !== transfer.token) return HidReport.Timeout
        done = true
        return bytesRead(buffer)?.let { HidReport.Data(it) } ?: HidReport.Empty
    } finally {
        if (queued && !done) transfer.cancel()
        transfer.close()
    }
}
