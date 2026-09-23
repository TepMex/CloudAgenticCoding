package com.tepmex.instantpinyin.ocr

data class TextBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val score: Float,
)

data class Letterbox(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val srcWidth: Int,
    val srcHeight: Int,
    val dst: Int = DET_SIZE,
)

const val DET_SIZE = 640

object ProbMapBoxes {
    fun find(
        prob: FloatArray,
        width: Int = DET_SIZE,
        height: Int = DET_SIZE,
        thresh: Float = 0.3f,
        minSide: Int = 6,
        minMean: Float = 0.5f,
        unclip: Float = 0.35f,
        maxBoxes: Int = 80,
    ): List<TextBox> {
        require(prob.size >= width * height)
        val parent = IntArray(width * height) { -1 }

        fun idx(x: Int, y: Int) = y * width + x
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = idx(x, y)
                if (prob[i] <= thresh) continue
                parent[i] = i
                if (x > 0 && parent[i - 1] >= 0) union(i, i - 1)
                if (y > 0 && parent[i - width] >= 0) union(i, i - width)
            }
        }

        data class Acc(
            var minX: Int = Int.MAX_VALUE,
            var minY: Int = Int.MAX_VALUE,
            var maxX: Int = 0,
            var maxY: Int = 0,
            var sum: Float = 0f,
            var count: Int = 0,
        )

        val acc = HashMap<Int, Acc>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = idx(x, y)
                if (parent[i] < 0) continue
                val r = find(i)
                val a = acc.getOrPut(r) { Acc() }
                if (x < a.minX) a.minX = x
                if (y < a.minY) a.minY = y
                if (x > a.maxX) a.maxX = x
                if (y > a.maxY) a.maxY = y
                a.sum += prob[i]
                a.count += 1
            }
        }

        val boxes = ArrayList<TextBox>(acc.size)
        for (a in acc.values) {
            if (a.count == 0) continue
            val mean = a.sum / a.count
            if (mean < minMean) continue
            var w = a.maxX - a.minX + 1
            var h = a.maxY - a.minY + 1
            if (w < minSide || h < minSide) continue
            val pad = (unclip * minOf(w, h)).toInt().coerceIn(2, 24)
            val x0 = (a.minX - pad).coerceAtLeast(0)
            val y0 = (a.minY - pad).coerceAtLeast(0)
            val x1 = (a.maxX + pad).coerceAtMost(width - 1)
            val y1 = (a.maxY + pad).coerceAtMost(height - 1)
            w = x1 - x0 + 1
            h = y1 - y0 + 1
            boxes.add(TextBox(x0, y0, w, h, mean))
        }

        boxes.sortWith(compareBy<TextBox> { it.y / 16 }.thenBy { it.x })
        return if (boxes.size > maxBoxes) boxes.subList(0, maxBoxes) else boxes
    }

    fun letterboxOf(srcWidth: Int, srcHeight: Int, dst: Int = DET_SIZE): Letterbox {
        val scale = minOf(dst.toFloat() / srcWidth, dst.toFloat() / srcHeight)
        val padX = (dst - srcWidth * scale) / 2f
        val padY = (dst - srcHeight * scale) / 2f
        return Letterbox(scale, padX, padY, srcWidth, srcHeight, dst)
    }

    fun mapToSource(box: TextBox, lb: Letterbox): TextBox {
        val x0 = ((box.x - lb.padX) / lb.scale).toInt()
        val y0 = ((box.y - lb.padY) / lb.scale).toInt()
        val x1 = ((box.x + box.width - lb.padX) / lb.scale).toInt()
        val y1 = ((box.y + box.height - lb.padY) / lb.scale).toInt()
        val left = x0.coerceIn(0, lb.srcWidth - 1)
        val top = y0.coerceIn(0, lb.srcHeight - 1)
        val right = x1.coerceIn(left + 1, lb.srcWidth)
        val bottom = y1.coerceIn(top + 1, lb.srcHeight)
        return TextBox(left, top, right - left, bottom - top, box.score)
    }
}
