package com.smartcheck.app.ui.components

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun FaceOverlay(
    faceBoxes: List<Rect>,
    frameWidth: Int,
    frameHeight: Int,
    mirrorX: Boolean,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    if (frameWidth <= 0 || frameHeight <= 0) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val frameWidthF = frameWidth.toFloat()
        val frameHeightF = frameHeight.toFloat()
        val scaleX = size.width / frameWidthF
        val scaleY = size.height / frameHeightF

        val (sx, sy, ox, oy) = when (contentScale) {
            ContentScale.Fit -> {
                val s = min(scaleX, scaleY)
                val w = frameWidthF * s
                val h = frameHeightF * s
                val dx = (size.width - w) / 2f
                val dy = (size.height - h) / 2f
                listOf(s, s, dx, dy)
            }
            ContentScale.Crop -> {
                val s = max(scaleX, scaleY)
                val w = frameWidthF * s
                val h = frameHeightF * s
                val dx = (size.width - w) / 2f
                val dy = (size.height - h) / 2f
                listOf(s, s, dx, dy)
            }
            else -> listOf(scaleX, scaleY, 0f, 0f)
        }

        fun mapX(xRaw: Float): Float {
            val xr = if (mirrorX) (frameWidthF - xRaw) else xRaw
            return ox + xr * sx
        }

        fun mapY(yRaw: Float): Float {
            return oy + yRaw * sy
        }

        faceBoxes.forEach { rect ->
            if (rect.isEmpty) return@forEach

            val leftRaw = rect.left.toFloat()
            val rightRaw = rect.right.toFloat()
            val topRaw = rect.top.toFloat()
            val bottomRaw = rect.bottom.toFloat()

            val x1 = mapX(leftRaw)
            val x2 = mapX(rightRaw)
            val left = min(x1, x2)
            val right = max(x1, x2)
            val top = mapY(topRaw)
            val bottom = mapY(bottomRaw)

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
