package com.smartcheck.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * CameraX 预览组件
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameAnalyzed: (Bitmap) -> Unit = {},
    onCameraFacingChanged: (isFrontCamera: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val onFrameAnalyzedState by rememberUpdatedState(onFrameAnalyzed)
    val onCameraFacingChangedState by rememberUpdatedState(onCameraFacingChanged)

    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            boundCameraProvider?.unbindAll()
            executor.shutdown()
        }
    }
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            var lastAnalyzeTimeMs = 0L
            
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    boundCameraProvider = provider
                    
                    // 预览用例
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    // 图像分析用例
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { imageProxy ->
                                val now = System.currentTimeMillis()
                                if (now - lastAnalyzeTimeMs < 300L) {
                                    imageProxy.close()
                                } else {
                                    lastAnalyzeTimeMs = now
                                    processImageProxy(imageProxy, onFrameAnalyzedState)
                                }
                            }
                        }
                    
                    // 智能选择摄像头：优先前置，其次后置，最后使用任何可用的
                    val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

                    val cameraSelector = when {
                        hasFront -> {
                            Timber.d("Using front camera")
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                        hasBack -> {
                            Timber.d("Using back camera")
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        else -> {
                            // 使用第一个可用摄像头（兼容特殊设备如 AI 秤）
                            Timber.d("Using first available camera")
                            CameraSelector.Builder().build()
                        }
                    }

                    onCameraFacingChangedState(cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    
                    // 绑定生命周期
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    
                    Timber.d("Camera initialized successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Camera initialization failed")
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        }
    )
}

/**
 * 处理 ImageProxy 转 Bitmap
 */
private fun processImageProxy(imageProxy: ImageProxy, onFrameAnalyzed: (Bitmap) -> Unit) {
    try {
        val bitmap = imageProxy.toBitmap()
        
        // 旋转图像以匹配屏幕方向
        val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
        
        onFrameAnalyzed(rotatedBitmap)
    } catch (e: Exception) {
        Timber.e(e, "Failed to process image")
    } finally {
        imageProxy.close()
    }
}

/**
 * 旋转 Bitmap
 */
private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun ImageProxy.toBitmap(): Bitmap {
    require(format == ImageFormat.YUV_420_888) { "Unsupported image format: $format" }

    val nv21 = yuv420ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val out = ByteArray(width * height + (width * height) / 2)
    var pos = 0

    for (row in 0 until height) {
        val yRowStart = row * yRowStride
        if (yPixelStride == 1) {
            yBuffer.position(yRowStart)
            yBuffer.get(out, pos, width)
            pos += width
        } else {
            for (col in 0 until width) {
                out[pos++] = yBuffer.get(yRowStart + col * yPixelStride)
            }
        }
    }

    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        val uRowStart = row * uRowStride
        val vRowStart = row * vRowStride
        for (col in 0 until chromaWidth) {
            out[pos++] = vBuffer.get(vRowStart + col * vPixelStride)
            out[pos++] = uBuffer.get(uRowStart + col * uPixelStride)
        }
    }

    return out
}
