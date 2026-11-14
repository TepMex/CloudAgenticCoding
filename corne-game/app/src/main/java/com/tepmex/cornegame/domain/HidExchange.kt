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

/**
 * Interrupt-отчёт: сначала OUT, затем IN. Оба буфера прямые и начинаются с индекса 0.
 * Новая [android.hardware.usb.UsbRequest.queue] сдвигает position на число байт,
 * старая ставит position в это число. Таймаут отменяет запрос до close.
 * Пустой ответ — не успех.
 */
fun exchangeHidReport(
    port: HidPort,
    outKey: String,
    inKey: String,
    payload: ByteArray,
    responseLength: Int,
    timeoutMs: Long,
): ByteArray? {
    if (!transferOut(port, outKey, payload, timeoutMs)) return null
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

private fun transferOut(port: HidPort, key: String, payload: ByteArray, timeoutMs: Long): Boolean {
    val transfer = port.open(key)
    var queued = false
    var done = false
    try {
        val buffer = ByteBuffer.allocateDirect(payload.size)
        buffer.put(payload)
        buffer.flip()
        queued = transfer.queue(buffer, payload.size)
        if (!queued) return false
        if (port.await(timeoutMs) !== transfer.token) return false
        done = true
        return true
    } finally {
        if (queued && !done) transfer.cancel()
        transfer.close()
    }
}

private fun transferIn(port: HidPort, key: String, length: Int, timeoutMs: Long): ByteArray? {
    val transfer = port.open(key)
    var queued = false
    var done = false
    try {
        val buffer = ByteBuffer.allocateDirect(length)
        queued = transfer.queue(buffer, length)
        if (!queued) return null
        if (port.await(timeoutMs) !== transfer.token) return null
        done = true
        return bytesRead(buffer)
    } finally {
        if (queued && !done) transfer.cancel()
        transfer.close()
    }
}
