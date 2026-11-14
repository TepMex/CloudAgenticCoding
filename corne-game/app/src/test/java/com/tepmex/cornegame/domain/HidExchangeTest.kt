package com.tepmex.cornegame.domain

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidExchangeTest {
    @Test
    fun outThenInReturnsBytesAndClosesBothRequests() {
        val response = byteArrayOf(0x01, 0x0c, 0x00)
        val port = ScriptedPort(awaitSteps = ArrayDeque(listOf("out", "in")), response = response)
        val payload = byteArrayOf(0x01, 0, 0)
        val got = exchangeHidReport(port, OUT_ENDPOINT, IN_ENDPOINT, payload, responseLength = 32, timeoutMs = 400)
        assertEquals(listOf(0x01.toByte(), 0x0c.toByte(), 0x00.toByte()), got?.toList())
        assertEquals(
            listOf(
                "open:out",
                "queue:out:3",
                "await:400",
                "close:out",
                "open:in",
                "queue:in:32",
                "await:400",
                "close:in",
            ),
            port.log,
        )
        assertEquals(0, port.opened.getValue("out").positionAtQueue)
        assertEquals(3, port.opened.getValue("out").remainingAtQueue)
        assertEquals(0, port.opened.getValue("in").positionAtQueue)
        assertEquals(32, port.opened.getValue("in").remainingAtQueue)
        assertTrue(port.opened.getValue("out").buffer!!.isDirect)
        val sent = ByteArray(3)
        port.opened.getValue("out").buffer!!.duplicate().apply { position(0); limit(3) }.get(sent)
        assertEquals(payload.toList(), sent.toList())
    }

    @Test
    fun outTimeoutCancelsAndDoesNotOpenTheInEndpoint() {
        val port = ScriptedPort(awaitSteps = ArrayDeque(listOf(null)))
        assertNull(exchangeHidReport(port, OUT_ENDPOINT, IN_ENDPOINT, byteArrayOf(1), 32, 400))
        assertEquals(listOf("open:out", "queue:out:1", "await:400", "cancel:out", "close:out"), port.log)
    }

    @Test
    fun inTimeoutCancelsOnlyTheInRequest() {
        val port = ScriptedPort(awaitSteps = ArrayDeque(listOf("out", null)))
        assertNull(exchangeHidReport(port, OUT_ENDPOINT, IN_ENDPOINT, byteArrayOf(1), 32, 50))
        assertEquals(
            listOf(
                "open:out",
                "queue:out:1",
                "await:50",
                "close:out",
                "open:in",
                "queue:in:32",
                "await:50",
                "cancel:in",
                "close:in",
            ),
            port.log,
        )
    }

    @Test
    fun failedQueueClosesWithoutCancelOrWait() {
        val port = ScriptedPort(awaitSteps = ArrayDeque(), failQueue = "out")
        assertNull(exchangeHidReport(port, OUT_ENDPOINT, IN_ENDPOINT, byteArrayOf(1), 32, 400))
        assertEquals(listOf("open:out", "queue:out:1", "close:out"), port.log)
    }

    @Test
    fun completedInWithNoBytesIsNotAReport() {
        val port = ScriptedPort(awaitSteps = ArrayDeque(listOf("out", "in")), response = byteArrayOf())
        assertNull(exchangeHidReport(port, OUT_ENDPOINT, IN_ENDPOINT, byteArrayOf(1), 32, 400))
        assertFalse(port.log.contains("cancel:in"))
        assertTrue(port.log.contains("close:in"))
    }

    @Test
    fun activityCreatesUsbOnlyAfterSuperOnCreateAndInterruptPathDoesNotUseBulkTransfer() {
        val activity = File("src/main/java/com/tepmex/cornegame/MainActivity.kt").readText()
        val onCreate = activity.substringAfter("override fun onCreate")
        val superAt = onCreate.indexOf("super.onCreate")
        val hardwareAt = onCreate.indexOf("AndroidUsbHardware")
        assertTrue(superAt >= 0)
        assertTrue(hardwareAt > superAt)
        assertTrue(activity.contains("private var usbHardware: AndroidUsbHardware? = null"))
        assertFalse(activity.contains("val usbHardware = AndroidUsbHardware"))
        val usb = File("src/main/java/com/tepmex/cornegame/usb/AndroidUsbHardware.kt").readText()
        assertFalse(usb.contains("bulkTransfer("))
        assertTrue(usb.contains("UsbRequest"))
        assertTrue(usb.contains("requestWait"))
    }
}

private class ScriptedPort(
    private val awaitSteps: ArrayDeque<String?>,
    private val response: ByteArray = byteArrayOf(),
    val failQueue: String? = null,
) : HidPort {
    val log = mutableListOf<String>()
    val opened = mutableMapOf<String, ScriptedTransfer>()

    override fun open(endpointKey: String): HidTransfer {
        log += "open:$endpointKey"
        return ScriptedTransfer(endpointKey, this).also { opened[endpointKey] = it }
    }

    override fun await(timeoutMs: Long): Any? {
        log += "await:$timeoutMs"
        val step = awaitSteps.removeFirst() ?: return null
        val transfer = opened.getValue(step)
        if (step == IN_ENDPOINT && response.isNotEmpty()) {
            transfer.buffer!!.put(response)
        }
        return transfer.token
    }
}

private class ScriptedTransfer(
    val name: String,
    val port: ScriptedPort,
) : HidTransfer {
    override val token: Any = Any()
    var buffer: ByteBuffer? = null
    var positionAtQueue: Int = -1
    var remainingAtQueue: Int = -1

    override fun queue(buffer: ByteBuffer, length: Int): Boolean {
        port.log += "queue:$name:$length"
        this.buffer = buffer
        positionAtQueue = buffer.position()
        remainingAtQueue = buffer.remaining()
        assertTrue(buffer.isDirect)
        return name != port.failQueue
    }

    override fun cancel() {
        port.log += "cancel:$name"
    }

    override fun close() {
        port.log += "close:$name"
    }
}
