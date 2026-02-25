package com.smartcheck.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens

@Composable
fun CameraCaptureDialog(
    cameraId: String,
    onCapture: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(0.8f),
                color = Color.White,
                shape = RoundedCornerShape(Dimens.CornerRadius)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.PaddingNormal),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "请对准摄像头并拍照",
                            fontSize = Dimens.TextSizeNormal,
                            color = Color.Black
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭"
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
                    ) {
                        DualCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            preferredCameraId = cameraId,
                            onFrameAnalyzed = { bitmap ->
                                latestFrame = bitmap
                            }
                        )

                        CaptureGuideOverlay(cameraId = cameraId)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.PaddingLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                latestFrame?.let { frame ->
                                    onCapture(frame)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(BrandGreen, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "拍照",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureGuideOverlay(cameraId: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 3.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
        val guideColor = BrandGreen.copy(alpha = 0.8f)

        val rect = if (cameraId == "100") {
            val guideSize = size.minDimension * 0.6f
            val left = (size.width - guideSize) / 2f
            val top = (size.height - guideSize) / 2f
            Rect(Offset(left, top), androidx.compose.ui.geometry.Size(guideSize, guideSize))
        } else {
            val rectWidth = size.width * 0.75f
            val rectHeight = size.height * 0.4f
            val left = (size.width - rectWidth) / 2f
            val top = (size.height - rectHeight) / 2f
            Rect(Offset(left, top), androidx.compose.ui.geometry.Size(rectWidth, rectHeight))
        }

        drawRect(
            color = guideColor,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = strokeWidth, pathEffect = dash)
        )
    }
}
