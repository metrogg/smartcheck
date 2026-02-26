package com.smartcheck.app.ui.components

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

enum class CoordinateScaleMode {
    Fit,
    Crop
}

data class MappedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class CoordinateMapper(
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    private val scaleMode: CoordinateScaleMode,
    private val mirrorX: Boolean = false
) {
    private val imageW = imageWidth.toFloat().coerceAtLeast(1f)
    private val imageH = imageHeight.toFloat().coerceAtLeast(1f)
    private val viewW = viewWidth.toFloat().coerceAtLeast(1f)
    private val viewH = viewHeight.toFloat().coerceAtLeast(1f)

    private val scaleX = viewW / imageW
    private val scaleY = viewH / imageH
    private val scale = when (scaleMode) {
        CoordinateScaleMode.Fit -> min(scaleX, scaleY)
        CoordinateScaleMode.Crop -> max(scaleX, scaleY)
    }
    private val offsetX = (viewW - imageW * scale) / 2f
    private val offsetY = (viewH - imageH * scale) / 2f

    fun mapRect(rect: Rect): MappedRect {
        val left = rect.left.toFloat()
        val top = rect.top.toFloat()
        val right = rect.right.toFloat()
        val bottom = rect.bottom.toFloat()
        return mapRectF(left, top, right, bottom)
    }

    fun mapRect(rect: RectF): MappedRect {
        return mapRectF(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun mapRectF(left: Float, top: Float, right: Float, bottom: Float): MappedRect {
        val x1 = mapX(left)
        val x2 = mapX(right)
        val y1 = mapY(top)
        val y2 = mapY(bottom)
        return MappedRect(
            left = min(x1, x2),
            top = min(y1, y2),
            right = max(x1, x2),
            bottom = max(y1, y2)
        )
    }

    private fun mapX(x: Float): Float {
        val xr = if (mirrorX) (imageW - x) else x
        return offsetX + xr * scale
    }

    private fun mapY(y: Float): Float {
        return offsetY + y * scale
    }
}

class RectSmoother(private val alpha: Float = 0.25f) {
    private var last: MappedRect? = null

    fun update(next: MappedRect): MappedRect {
        val prev = last
        if (prev == null) {
            last = next
            return next
        }
        val smoothed = MappedRect(
            left = lerp(prev.left, next.left, alpha),
            top = lerp(prev.top, next.top, alpha),
            right = lerp(prev.right, next.right, alpha),
            bottom = lerp(prev.bottom, next.bottom, alpha)
        )
        last = smoothed
        return smoothed
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
