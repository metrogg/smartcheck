package com.smartcheck.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.smartcheck.app.ui.components.DualCameraPreview
import com.smartcheck.app.ui.components.FaceOverlay
import com.smartcheck.app.ui.components.HandOverlay
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.CheckState
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import android.widget.Toast

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateAdmin: (() -> Unit)? = null,
    onNavigateBackToDashboard: (() -> Unit)? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val handInfos by viewModel.handDetectionState.collectAsState()
    val faceBoxes by viewModel.faceDetectionBoxes.collectAsState()
    val context = LocalContext.current

    var lastFrameWidth by remember { mutableIntStateOf(0) }
    var lastFrameHeight by remember { mutableIntStateOf(0) }
    var cameraLensFacing by remember { mutableIntStateOf(-1) }
    var lastFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var faceSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var handFrontShot by remember { mutableStateOf<Bitmap?>(null) }
    var handBackShot by remember { mutableStateOf<Bitmap?>(null) }
    var showSuccessOverlay by remember { mutableStateOf(false) }

    var showSymptomDialog by remember { mutableStateOf(false) }
    var symptomConfirmed by remember { mutableStateOf(false) }
    var autoNavigateState by remember { mutableStateOf<CheckState?>(null) }
    var cameraInitState by remember { mutableStateOf(com.smartcheck.app.ui.components.CameraInitState.Initializing) }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CheckState.SYMPTOM_CHECKING) {
            showSymptomDialog = true
            symptomConfirmed = false
        } else {
            showSymptomDialog = false
        }

        when (uiState.state) {
            CheckState.HAND_BACK_CHECKING -> {
                if (handFrontShot == null) {
                    handFrontShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                }
            }
            CheckState.SYMPTOM_CHECKING, CheckState.HAND_FAIL -> {
                if (handBackShot == null) {
                    handBackShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                }
            }
            CheckState.IDLE -> {
                handFrontShot = null
                handBackShot = null
                faceSnapshot = null
            }
            else -> Unit
        }
    }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CheckState.FACE_PASS) {
            lastFrameBitmap?.let { faceSnapshot = createPreviewBitmap(it, maxWidth = 220) }
        }
    }

    LaunchedEffect(uiState.state) {
        val terminalStates = setOf(
            CheckState.ALL_PASS,
            CheckState.HAND_FAIL,
            CheckState.SYMPTOM_FAIL,
            CheckState.TEMP_FAIL
        )
        if (onNavigateBackToDashboard == null || uiState.state !in terminalStates) return@LaunchedEffect
        if (!uiState.isRecordFinalized) return@LaunchedEffect
        if (autoNavigateState == uiState.state) return@LaunchedEffect
        autoNavigateState = uiState.state

        if (uiState.state == CheckState.ALL_PASS) {
            showSuccessOverlay = true
            delay(1500)
            showSuccessOverlay = false
            onNavigateBackToDashboard()
        } else {
            delay(1200)
            onNavigateBackToDashboard()
        }
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        val available = context.filesDir.usableSpace
        val threshold = 500L * 1024L * 1024L
        if (available < threshold) {
            FileUtil.clearOldRecords(context, 30)
            Toast.makeText(context, "存储空间不足，已清理30天前记录", Toast.LENGTH_SHORT).show()
        }
    }

    val isHandStage = uiState.state == CheckState.HAND_PALM_CHECKING ||
        uiState.state == CheckState.HAND_BACK_CHECKING
    val preferredCameraId = if (isHandStage) "102" else "100"
    val isSwitchingCamera = uiState.state == CheckState.FACE_PASS || uiState.state == CheckState.TEMP_MEASURING
    val isMirrored = cameraLensFacing == CameraSelector.LENS_FACING_FRONT ||
        (!isHandStage && cameraLensFacing == CameraSelector.LENS_FACING_EXTERNAL)

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .background(Color.Black)
        ) {
            if (cameraPermissionState.status.isGranted) {
                DualCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    cameraType = if (isHandStage) com.smartcheck.app.ui.components.CameraType.HAND else com.smartcheck.app.ui.components.CameraType.FACE,
                    preferredCameraId = preferredCameraId,
                    onFrameAnalyzed = { bitmap ->
                        lastFrameWidth = bitmap.width
                        lastFrameHeight = bitmap.height
                        lastFrameBitmap = bitmap
                        viewModel.processFrame(bitmap)
                    },
                    onCameraInfo = { _, lensFacing ->
                        cameraLensFacing = lensFacing
                    },
                    onCameraState = { state ->
                        cameraInitState = state
                    }
                )

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready && !isHandStage && faceBoxes.isNotEmpty()) {
                    FaceOverlay(
                        faceBoxes = faceBoxes,
                        frameWidth = lastFrameWidth,
                        frameHeight = lastFrameHeight,
                        mirrorX = isMirrored,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready && isHandStage) {
                    HandOverlay(
                        handInfos = handInfos,
                        frameWidth = lastFrameWidth,
                        frameHeight = lastFrameHeight,
                        contentScale = ContentScale.Fit,
                        mirrorX = isMirrored,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ScannerFrameOverlay()

                if (cameraInitState != com.smartcheck.app.ui.components.CameraInitState.Ready) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "正在寻找相机...",
                            color = Color.White,
                            fontSize = Dimens.TextSizeNormal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isSwitchingCamera && cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "正在切换手部摄像头...",
                            color = Color.White,
                            fontSize = Dimens.TextSizeNormal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "需要相机权限以进行人脸识别",
                            fontSize = Dimens.TextSizeNormal
                        )
                        Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                            Text(text = "授予权限", fontSize = Dimens.TextSizeNormal)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .background(Color.White)
                .padding(Dimens.PaddingNormal),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)) {
                PersonInfoCard(
                    userName = uiState.currentUserName,
                    temperature = uiState.currentTemp,
                    healthCertDays = uiState.healthCertDaysRemaining,
                    snapshot = faceSnapshot,
                    facePath = uiState.faceImagePath
                )

                HandCheckPanel(
                    state = uiState.state,
                    handInfos = handInfos,
                    frontShot = handFrontShot,
                    backShot = handBackShot,
                    frontPath = uiState.handPalmPath,
                    backPath = uiState.handBackPath
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)) {
                Button(
                    onClick = { viewModel.finalizeCheckRecord() },
                    enabled = !uiState.isSubmitting && uiState.state != CheckState.SYMPTOM_CHECKING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                ) {
                    if (uiState.isSubmitting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    }
                    Text(text = "提交并上岗", fontSize = Dimens.TextSizeNormal, color = Color.White)
                }
                if (uiState.state == CheckState.ALL_PASS) {
                    Text(
                        text = "晨检完成，将自动返回首页",
                        fontSize = Dimens.TextSizeSmall,
                        color = BrandGreen
                    )
                }
                if (uiState.state == CheckState.HAND_FAIL || uiState.state == CheckState.TEMP_FAIL) {
                    Text(
                        text = "晨检未通过，将自动返回首页",
                        fontSize = Dimens.TextSizeSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showSymptomDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = "健康询问", color = BrandGreen, fontSize = Dimens.TextSizeLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = symptomConfirmed,
                            onCheckedChange = { symptomConfirmed = it }
                        )
                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                        Text(
                            text = "我承诺今日无腹泻、咽痛等异常症状",
                            fontSize = Dimens.TextSizeNormal
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSymptomDialog = false
                        if (symptomConfirmed) {
                            viewModel.submitSymptoms(emptyList())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                ) {
                    Text(text = "确认", fontSize = Dimens.TextSizeNormal, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSymptomDialog = false
                        viewModel.submitSymptoms(listOf("自述异常"))
                    }
                ) {
                    Text(text = "有异常", fontSize = Dimens.TextSizeNormal, color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showSuccessOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandGreen.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "✔", color = Color.White, fontSize = Dimens.TextSizeTitle)
                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                Text(
                    text = "晨检成功，祝您工作愉快",
                    color = Color.White,
                    fontSize = Dimens.TextSizeLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PersonInfoCard(
    userName: String,
    temperature: Float,
    healthCertDays: Int?,
    snapshot: Bitmap?,
    facePath: String?
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(Dimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingNormal),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFE8F3EF), RoundedCornerShape(Dimens.CornerRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    val file = FileUtil.getRecordImageFile(context, facePath)?.takeIf { it.exists() }
                    val model = file ?: snapshot
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BrandGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    Text(
                        text = userName.ifBlank { "--" },
                        fontSize = Dimens.TextSizeLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatTemp(temperature),
                        fontSize = Dimens.TextSizeNormal,
                        color = if (temperature >= 37.3f) MaterialTheme.colorScheme.error else BrandGreen
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(label = "体温", value = formatTemp(temperature), highlight = temperature >= 37.3f)
            InfoRow(label = "健康证", value = formatHealthCert(healthCertDays), highlight = (healthCertDays ?: 0) < 0)
        }
    }
}

@Composable
private fun HandCheckPanel(
    state: CheckState,
    handInfos: List<com.smartcheck.sdk.HandInfo>,
    frontShot: Bitmap?,
    backShot: Bitmap?,
    frontPath: String?,
    backPath: String?
) {
    val hasIssue = handInfos.any { it.hasForeignObject }
    val summary = handInfos.filter { it.hasForeignObject }.map { it.label }.distinct().joinToString("，")

    Card(
        shape = RoundedCornerShape(Dimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingNormal),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
        ) {
            Text(text = "手部检测", fontSize = Dimens.TextSizeNormal, fontWeight = FontWeight.SemiBold)
            HandPhotoRow(
                frontShot = frontShot,
                backShot = backShot,
                frontPath = frontPath,
                backPath = backPath
            )
            HandSlot(title = "手心", active = state == CheckState.HAND_PALM_CHECKING, hasIssue = hasIssue, summary = summary)
            HandSlot(title = "手背", active = state == CheckState.HAND_BACK_CHECKING, hasIssue = hasIssue, summary = summary)
        }
    }
}

@Composable
private fun HandPhotoRow(
    frontShot: Bitmap?,
    backShot: Bitmap?,
    frontPath: String?,
    backPath: String?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
        HandPhotoSlot(
            title = "手心",
            bitmap = frontShot,
            path = frontPath,
            modifier = Modifier.weight(1f)
        )
        HandPhotoSlot(
            title = "手背",
            bitmap = backShot,
            path = backPath,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HandPhotoSlot(
    title: String,
    bitmap: Bitmap?,
    path: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5F2))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val file = FileUtil.getRecordImageFile(context, path)?.takeIf { it.exists() }
            val model = file ?: bitmap
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(text = title, fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
            }
        }
    }
}

@Composable
private fun HandSlot(
    title: String,
    active: Boolean,
    hasIssue: Boolean,
    summary: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(Dimens.CornerRadius))
            .padding(Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PanTool,
                contentDescription = null,
                tint = if (active) BrandGreen else Color(0xFF9AA4B2),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
            Text(text = title, fontSize = Dimens.TextSizeNormal)
        }
        if (hasIssue) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                Text(
                    text = if (summary.isBlank()) "检测异常" else summary,
                    fontSize = Dimens.TextSizeSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
        Text(
            text = value,
            fontSize = Dimens.TextSizeSmall,
            color = if (highlight) MaterialTheme.colorScheme.error else Color(0xFF111827)
        )
    }
}

@Composable
private fun ScannerFrameOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        val cornerColor = BrandGreen
        val cornerLength = 40.dp
        val cornerStroke = 4.dp

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .width(cornerLength)
                .height(cornerLength)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerStroke)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .width(cornerStroke)
                    .fillMaxHeight()
                    .background(cornerColor)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .width(cornerLength)
                .height(cornerLength)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerStroke)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .width(cornerStroke)
                    .fillMaxHeight()
                    .background(cornerColor)
                    .align(Alignment.TopEnd)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .width(cornerLength)
                .height(cornerLength)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerStroke)
                    .background(cornerColor)
                    .align(Alignment.BottomStart)
            )
            Box(
                modifier = Modifier
                    .width(cornerStroke)
                    .fillMaxHeight()
                    .background(cornerColor)
                    .align(Alignment.BottomStart)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .width(cornerLength)
                .height(cornerLength)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerStroke)
                    .background(cornerColor)
                    .align(Alignment.BottomEnd)
            )
            Box(
                modifier = Modifier
                    .width(cornerStroke)
                    .fillMaxHeight()
                    .background(cornerColor)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

private fun formatTemp(temp: Float): String {
    return if (temp == 0f) "读取中..." else "%.1f°C".format(temp)
}

private fun formatHealthCert(days: Int?): String {
    return when {
        days == null -> "--"
        days < 0 -> "已过期 ${kotlin.math.abs(days)} 天"
        else -> "剩余 ${days} 天"
    }
}

private fun createPreviewBitmap(source: Bitmap, maxWidth: Int = 320): Bitmap {
    if (source.width <= maxWidth) return source
    val ratio = source.height.toFloat() / source.width.toFloat()
    val targetHeight = (maxWidth * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, maxWidth, targetHeight, true)
}
