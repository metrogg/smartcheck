package com.smartcheck.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.content.Intent
import android.app.Activity
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.viewmodel.MainViewModel
import com.smartcheck.sdk.ForeignObjectInfo
import com.smartcheck.sdk.HandInfo
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * 手部检测测试界面 - 模仿 Gradio Demo 布局
 */
@Composable
fun HandTestScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    // Reserved for future latency display.
    @Suppress("UNUSED_VARIABLE")
    var processingTime by remember { mutableStateOf(0L) }
    
    val handInfos by viewModel.handDetectionState.collectAsState()
    
    // 当检测结果更新时，绘制标注图
    LaunchedEffect(handInfos, originalBitmap) {
        if (originalBitmap != null && handInfos.isNotEmpty()) {
            annotatedBitmap = drawDetections(originalBitmap!!, handInfos)
            isProcessing = false
        } else if (originalBitmap != null && isProcessing && handInfos.isEmpty()) {
            // 还在处理中，或者处理完没结果
            // 如果这里没有处理完成的信号，可能需要 viewModel 加一个 loading state
            // 暂时手动在选择图片时设 true，在收到结果时设 false
        }
    }

    // 选择图片
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (isProcessing) return@rememberLauncherForActivityResult
        try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
            if (bitmap == null) return@rememberLauncherForActivityResult

            // 限制图片大小，避免 OOM
            val scaledBitmap = scaleBitmapDown(bitmap, 1024)
            originalBitmap = scaledBitmap
            annotatedBitmap = null // 清除旧结果
            isProcessing = true
            
            val startTime = System.currentTimeMillis()
            viewModel.processHandDetection(scaledBitmap)
            
            // 简单的计时逻辑（实际应该在 viewModel 中做）
            processingTime = System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            Timber.e(e, "Failed to load image")
            isProcessing = false
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isProcessing) return@rememberLauncherForActivityResult

        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
            if (bitmap == null) return@rememberLauncherForActivityResult

            val scaledBitmap = scaleBitmapDown(bitmap, 1024)
            originalBitmap = scaledBitmap
            annotatedBitmap = null
            isProcessing = true
            viewModel.processHandDetection(scaledBitmap)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load image (GetContent)")
            isProcessing = false
        }
    }

    fun launchPickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
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
    
    // 拍照
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            if (isProcessing) return@rememberLauncherForActivityResult
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val scaledBitmap = scaleBitmapDown(bitmap, 1024)
                originalBitmap = scaledBitmap
                annotatedBitmap = null
                isProcessing = true
                
                viewModel.processHandDetection(scaledBitmap)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load photo")
                isProcessing = false
            }
        }
    }
    
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val isCompact = maxWidth < 720.dp

        val rootModifier = if (isCompact) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }

        Column(
            modifier = rootModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
            text = "手部异物检测 Demo",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // 1. 操作区
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { launchPickImage() }
            ) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.width(6.dp))
                Text("选择图片")
            }
            
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    try {
                        val photoFile = File(context.cacheDir, "hand_test_${System.currentTimeMillis()}.jpg")
                        photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                        takePictureLauncher.launch(photoUri)
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(6.dp))
                Text("拍照")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { launchPickFromGallery() }
        ) {
            Icon(Icons.Default.PhotoLibrary, null)
            Spacer(Modifier.width(6.dp))
            Text("相册选择(兼容)")
        }
        
        Spacer(Modifier.height(12.dp))
        
        // 2. 主图展示区 (Result)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = if (isCompact) 420.dp else 520.dp)
                .background(Color.LightGray, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (originalBitmap != null) {
                Image(
                    bitmap = (annotatedBitmap ?: originalBitmap!!).asImageBitmap(),
                    contentDescription = "Result",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("请上传图片或拍照", color = Color.Gray)
            }
            
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (handInfos.isEmpty()) "未检测到手部" else "检测到 ${handInfos.size} 只手",
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        // 3. 详情画廊区 (Gallery)
        if (handInfos.isNotEmpty() && originalBitmap != null) {
            Text(
                text = "手部详情 (裁剪 & 异物检测)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                itemsIndexed(handInfos) { index, hand ->
                    HandDetailCard(originalBitmap!!, hand, index + 1)
                }
            }
        }
    }
    }
}

@Composable
fun HandDetailCard(originalBitmap: Bitmap, hand: HandInfo, index: Int) {
    // 动态裁剪手部区域（1.5x 放大，模仿 PC demo）
    val handCrop = remember(originalBitmap, hand) {
        cropAndScaleHand(originalBitmap, hand.box, 1.5f)
    }
    
    // 如果有异物，在裁剪图上绘制异物框和关键点
    val annotatedCrop = remember(handCrop, hand) {
        if (handCrop != null && hand.hasForeignObject && hand.keyPoints.size >= 2) {
            drawForeignObjectOnCrop(handCrop, hand)
        } else {
            handCrop
        }
    }
    
    Card(
        modifier = Modifier
            .width(180.dp)
            .border(
                width = 2.dp, 
                color = if (hand.hasForeignObject) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            // 手部裁剪图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.Black)
            ) {
                annotatedCrop?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            // 信息区
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Hand $index",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text("Conf: ${"%.2f".format(hand.score)}")
                Spacer(Modifier.height(4.dp))
                
                if (hand.hasForeignObject) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "异物: ${hand.label}",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "✅ 正常",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ========== 工具函数 ==========

// 缩放图片避免 OOM
fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    var resizedWidth = originalWidth
    var resizedHeight = originalHeight

    if (originalHeight > maxDimension || originalWidth > maxDimension) {
        if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = (originalHeight * maxDimension.toFloat() / originalWidth).toInt()
        } else {
            resizedHeight = maxDimension
            resizedWidth = (originalWidth * maxDimension.toFloat() / originalHeight).toInt()
        }
    } else {
        return bitmap
    }
    return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
}

// 在原图上画框
fun drawDetections(bitmap: Bitmap, hands: List<HandInfo>): Bitmap {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        textSize = 40f
    }

    val foreignPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = android.graphics.Color.RED
    }
    
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    hands.forEachIndexed { i, hand ->
        val color = if (hand.hasForeignObject) android.graphics.Color.RED else android.graphics.Color.GREEN
        paint.color = color
        
        canvas.drawRect(hand.box, paint)
        
        // 画标签背景
        val label = "Hand ${i+1}"
        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val bgRect = RectF(
            hand.box.left, 
            hand.box.top - textBounds.height() - 10, 
            hand.box.left + textBounds.width() + 20, 
            hand.box.top
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(bgRect, paint)
        paint.style = Paint.Style.STROKE
        
        canvas.drawText(label, hand.box.left + 10, hand.box.top - 10, textPaint)

        val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
            hand.foreignObjects
        } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
            val tl = hand.keyPoints[0]
            val br = hand.keyPoints[1]
            listOf(
                ForeignObjectInfo(
                    box = RectF(tl.x, tl.y, br.x, br.y),
                    score = hand.score,
                    label = hand.label,
                )
            )
        } else {
            emptyList()
        }

        if (foreignObjects.isNotEmpty()) {
            val cropScaleFactor = 1.5f
            val cx = ((hand.box.left + hand.box.right) / 2f).toInt()
            val cy = ((hand.box.top + hand.box.bottom) / 2f).toInt()
            val w = (hand.box.width()).toInt()
            val h = (hand.box.height()).toInt()
            val newW = (w * cropScaleFactor).toInt()
            val newH = (h * cropScaleFactor).toInt()
            val cropLeft = max(0, cx - newW / 2)
            val cropTop = max(0, cy - newH / 2)

            foreignObjects.forEach { fo ->
                val b = fo.box
                val left = cropLeft + min(b.left, b.right)
                val top = cropTop + min(b.top, b.bottom)
                val right = cropLeft + max(b.left, b.right)
                val bottom = cropTop + max(b.top, b.bottom)
                canvas.drawRect(left, top, right, bottom, foreignPaint)
            }
        }
    }
    
    return mutableBitmap
}

// 裁剪并放大手部区域（模仿 PC demo 的 1.5x 放大逻辑）
fun cropAndScaleHand(bitmap: Bitmap, box: RectF, scaleFactor: Float = 1.5f): Bitmap? {
    return try {
        val cx = (box.left + box.right) / 2
        val cy = (box.top + box.bottom) / 2
        val w = box.width()
        val h = box.height()
        
        val newW = (w * scaleFactor).toInt()
        val newH = (h * scaleFactor).toInt()
        
        val x = max(0, (cx - newW / 2).toInt())
        val y = max(0, (cy - newH / 2).toInt())
        val x2 = min(bitmap.width, (cx + newW / 2).toInt())
        val y2 = min(bitmap.height, (cy + newH / 2).toInt())
        
        val actualW = x2 - x
        val actualH = y2 - y
        
        if (actualW <= 0 || actualH <= 0) return null
        
        Bitmap.createBitmap(bitmap, x, y, actualW, actualH)
    } catch (e: Exception) {
        Timber.e(e, "Failed to crop hand")
        null
    }
}

// 在裁剪图上绘制异物框和关键点
fun drawForeignObjectOnCrop(cropBitmap: Bitmap, hand: HandInfo): Bitmap {
    val mutableBitmap = cropBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
        hand.foreignObjects
    } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
        val tl = hand.keyPoints[0]
        val br = hand.keyPoints[1]
        listOf(
            ForeignObjectInfo(
                box = RectF(tl.x, tl.y, br.x, br.y),
                score = hand.score,
                label = hand.label,
            )
        )
    } else {
        emptyList()
    }

    val boxPaint = Paint().apply {
        color = android.graphics.Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val textPaint = Paint().apply {
        color = android.graphics.Color.RED
        textSize = 30f
        style = Paint.Style.FILL
        setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
    }

    foreignObjects.forEach { fo ->
        val b = fo.box
        val rect = RectF(min(b.left, b.right), min(b.top, b.bottom), max(b.left, b.right), max(b.top, b.bottom))
        canvas.drawRect(rect, boxPaint)
        canvas.drawText("${fo.label} ${"%.2f".format(fo.score)}", rect.left, (rect.top - 10).coerceAtLeast(30f), textPaint)
    }
    
    // 画关键点：新版本 keyPoints 已经是骨架点；旧版本跳过前两个 box 点
    val keypointPaint = Paint().apply {
        color = android.graphics.Color.YELLOW
        style = Paint.Style.FILL
    }
    val startIndex = if (hand.foreignObjects.isEmpty() && hand.hasForeignObject && hand.keyPoints.size > 2) 2 else 0
    for (i in startIndex until hand.keyPoints.size) {
        val kp = hand.keyPoints[i]
        canvas.drawCircle(kp.x, kp.y, 4f, keypointPaint)
    }
    
    return mutableBitmap
}
