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
    contentScale: ContentScale = ContentScale.FillBounds,
    mirrorX: Boolean,
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

        val cropScaleFactor = 1.5f
        
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
            style = android.graphics.Paint.Style.FILL
            setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
        }
        
        handInfos.forEach { hand ->
            // 1. 确定颜色
            val color = if (hand.hasForeignObject) Color.Red else Color.Green

            val leftRaw = hand.box.left
            val rightRaw = hand.box.right
            val topRaw = hand.box.top
            val bottomRaw = hand.box.bottom

            val x1 = mapX(leftRaw)
            val x2 = mapX(rightRaw)
            val left = min(x1, x2)
            val right = max(x1, x2)
            val top = mapY(topRaw)
            val bottom = mapY(bottomRaw)
            
            // 2. 绘制检测框
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )
            
            // 3. 绘制标签
            drawContext.canvas.nativeCanvas.drawText(
                "${hand.label} ${"%.2f".format(hand.score)}",
                left,
                top - 10f,
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

            if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                val tl = hand.keyPoints[0]
                val br = hand.keyPoints[1]

                val foreignLeftRaw = cropLeftF + min(tl.x, br.x)
                val foreignRightRaw = cropLeftF + max(tl.x, br.x)
                val foreignTopRaw = cropTopF + min(tl.y, br.y)
                val foreignBottomRaw = cropTopF + max(tl.y, br.y)

                val fx1 = mapX(foreignLeftRaw)
                val fx2 = mapX(foreignRightRaw)
                val foreignLeft = min(fx1, fx2)
                val foreignRight = max(fx1, fx2)
                val foreignTop = mapY(foreignTopRaw)
                val foreignBottom = mapY(foreignBottomRaw)

                drawRect(
                    color = Color.Red,
                    topLeft = Offset(foreignLeft, foreignTop),
                    size = Size(foreignRight - foreignLeft, foreignBottom - foreignTop),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            val skeletonPoints = if (hand.hasForeignObject && hand.keyPoints.size > 2) {
                hand.keyPoints.subList(2, hand.keyPoints.size)
            } else {
                hand.keyPoints
            }

            val mappedSkeletonPoints = skeletonPoints.map { point ->
                val xRaw = cropLeftF + point.x
                val yRaw = cropTopF + point.y
                val x = mapX(xRaw)
                val y = mapY(yRaw)
                PointF(x, y)
            }
            
            // 4. 绘制骨架 (关键点连线)
            if (mappedSkeletonPoints.isNotEmpty()) {
                drawSkeleton(mappedSkeletonPoints, color)
            }
            
            // 5. 绘制关键点
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
