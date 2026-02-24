package com.smartcheck.app.ui.screens
 
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.smartcheck.app.ui.components.CameraType
import com.smartcheck.app.ui.components.DualCameraPreview
import com.smartcheck.app.ui.components.HandOverlay
import com.smartcheck.app.viewmodel.MainViewModel
import com.smartcheck.sdk.HandDetector
import com.smartcheck.sdk.ForeignObjectInfo
import com.smartcheck.sdk.HandInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private enum class DetectMode { REALTIME, IMAGE }

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HandCheckScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val handInfos by viewModel.handDetectionState.collectAsState()
    val isHandDetecting by viewModel.isHandDetecting.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var lastFrameWidth by remember { mutableIntStateOf(0) }
    var lastFrameHeight by remember { mutableIntStateOf(0) }
    var currentCameraType by remember { mutableStateOf(CameraType.HAND) }
    var cameraInfo by remember { mutableStateOf("未连接" to -1) }
    var isCameraRunning by remember { mutableStateOf(false) }
    var cameraLensFacing by remember { mutableIntStateOf(-1) }

    var detectMode by remember { mutableStateOf(DetectMode.REALTIME) }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }

    var stillOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var stillAnnotated by remember { mutableStateOf<Bitmap?>(null) }
    var stillHandInfos by remember { mutableStateOf<List<HandInfo>>(emptyList()) }
    var isStillProcessing by remember { mutableStateOf(false) }
    var lastPickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickImageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detectMode) {
        // Keep the two modes isolated so UI state stays crisp.
        if (detectMode == DetectMode.IMAGE) {
            viewModel.clearHandDetection()
            latestFrame = null
        } else {
            isStillProcessing = false
            latestFrame = null
        }
    }

    val preferredCameraId = when (currentCameraType) {
        CameraType.HAND -> "102"
        CameraType.FACE -> "100"
        CameraType.FRONT -> "100"
        CameraType.BACK -> "102"
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        if (originalWidth <= maxDimension && originalHeight <= maxDimension) return bitmap
        val (resizedWidth, resizedHeight) = if (originalWidth > originalHeight) {
            maxDimension to (originalHeight * maxDimension.toFloat() / originalWidth).toInt()
        } else {
            (originalWidth * maxDimension.toFloat() / originalHeight).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

    fun drawDetections(bitmap: Bitmap, hands: List<HandInfo>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val boxPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }
        val foreignPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
            color = android.graphics.Color.RED
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            style = android.graphics.Paint.Style.FILL
            setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
        }

        hands.forEach { hand ->
            val color = if (hand.hasForeignObject) android.graphics.Color.RED else android.graphics.Color.GREEN
            boxPaint.color = color
            canvas.drawRect(hand.box, boxPaint)
            canvas.drawText(hand.label, hand.box.left, (hand.box.top - 10f).coerceAtLeast(40f), textPaint)

            val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
                hand.foreignObjects
            } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                // Legacy fallback: 1 box stored in keyPoints[0..1]
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

            // Draw foreign boxes (relative to 1.5x crop) onto original image.
            if (foreignObjects.isNotEmpty()) {
                val cropScaleFactor = 1.5f
                val cx = ((hand.box.left + hand.box.right) / 2f).toInt()
                val cy = ((hand.box.top + hand.box.bottom) / 2f).toInt()
                val w = (hand.box.width()).toInt()
                val h = (hand.box.height()).toInt()
                val newW = (w * cropScaleFactor).toInt()
                val newH = (h * cropScaleFactor).toInt()
                val cropLeft = (cx - newW / 2).coerceAtLeast(0)
                val cropTop = (cy - newH / 2).coerceAtLeast(0)

                foreignObjects.forEach { fo ->
                    val b = fo.box
                    val left = cropLeft + minOf(b.left, b.right)
                    val top = cropTop + minOf(b.top, b.bottom)
                    val right = cropLeft + maxOf(b.left, b.right)
                    val bottom = cropTop + maxOf(b.top, b.bottom)
                    canvas.drawRect(left, top, right, bottom, foreignPaint)
                }
            }
        }

        return mutableBitmap
    }

    fun runStillDetection(bitmap: Bitmap) {
        if (isStillProcessing) return
        isStillProcessing = true
        stillOriginal = bitmap
        stillAnnotated = null
        stillHandInfos = emptyList()

        scope.launch {
            try {
                val results = withContext(Dispatchers.Default) { HandDetector.detect(bitmap) }
                stillHandInfos = results
                stillAnnotated = if (results.isNotEmpty()) drawDetections(bitmap, results) else null
            } catch (e: Exception) {
                Timber.e(e, "Still image hand detection failed")
            } finally {
                isStillProcessing = false
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (isStillProcessing) return@rememberLauncherForActivityResult

        pickImageError = null
        lastPickedUri = uri
        try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap == null) {
                    pickImageError = "选择的文件不是图片或无法读取"
                    return@rememberLauncherForActivityResult
                }
                val scaled = scaleBitmapDown(bitmap, 1280)
                detectMode = DetectMode.IMAGE
                runStillDetection(scaled)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load picked image")
            pickImageError = "读取图片失败: ${e.message ?: "未知错误"}"
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isStillProcessing) return@rememberLauncherForActivityResult

        pickImageError = null
        lastPickedUri = uri
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap == null) {
                    pickImageError = "选择的文件不是图片或无法读取"
                    return@rememberLauncherForActivityResult
                }
                val scaled = scaleBitmapDown(bitmap, 1280)
                detectMode = DetectMode.IMAGE
                runStillDetection(scaled)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load picked image (GetContent)")
            pickImageError = "读取图片失败: ${e.message ?: "未知错误"}"
        }
    }

    fun launchPickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Use */* so Downloads images with unknown mimeType still show up.
            type = "*/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            runCatching {
                val downloads = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloads)
            }
        }
        pickImageLauncher.launch(intent)
    }

    fun launchPickFromGallery() {
        pickGalleryLauncher.launch("image/*")
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("手部异物检测") }
            )
        }
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(12.dp)
        ) {
            // Landscape-first layout: prefer two-pane on typical industrial tablets,
            // but fall back to a stacked layout on narrow or very short displays.
            val isCompact = maxWidth < 900.dp || maxHeight < 520.dp

            @Composable
            fun PreviewArea(modifier: Modifier) {
                Card(
                    modifier = modifier,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (detectMode == DetectMode.REALTIME) {
                            // Keep preview aspect ratio without making it look "small" on wide/tall cards.
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val targetAspect = 16f / 9f
                                val containerAspect = if (maxHeight == 0.dp) targetAspect else (maxWidth / maxHeight)

                                val previewModifier = if (containerAspect > targetAspect) {
                                    Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(targetAspect)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(targetAspect)
                                }

                                Box(modifier = previewModifier) {
                                    if (cameraPermissionState.status.isGranted) {
                                        DualCameraPreview(
                                            modifier = Modifier.fillMaxSize(),
                                            cameraType = currentCameraType,
                                            preferredCameraId = preferredCameraId,
                                            enableAnalysis = true,
                                            analysisThrottleMs = 200L,
                                            onFrameAnalyzed = { bitmap ->
                                                lastFrameWidth = bitmap.width
                                                lastFrameHeight = bitmap.height
                                                if (detectMode == DetectMode.REALTIME) {
                                                    viewModel.processHandDetection(bitmap)
                                                }
                                            },
                                            onCameraInfo = { cameraId, lensFacingValue ->
                                                cameraInfo = cameraId to lensFacingValue
                                                isCameraRunning = true
                                                cameraLensFacing = lensFacingValue
                                            }
                                        )

                                        HandOverlay(
                                            handInfos = handInfos,
                                            frameWidth = lastFrameWidth,
                                            frameHeight = lastFrameHeight,
                                            contentScale = ContentScale.Fit,
                                            mirrorX = cameraLensFacing == CameraSelector.LENS_FACING_FRONT,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "需要相机权限",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                                Text("授予权限")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (stillOriginal != null) {
                                Image(
                                    bitmap = (stillAnnotated ?: stillOriginal!!).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "拍照或选择图片后在这里显示",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "右侧点击 '拍照检测' / '图片检测'",
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        if (detectMode == DetectMode.REALTIME && isHandDetecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White
                            )
                        }

                        if (detectMode == DetectMode.IMAGE && isStillProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            @Composable
            fun PanelContent(modifier: Modifier) {
                val rows: List<@Composable () -> Unit> = buildList {
                    add {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("模式", fontWeight = FontWeight.Bold)
                                TabRow(selectedTabIndex = if (detectMode == DetectMode.REALTIME) 0 else 1) {
                                    Tab(
                                        selected = detectMode == DetectMode.REALTIME,
                                        onClick = { detectMode = DetectMode.REALTIME },
                                        text = { Text("实时") }
                                    )
                                    Tab(
                                        selected = detectMode == DetectMode.IMAGE,
                                        onClick = { detectMode = DetectMode.IMAGE },
                                        text = { Text("拍照/图片") }
                                    )
                                }
                            }
                        }
                    }

                    add {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("操作", fontWeight = FontWeight.Bold)
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        currentCameraType = when (currentCameraType) {
                                            CameraType.HAND -> CameraType.FACE
                                            CameraType.FACE -> CameraType.HAND
                                            else -> CameraType.HAND
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.SwapHoriz, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("切换摄像头")
                                }

                                 if (detectMode == DetectMode.IMAGE) {
                                     Button(
                                         modifier = Modifier.fillMaxWidth(),
                                         enabled = latestFrame != null && !isStillProcessing,
                                         onClick = {
                                             val frame = latestFrame ?: return@Button
                                             val snapshot = scaleBitmapDown(frame.copy(Bitmap.Config.ARGB_8888, false), 1280)
                                             lastPickedUri = null
                                             runStillDetection(snapshot)
                                         }
                                     ) {
                                        Icon(Icons.Default.CameraAlt, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("拍照检测")
                                    }

                                     OutlinedButton(
                                         modifier = Modifier.fillMaxWidth(),
                                         enabled = !isStillProcessing,
                                         onClick = { launchPickImage() }
                                     ) {
                                         Icon(Icons.Default.PhotoLibrary, null)
                                         Spacer(Modifier.width(8.dp))
                                         Text("图片检测")
                                     }

                                     OutlinedButton(
                                         modifier = Modifier.fillMaxWidth(),
                                         enabled = !isStillProcessing,
                                         onClick = { launchPickFromGallery() }
                                     ) {
                                         Icon(Icons.Default.PhotoLibrary, null)
                                         Spacer(Modifier.width(8.dp))
                                         Text("相册选择(兼容)")
                                     }
                                 }
                             }
                         }
                     }

                    add {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("摄像头信息", fontWeight = FontWeight.Bold)
                                Text("当前: ${currentCameraType.name}")
                                Text("状态: ${if (isCameraRunning) "运行中" else "未连接"}")
                                Text("ID: ${cameraInfo.first}")
                            }
                        }
                    }

                    if (detectMode == DetectMode.REALTIME) {
                        add {
                            val hasIssue = handInfos.any { it.hasForeignObject }
                            val foreignList = handInfos.flatMap { it.foreignObjects }
                            val foreignCount = foreignList.size
                            val foreignSummary = foreignList.groupingBy { it.label }.eachCount()
                                .entries
                                .sortedByDescending { it.value }
                                .joinToString { "${it.key} x${it.value}" }
                            val statusText = when {
                                !isCameraRunning -> "等待相机..."
                                handInfos.isEmpty() -> "未检测到手部"
                                hasIssue -> "发现异物/伤口"
                                else -> "检测通过"
                            }

                            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("实时结果", fontWeight = FontWeight.Bold)
                                    Text(statusText, fontWeight = FontWeight.Bold)
                                    Text("检测到手: ${handInfos.size}")
                                    if (foreignCount > 0) {
                                        Text("异物数: $foreignCount")
                                        if (foreignSummary.isNotBlank()) {
                                            Text("类别: $foreignSummary", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        add {
                            val foreignList = stillHandInfos.flatMap { it.foreignObjects }
                            val foreignCount = foreignList.size
                            val foreignSummary = foreignList.groupingBy { it.label }.eachCount()
                                .entries
                                .sortedByDescending { it.value }
                                .joinToString { "${it.key} x${it.value}" }
                            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("图片结果", fontWeight = FontWeight.Bold)
                                    if (pickImageError != null) {
                                        Text(
                                            text = pickImageError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        when {
                                            isStillProcessing -> "处理中..."
                                            stillOriginal == null -> "未开始"
                                            stillHandInfos.isEmpty() -> "未检测到手部"
                                            stillHandInfos.any { it.hasForeignObject } -> "发现异物/伤口"
                                            else -> "检测通过"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("检测到手: ${stillHandInfos.size}")
                                    if (foreignCount > 0) {
                                        Text("异物数: $foreignCount")
                                        if (foreignSummary.isNotBlank()) {
                                            Text("类别: $foreignSummary", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(rows) { item -> item() }
                }
            }

            if (isCompact) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PreviewArea(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }

                    item {
                        PanelContent(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PreviewArea(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = 360.dp)
                        )
                    }

                    PanelContent(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 360.dp, max = 420.dp)
                    )
                }
            }

            // In IMAGE mode we still need a live frame source for "拍照检测",
            // but we don't want to show the live preview. Keep it hidden.
            if (detectMode == DetectMode.IMAGE && cameraPermissionState.status.isGranted) {
                DualCameraPreview(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    cameraType = currentCameraType,
                    preferredCameraId = preferredCameraId,
                    enableAnalysis = true,
                    analysisThrottleMs = 500L,
                    onFrameAnalyzed = { bitmap ->
                        latestFrame = bitmap
                    },
                    onCameraInfo = { cameraId, lensFacingValue ->
                        cameraInfo = cameraId to lensFacingValue
                        isCameraRunning = true
                        cameraLensFacing = lensFacingValue
                    }
                )
            }
        }
    }
}
