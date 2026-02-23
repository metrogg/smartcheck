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
import androidx.compose.ui.unit.dp

@Composable
fun FaceOverlay(
    faceBoxes: List<Rect>,
    frameWidth: Int,
    frameHeight: Int,
    mirrorX: Boolean,
    modifier: Modifier = Modifier
) {
    if (frameWidth <= 0 || frameHeight <= 0) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / frameWidth.toFloat()
        val scaleY = size.height / frameHeight.toFloat()

        faceBoxes.forEach { rect ->
            if (rect.isEmpty) return@forEach

            val leftRaw = rect.left.toFloat()
            val rightRaw = rect.right.toFloat()
            val topRaw = rect.top.toFloat()
            val bottomRaw = rect.bottom.toFloat()

            val left = if (mirrorX) (frameWidth - rightRaw) * scaleX else leftRaw * scaleX
            val right = if (mirrorX) (frameWidth - leftRaw) * scaleX else rightRaw * scaleX
            val top = topRaw * scaleY
            val bottom = bottomRaw * scaleY

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
