package com.tepmex.ankientertainer.ui

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * Pinch-zoom / pan image view for mnemonic posters.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val imgMatrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var minScale = 1f
    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = imgMatrix
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { fitToView() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToView()
    }

    private fun fitToView() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return
        val scale = min(width / dw, height / dh)
        minScale = scale
        currentScale = scale
        imgMatrix.reset()
        imgMatrix.postScale(scale, scale)
        imgMatrix.postTranslate((width - dw * scale) / 2f, (height - dh * scale) / 2f)
        imageMatrix = imgMatrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress && event.pointerCount == 1) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    imgMatrix.postTranslate(dx, dy)
                    clamp()
                    imageMatrix = imgMatrix
                }
            }
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (currentScale * detector.scaleFactor).coerceIn(minScale, MAX_SCALE)
            val applied = next / currentScale
            currentScale = next
            imgMatrix.postScale(applied, applied, detector.focusX, detector.focusY)
            clamp()
            imageMatrix = imgMatrix
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale * 1.1f) {
                fitToView()
            } else {
                val target = min(MAX_SCALE, minScale * 3f)
                val applied = target / currentScale
                currentScale = target
                imgMatrix.postScale(applied, applied, e.x, e.y)
                clamp()
                imageMatrix = imgMatrix
            }
            return true
        }
    }

    private fun clamp() {
        val d = drawable ?: return
        val values = FloatArray(9)
        imgMatrix.getValues(values)
        val dw = d.intrinsicWidth * values[Matrix.MSCALE_X]
        val dh = d.intrinsicHeight * values[Matrix.MSCALE_Y]
        val tx = values[Matrix.MTRANS_X]
        val ty = values[Matrix.MTRANS_Y]
        val nx = if (dw <= width) (width - dw) / 2f else tx.coerceIn(width - dw, 0f)
        val ny = if (dh <= height) (height - dh) / 2f else ty.coerceIn(height - dh, 0f)
        imgMatrix.postTranslate(nx - tx, ny - ty)
    }

    companion object {
        private const val MAX_SCALE = 8f
    }
}
