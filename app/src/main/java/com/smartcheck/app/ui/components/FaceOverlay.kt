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

@Composable
fun FaceOverlay(
    faceBoxes: List<Rect>,
    frameWidth: Int,
    frameHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    mirrorX: Boolean,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return

    val scaleMode = when (contentScale) {
        ContentScale.Crop -> CoordinateScaleMode.Crop
        else -> CoordinateScaleMode.Fit
    }
    val mapper = androidx.compose.runtime.remember(frameWidth, frameHeight, viewWidth, viewHeight, scaleMode, mirrorX) {
        CoordinateMapper(
            imageWidth = frameWidth,
            imageHeight = frameHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            scaleMode = scaleMode,
            mirrorX = mirrorX
        )
    }
    val smoother = androidx.compose.runtime.remember { RectSmoother(alpha = 0.28f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        faceBoxes.forEach { rect ->
            if (rect.isEmpty) return@forEach
            val mapped = mapper.mapRect(rect)
            val smooth = smoother.update(mapped)
            drawRect(
                color = Color.Green,
                topLeft = Offset(smooth.left, smooth.top),
                size = Size(smooth.right - smooth.left, smooth.bottom - smooth.top),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
