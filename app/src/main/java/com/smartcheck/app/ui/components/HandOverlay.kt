package com.smartcheck.app.ui.components

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.smartcheck.sdk.ForeignObjectInfo
import com.smartcheck.sdk.HandInfo
import kotlin.math.max
import kotlin.math.min

/**
 * 手部检测覆盖层
 * 绘制骨架、检测框和标签
 */
@Composable
fun HandOverlay(
    handInfos: List<HandInfo>,
    frameWidth: Int,
    frameHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    contentScale: ContentScale = ContentScale.Fit,
    mirrorX: Boolean,
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
    val smootherMap = androidx.compose.runtime.remember { mutableMapOf<Int, RectSmoother>() }
    val currentIds = androidx.compose.runtime.remember(handInfos) { handInfos.map { it.id }.toSet() }
    val staleIds = smootherMap.keys.filter { it !in currentIds }
    staleIds.forEach { smootherMap.remove(it) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cropScaleFactor = 1.5f

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
            style = android.graphics.Paint.Style.FILL
            setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
        }

        handInfos.forEach { hand ->
            val color = if (hand.hasForeignObject) Color.Red else Color.Green
            val smoother = smootherMap.getOrPut(hand.id) { RectSmoother(alpha = 0.28f) }
            val mapped = smoother.update(mapper.mapRect(hand.box))

            drawRect(
                color = color,
                topLeft = Offset(mapped.left, mapped.top),
                size = Size(mapped.right - mapped.left, mapped.bottom - mapped.top),
                style = Stroke(width = 3.dp.toPx())
            )

            drawContext.canvas.nativeCanvas.drawText(
                "${hand.label} ${"%.2f".format(hand.score)}",
                mapped.left,
                mapped.top - 10f,
                textPaint
            )

            val cx = ((hand.box.left + hand.box.right) / 2f).toInt()
            val cy = ((hand.box.top + hand.box.bottom) / 2f).toInt()
            val w = (hand.box.width()).toInt()
            val h = (hand.box.height()).toInt()
            val newW = (w * cropScaleFactor).toInt()
            val newH = (h * cropScaleFactor).toInt()

            val cropLeft = max(0, cx - newW / 2)
            val cropTop = max(0, cy - newH / 2)
            val cropLeftF = cropLeft.toFloat()
            val cropTopF = cropTop.toFloat()

            val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
                hand.foreignObjects
            } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                val tl = hand.keyPoints[0]
                val br = hand.keyPoints[1]
                listOf(
                    ForeignObjectInfo(
                        box = android.graphics.RectF(tl.x, tl.y, br.x, br.y),
                        score = hand.score,
                        label = hand.label,
                    )
                )
            } else {
                emptyList()
            }

            foreignObjects.forEach { fo ->
                val b = fo.box
                val foreignLeftRaw = cropLeftF + min(b.left, b.right)
                val foreignRightRaw = cropLeftF + max(b.left, b.right)
                val foreignTopRaw = cropTopF + min(b.top, b.bottom)
                val foreignBottomRaw = cropTopF + max(b.top, b.bottom)
                val foreignMapped = mapper.mapRect(android.graphics.RectF(
                    foreignLeftRaw,
                    foreignTopRaw,
                    foreignRightRaw,
                    foreignBottomRaw
                ))

                drawRect(
                    color = Color.Red,
                    topLeft = Offset(foreignMapped.left, foreignMapped.top),
                    size = Size(foreignMapped.right - foreignMapped.left, foreignMapped.bottom - foreignMapped.top),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            val skeletonPoints = if (hand.foreignObjects.isEmpty() && hand.hasForeignObject && hand.keyPoints.size > 2) {
                hand.keyPoints.subList(2, hand.keyPoints.size)
            } else {
                hand.keyPoints
            }

            val mappedSkeletonPoints = skeletonPoints.map { point ->
                val xRaw = cropLeftF + point.x
                val yRaw = cropTopF + point.y
                val mappedPoint = mapper.mapRect(android.graphics.RectF(xRaw, yRaw, xRaw + 1f, yRaw + 1f))
                PointF(mappedPoint.left, mappedPoint.top)
            }

            if (mappedSkeletonPoints.isNotEmpty()) {
                drawSkeleton(mappedSkeletonPoints, color)
            }

            mappedSkeletonPoints.forEach { point ->
                drawCircle(
                    color = Color.Yellow,
                    radius = 4.dp.toPx(),
                    center = Offset(point.x, point.y)
                )
            }
        }
    }
}

/**
 * 绘制手部骨架连线
 * 假设 keyPoints 顺序：0=手腕, 1-4=拇指, 5-8=食指... (根据 Mock 生成逻辑)
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkeleton(
    points: List<PointF>,
    color: Color
) {
    if (points.isEmpty()) return
    
    val wrist = points[0]
    
    // 遍历 5 根手指
    for (i in 0 until 5) {
        var startPoint = wrist
        // 每根手指 4 个点，在 list 中从 index 1 开始
        for (j in 0 until 4) {
            val index = 1 + i * 4 + j
            if (index < points.size) {
                val endPoint = points[index]
                drawLine(
                    color = color,
                    start = Offset(startPoint.x, startPoint.y),
                    end = Offset(endPoint.x, endPoint.y),
                    strokeWidth = 2.dp.toPx()
                )
                startPoint = endPoint
            }
        }
    }
}
