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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.viewmodel.FaceTestViewModel
import com.smartcheck.sdk.face.FaceInfo
import com.smartcheck.sdk.face.FaceSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@Composable
fun FaceTestScreen(
    onNavigateHome: (() -> Unit)? = null,
    viewModel: FaceTestViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var faces by remember { mutableStateOf<List<FaceInfo>>(emptyList()) }

    var employeeId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var registerResult by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }

    var lastFeature by remember { mutableStateOf<FloatArray?>(null) }
    var registeredFeature by remember { mutableStateOf<FloatArray?>(null) }
    var similarity by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            FaceSdk.init(context)
        }
    }

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
            } ?: return@rememberLauncherForActivityResult

            val scaledBitmap = scaleBitmapDown(bitmap, 1024)
            originalBitmap = scaledBitmap
            annotatedBitmap = null
            registerResult = null
            isProcessing = true

            val faceResult = FaceSdk.detect(scaledBitmap)
            faces = faceResult
            annotatedBitmap = if (faceResult.isNotEmpty()) drawFaceDetections(scaledBitmap, faceResult) else null

            lastFeature = FaceSdk.extractFeature(scaledBitmap)
            similarity = if (registeredFeature != null && lastFeature != null) {
                FaceSdk.calculateSimilarity(registeredFeature!!, lastFeature!!)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load image")
        } finally {
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
            } ?: return@rememberLauncherForActivityResult

            val scaledBitmap = scaleBitmapDown(bitmap, 1024)
            originalBitmap = scaledBitmap
            annotatedBitmap = null
            registerResult = null
            isProcessing = true

            val faceResult = FaceSdk.detect(scaledBitmap)
            faces = faceResult
            annotatedBitmap = if (faceResult.isNotEmpty()) drawFaceDetections(scaledBitmap, faceResult) else null

            lastFeature = FaceSdk.extractFeature(scaledBitmap)
            similarity = if (registeredFeature != null && lastFeature != null) {
                FaceSdk.calculateSimilarity(registeredFeature!!, lastFeature!!)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load image (GetContent)")
        } finally {
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
                registerResult = null
                isProcessing = true

                val result = FaceSdk.detect(scaledBitmap)
                faces = result
                annotatedBitmap = if (result.isNotEmpty()) drawFaceDetections(scaledBitmap, result) else null

                lastFeature = FaceSdk.extractFeature(scaledBitmap)
                similarity = if (registeredFeature != null && lastFeature != null) {
                    FaceSdk.calculateSimilarity(registeredFeature!!, lastFeature!!)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load photo")
            } finally {
                isProcessing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "人脸识别 Demo (SeetaFace2)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (onNavigateHome != null) {
            OutlinedButton(onClick = onNavigateHome) {
                Text("进入主页识别")
            }

            Spacer(Modifier.height(12.dp))
        }

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
                        val photoFile = File(context.cacheDir, "face_test_${System.currentTimeMillis()}.jpg")
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                enabled = lastFeature != null,
                onClick = {
                    registeredFeature = lastFeature
                    similarity = null
                }
            ) {
                Text("设为注册")
            }

            OutlinedButton(
                enabled = registeredFeature != null,
                onClick = {
                    registeredFeature = null
                    similarity = null
                }
            ) {
                Text("清除注册")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = employeeId,
            onValueChange = { employeeId = it },
            label = { Text("工号") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("姓名") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = department,
            onValueChange = { department = it },
            label = { Text("部门") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !isRegistering && originalBitmap != null && faces.isNotEmpty() && employeeId.trim().isNotEmpty() && name.trim().isNotEmpty(),
            onClick = {
                val frame = originalBitmap
                if (frame == null) return@Button

                scope.launch {
                    isRegistering = true
                    registerResult = null
                    try {
                        val userId = viewModel.registerUserWithFrame(
                            name = name,
                            employeeId = employeeId,
                            department = department,
                            frame = frame
                        )
                        registerResult = if (userId != null) {
                            "注册成功: userId=$userId"
                        } else {
                            "注册失败"
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Register user failed")
                        registerResult = "注册异常"
                    } finally {
                        isRegistering = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRegistering) "注册中..." else "写入人脸到数据库")
        }

        if (registerResult != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = registerResult!!,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = if (faces.isEmpty()) "未检测到人脸" else "检测到 ${faces.size} 张人脸",
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (registeredFeature == null) "未注册人脸" else "已注册人脸特征 (len=${registeredFeature!!.size})"
        )

        if (similarity != null) {
            Text(
                text = "相似度: ${String.format("%.3f", similarity)}",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun drawFaceDetections(bitmap: Bitmap, faces: List<FaceInfo>): Bitmap {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = android.graphics.Color.GREEN
    }

    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    faces.forEachIndexed { index, face ->
        canvas.drawRect(face.box, boxPaint)

        val label = "Face ${index + 1} (${String.format("%.2f", face.score)})"
        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val bgRect = RectF(
            face.box.left,
            face.box.top - textBounds.height() - 10,
            face.box.left + textBounds.width() + 20,
            face.box.top
        )

        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(160, 0, 0, 0)
        }
        canvas.drawRect(bgRect, bgPaint)
        canvas.drawText(label, face.box.left + 10, face.box.top - 10, textPaint)
    }

    return mutableBitmap
}
