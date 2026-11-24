package com.tepmex.assumptions.domain

import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Paints the hit-test geometry into PPM frames. Android unit tests cannot use java.awt,
 * so the pixels come straight from [WheelLayout].
 */
class WheelGallery {
    @Test
    fun writeWheelFrames() {
        val dir = File("/opt/cursor/artifacts")
        if (!dir.isDirectory || !dir.canWrite()) return
        paint(dir, "wheel-closed.ppm", WheelState())
        val opened = WheelReducer.reduce(WheelState(), WheelHit.PrimaryBand(0, 0))
        paint(dir, "wheel-open-one.ppm", opened)
        val deep = WheelReducer.reduce(opened, WheelHit.PrimaryBand(0, 1))
        paint(dir, "wheel-open-two.ppm", deep)
        paintQuadrants(dir, "report-quadrants.ppm")
    }

    private fun paint(dir: File, name: String, state: WheelState) {
        val size = 640
        val pixels = IntArray(size * size)
        val paper = 0xF3EEE6
        for (y in 0 until size) {
            for (x in 0 until size) {
                val hit = WheelLayout.hitTest(state, size.toFloat(), size.toFloat(), x + 0.5f, y + 0.5f)
                pixels[y * size + x] = when (hit) {
                    null -> paper
                    WheelHit.Hub -> 0xFFFBF7
                    is WheelHit.PrimaryBand -> rgb(Emotions.band(hit.index, hit.depth).colorArgb)
                    is WheelHit.Dyad -> rgb(Emotions.find(hit.id)?.colorArgb ?: 0xFFCCCCCC)
                }
            }
        }
        writePpm(File(dir, name), size, size, pixels)
    }

    private fun paintQuadrants(dir: File, name: String) {
        val width = 640
        val height = 640
        val pixels = IntArray(width * height) { 0xF3EEE6 }
        val cells = listOf(
            Quadrant.NegativeNegative to (32 to 32),
            Quadrant.NegativePositive to (336 to 32),
            Quadrant.PositiveNegative to (32 to 336),
            Quadrant.PositivePositive to (336 to 336),
        )
        for ((quadrant, origin) in cells) {
            val color = rgb(quadrant.colorArgb)
            for (y in origin.second until origin.second + 272) {
                for (x in origin.first until origin.first + 272) {
                    pixels[y * width + x] = color
                }
            }
        }
        writePpm(File(dir, name), width, height, pixels)
    }

    private fun rgb(argb: Long): Int = (argb and 0xFFFFFF).toInt()

    private fun writePpm(file: File, width: Int, height: Int, pixels: IntArray) {
        FileOutputStream(file).use { out ->
            out.write("P6\n$width $height\n255\n".toByteArray())
            val bytes = ByteArray(width * height * 3)
            for (index in pixels.indices) {
                val color = pixels[index]
                bytes[index * 3] = ((color shr 16) and 0xFF).toByte()
                bytes[index * 3 + 1] = ((color shr 8) and 0xFF).toByte()
                bytes[index * 3 + 2] = (color and 0xFF).toByte()
            }
            out.write(bytes)
        }
    }
}
