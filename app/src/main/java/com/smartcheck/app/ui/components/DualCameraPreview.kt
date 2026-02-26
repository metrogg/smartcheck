package com.smartcheck.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
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

enum class CameraType {
    FACE,   // 人脸摄像头（通常是前置摄像头）
    HAND,   // 手部摄像头（通常是后置摄像头或外接摄像头）
    FRONT,  // 前置摄像头
    BACK    // 后置摄像头
}

enum class CameraInitState {
    Initializing,
    Ready,
    Error
}

@Composable
fun DualCameraPreview(
    modifier: Modifier = Modifier,
    cameraType: CameraType = CameraType.FACE,
    preferredCameraId: String? = null,
    enableAnalysis: Boolean = true,
    analysisThrottleMs: Long = 200L,
    onFrameAnalyzed: (Bitmap) -> Unit = {},
    onCameraInfo: (cameraId: String, lensFacing: Int) -> Unit = { _, _ -> },
    onCameraState: (CameraInitState) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val onFrameAnalyzedState by rememberUpdatedState(onFrameAnalyzed)
    val onCameraInfoState by rememberUpdatedState(onCameraInfo)
    val onCameraStateState by rememberUpdatedState(onCameraState)

    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            boundCameraProvider?.unbindAll()
            executor.shutdown()
        }
    }

    DisposableEffect(cameraType, preferredCameraId) {
        onDispose {
            boundCameraProvider?.unbindAll()
        }
    }
    
    val bindKey = remember(cameraType, preferredCameraId, enableAnalysis) { Any() }
    LaunchedEffect(bindKey) {
        onCameraStateState(CameraInitState.Initializing)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                boundCameraProvider = provider

                val cameraSelector = buildCameraSelector(provider, cameraType, preferredCameraId)
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setTargetRotation(rotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val useCases = mutableListOf<UseCase>(preview)
                val useCaseGroupBuilder = UseCaseGroup.Builder()

                if (enableAnalysis) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 360))
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            var lastAnalyzeTimeMs = 0L
                            it.setAnalyzer(executor) { imageProxy ->
                                val now = System.currentTimeMillis()
                                if (now - lastAnalyzeTimeMs < analysisThrottleMs) {
                                    imageProxy.close()
                                } else {
                                    lastAnalyzeTimeMs = now
                                    processImageProxy(imageProxy, onFrameAnalyzedState)
                                }
                            }
                        }
                    useCases.add(imageAnalysis)
                }

                val viewPort = ViewPort.Builder(Rational(16, 9), rotation)
                    .setScaleType(ViewPort.FIT)
                    .build()
                useCaseGroupBuilder.setViewPort(viewPort)
                useCases.forEach { useCaseGroupBuilder.addUseCase(it) }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroupBuilder.build()
                )

                val cameraId = try {
                    Camera2CameraInfo.from(camera.cameraInfo).cameraId
                } catch (e: Exception) {
                    "unknown"
                }
                val lensFacing = runCatching { camera.cameraInfo.lensFacing }.getOrDefault(-1)
                onCameraInfoState(cameraId, lensFacing)
                Timber.d("Selected camera: type=$cameraType id=$cameraId lensFacing=$lensFacing")
                onCameraStateState(CameraInitState.Ready)
            } catch (e: Exception) {
                Timber.e(e, "DualCameraPreview initialization failed")
                onCameraStateState(CameraInitState.Error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { previewView }
    )
}

private fun buildCameraSelector(
    provider: ProcessCameraProvider,
    cameraType: CameraType,
    preferredCameraId: String?
): CameraSelector {
    if (!preferredCameraId.isNullOrBlank()) {
        val filterById = CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter {
                    runCatching { Camera2CameraInfo.from(it).cameraId == preferredCameraId }
                        .getOrDefault(false)
                }
            }
            .build()
        return filterById
    }

    // 无强制 ID 时，尽量选择前/后摄；若设备未标注 lensFacing，则回退到“第一个可用”避免验证失败。
    return when (cameraType) {
        CameraType.FACE -> {
            runCatching {
                if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    Timber.d("Using FRONT camera for FACE")
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    Timber.d("Using BACK camera for FACE (front not available)")
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    Timber.w("No lensFacing cameras reported, using first available")
                    CameraSelector.Builder().build()
                }
            }.getOrElse {
                Timber.w(it, "CameraSelector fallback to first available (FACE)")
                CameraSelector.Builder().build()
            }
        }
        CameraType.HAND -> {
            runCatching {
                if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    Timber.d("Using BACK camera for HAND")
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    Timber.d("Using FRONT camera for HAND (back not available)")
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    Timber.w("No lensFacing cameras reported, using first available")
                    CameraSelector.Builder().build()
                }
            }.getOrElse {
                Timber.w(it, "CameraSelector fallback to first available (HAND)")
                CameraSelector.Builder().build()
            }
        }
        CameraType.FRONT -> runCatching { CameraSelector.DEFAULT_FRONT_CAMERA }.getOrElse {
            Timber.w(it, "CameraSelector FRONT fallback to any")
            CameraSelector.Builder().build()
        }
        CameraType.BACK -> runCatching { CameraSelector.DEFAULT_BACK_CAMERA }.getOrElse {
            Timber.w(it, "CameraSelector BACK fallback to any")
            CameraSelector.Builder().build()
        }
    }
}

private fun processImageProxy(imageProxy: ImageProxy, onFrameAnalyzed: (Bitmap) -> Unit) {
    try {
        val baseBitmap = imageProxy.toBitmapCompat()
        val cropRect = imageProxy.cropRect
        val croppedBitmap = if (cropRect.left != 0 || cropRect.top != 0 ||
            cropRect.right != baseBitmap.width || cropRect.bottom != baseBitmap.height
        ) {
            val safeWidth = cropRect.width().coerceAtMost(baseBitmap.width - cropRect.left)
            val safeHeight = cropRect.height().coerceAtMost(baseBitmap.height - cropRect.top)
            Bitmap.createBitmap(baseBitmap, cropRect.left, cropRect.top, safeWidth, safeHeight)
        } else {
            baseBitmap
        }
        if (croppedBitmap !== baseBitmap) {
            baseBitmap.recycle()
        }
        val rotatedBitmap = rotateBitmap(croppedBitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
        onFrameAnalyzed(rotatedBitmap)
    } catch (e: Exception) {
        Timber.e(e, "Failed to process image")
    } finally {
        imageProxy.close()
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun ImageProxy.toBitmapCompat(): Bitmap {
    if (format == PixelFormat.RGBA_8888) {
        return rgba8888ToBitmap(this)
    }

    require(format == ImageFormat.YUV_420_888) { "Unsupported image format: $format" }

    val nv21 = yuv420ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private val rgbaRowBufferTL = ThreadLocal<ByteArray>()
private val argbRowBufferTL = ThreadLocal<IntArray>()

private fun rgba8888ToBitmap(image: ImageProxy): Bitmap {
    val plane = image.planes.firstOrNull() ?: error("No planes")
    val buffer = plane.buffer
    buffer.rewind()

    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    require(pixelStride == 4) { "Unexpected RGBA pixelStride=$pixelStride" }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val rowSize = width * 4
    val row = (rgbaRowBufferTL.get()?.takeIf { it.size >= rowSize } ?: ByteArray(rowSize)).also {
        rgbaRowBufferTL.set(it)
    }
    val pixelBuffer = (argbRowBufferTL.get()?.takeIf { it.size >= width } ?: IntArray(width)).also {
        argbRowBufferTL.set(it)
    }
    for (y in 0 until height) {
        buffer.position(y * rowStride)
        buffer.get(row, 0, row.size)

        var i = 0
        for (x in 0 until width) {
            val r = row[i].toInt() and 0xFF
            val g = row[i + 1].toInt() and 0xFF
            val b = row[i + 2].toInt() and 0xFF
            val a = row[i + 3].toInt() and 0xFF
            pixelBuffer[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            i += 4
        }
        bitmap.setPixels(pixelBuffer, 0, width, 0, y, width, 1)
    }
    return bitmap
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
