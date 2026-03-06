package com.smartcheck.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import timber.log.Timber
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.smartcheck.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import android.widget.Toast
import com.smartcheck.sdk.ForeignObjectInfo
import com.smartcheck.sdk.HandInfo
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalPermissionsApi::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    onNavigateAdmin: (() -> Unit)? = null,
    onNavigateBackToDashboard: (() -> Unit)? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val adminName by settingsViewModel.adminName.collectAsState()
    val adminAvatar by settingsViewModel.adminAvatar.collectAsState()
    val handInfos by viewModel.handDetectionState.collectAsState()
    val faceBoxes by viewModel.faceDetectionBoxes.collectAsState()
    val context = LocalContext.current

    var lastFrameWidth by remember { mutableIntStateOf(0) }
    var lastFrameHeight by remember { mutableIntStateOf(0) }
    var previewWidth by remember { mutableIntStateOf(0) }
    var previewHeight by remember { mutableIntStateOf(0) }
    var cameraLensFacing by remember { mutableIntStateOf(-1) }
    var lastFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var faceSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var handFrontShot by remember { mutableStateOf<Bitmap?>(null) }
    var handBackShot by remember { mutableStateOf<Bitmap?>(null) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var showTransitionMask by remember { mutableStateOf(false) }

    var showSymptomDialog by remember { mutableStateOf(false) }
    var symptomConfirmed by remember { mutableStateOf(false) }
    var autoNavigateState by remember { mutableStateOf<CheckState?>(null) }
    var cameraInitState by remember { mutableStateOf(com.smartcheck.app.ui.components.CameraInitState.Initializing) }
    var showHandFailDialog by remember { mutableStateOf(false) }
    var showAllPassDialog by remember { mutableStateOf(false) }
    var lastHandIssueShownAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CheckState.SYMPTOM_CHECKING) {
            showSymptomDialog = true
            symptomConfirmed = false
        } else {
            showSymptomDialog = false
        }

        if (uiState.state == CheckState.ALL_PASS && !uiState.handHasIssue) {
            showAllPassDialog = true
        } else {
            showAllPassDialog = false
        }

        if (uiState.state == CheckState.HAND_FAIL && uiState.handHasIssue) {
            val now = System.currentTimeMillis()
            if (now - lastHandIssueShownAt.toLong() > 2500L) {
                showHandFailDialog = true
                lastHandIssueShownAt = now
            }
        }

        when (uiState.state) {
            CheckState.HAND_BACK_CHECKING -> {
                if (handFrontShot == null) {
                    handFrontShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                }
            }
            CheckState.SYMPTOM_CHECKING, CheckState.HAND_FAIL -> {
                if (uiState.handBackInfos.isNotEmpty()) {
                    if (handBackShot == null) {
                        handBackShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                    }
                } else if (uiState.handPalmInfos.isNotEmpty()) {
                    if (handFrontShot == null) {
                        handFrontShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                    }
                } else if (handBackShot == null) {
                    handBackShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                }
            }
            CheckState.IDLE -> {
                handFrontShot.safeRecycle()
                handBackShot.safeRecycle()
                faceSnapshot.safeRecycle()
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

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            faceSnapshot.safeRecycle()
            handFrontShot.safeRecycle()
            handBackShot.safeRecycle()
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

    LaunchedEffect(isHandStage) {
        if (isHandStage) {
            showTransitionMask = true
            delay(1500)
            showTransitionMask = false
        } else {
            showTransitionMask = false
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(0.65f).fillMaxHeight()) {
            if (cameraPermissionState.status.isGranted) {
                DualCameraPreview(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            previewWidth = it.width
                            previewHeight = it.height
                        },
                    cameraType = if (isHandStage) com.smartcheck.app.ui.components.CameraType.HAND else com.smartcheck.app.ui.components.CameraType.FACE,
                    preferredCameraId = preferredCameraId,
                    onFrameAnalyzed = { bitmap ->
                        lastFrameWidth = bitmap.width
                        lastFrameHeight = bitmap.height
                        lastFrameBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        viewModel.processFrame(bitmap)
                    },
                    onCameraInfo = { _, lensFacing ->
                        cameraLensFacing = lensFacing
                    },
                    onCameraState = { state ->
                        cameraInitState = state
                    }
                )

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready &&
                    !isHandStage &&
                    faceBoxes.isNotEmpty()
                ) {
                    FaceOverlay(
                        faceBoxes = faceBoxes,
                        frameWidth = lastFrameWidth,
                        frameHeight = lastFrameHeight,
                        viewWidth = previewWidth,
                        viewHeight = previewHeight,
                        mirrorX = isMirrored,
                    )
                    Timber.d("[HomeScreen] 画框: faceBoxes=$faceBoxes, frame=(${lastFrameWidth}x${lastFrameHeight}), view=(${previewWidth}x${previewHeight}), mirror=$isMirrored")
                }

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready && isHandStage) {
                    HandOverlay(
                        handInfos = handInfos,
                        frameWidth = lastFrameWidth,
                        frameHeight = lastFrameHeight,
                        viewWidth = previewWidth,
                        viewHeight = previewHeight,
                        contentScale = ContentScale.Fit,
                        mirrorX = isMirrored,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                val statusText = when {
                    cameraInitState != com.smartcheck.app.ui.components.CameraInitState.Ready -> if (isHandStage) "正在初始化手部相机..." else "正在初始化人脸相机..."
                    showTransitionMask -> "正在切换相机..."
                    else -> uiState.message.ifBlank { "请正视摄像头" }
                }
                StatusBadge(
                    text = statusText,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Dimens.PaddingNormal)
                )

                ScannerFrameOverlay()

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "相机初始化失败",
                                color = Color.White,
                                fontSize = Dimens.TextSizeNormal,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            Text(
                                text = "请检查设备或重启",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = Dimens.TextSizeSmall
                            )
                        }
                    }
                } else if (cameraInitState != com.smartcheck.app.ui.components.CameraInitState.Ready) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "正在初始化相机...",
                                color = Color.White,
                                fontSize = Dimens.TextSizeNormal,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            Text(
                                text = if (isHandStage) "即将开始手部识别" else "即将开始人脸识别",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = Dimens.TextSizeSmall
                            )
                        }
                    }
                }

                if (showTransitionMask && cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready) {
                    TransitionMask()
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
                .padding(Dimens.PaddingNormal)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val context = LocalContext.current
                val faceFile = FileUtil.getRecordImageFile(context, uiState.faceImagePath)?.takeIf { it.exists() }
                val faceModel = faceFile ?: faceSnapshot
                if (faceModel != null) {
                    AsyncImage(
                        model = faceModel,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true)
                            ) { viewModel.retakeFace() },
                        contentScale = ContentScale.Crop,
                        contentDescription = "头像"
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE5E7EB))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true)
                            ) { viewModel.retakeFace() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "姓名: ${uiState.currentUserName.ifBlank { "--" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    val isHigh = uiState.currentTemp >= 37.3f && uiState.currentTemp > 0f
                    Text(
                        text = "体温: ${formatTemp(uiState.currentTemp)}",
                        color = if (isHigh) Color.Red else BrandGreen
                    )
                    val days = uiState.healthCertDaysRemaining?.toString() ?: "--"
                    Text(text = "健康证有效: ${days}天")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "手部检测", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                val handStageLabel = when (uiState.state) {
                    CheckState.HAND_PALM_CHECKING -> "当前：手心"
                    CheckState.HAND_BACK_CHECKING -> "当前：手背"
                    CheckState.AUTO_SUBMITTING, CheckState.ALL_PASS -> "已完成"
                    else -> "待开始"
                }
                Text(text = handStageLabel, fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                val context = LocalContext.current
                val handThumbAspectRatio = 16f / 9f

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(end = 8.dp)
                            .aspectRatio(handThumbAspectRatio)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true)
                            ) { viewModel.retakeHandPalm() }
                    ) {
                        val palmFile = FileUtil.getRecordImageFile(context, uiState.handPalmPath)?.takeIf { it.exists() }
                        val palmModel = palmFile ?: handFrontShot
                        if (palmModel != null) {
                            AsyncImage(
                                model = palmModel,
                                contentDescription = "手心",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PlaceholderHandTile(
                                title = "手心",
                                hint = if (uiState.state == CheckState.HAND_PALM_CHECKING) "请对准手心" else "等待拍摄",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (palmModel != null && uiState.handPalmInfos.isNotEmpty()) {
                            val frameW = uiState.handPalmFrameWidth ?: lastFrameWidth
                            val frameH = uiState.handPalmFrameHeight ?: lastFrameHeight
                            HandResultOverlay(
                                handInfos = uiState.handPalmInfos,
                                frameWidth = frameW,
                                frameHeight = frameH,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                        val hasIssue = uiState.handPalmInfos.any { it.hasForeignObject }
                        val issueSummary = uiState.handPalmInfos.flatMap { info ->
                            if (info.foreignObjects.isNotEmpty()) {
                                info.foreignObjects.map { it.label }
                            } else if (info.hasForeignObject) {
                                listOf(info.label)
                            } else {
                                emptyList()
                            }
                        }.distinct().joinToString("，")

                        if (hasIssue) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = issueSummary.ifBlank { "异常" },
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = Dimens.TextSizeSmall
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BrandGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(end = 8.dp)
                            .aspectRatio(handThumbAspectRatio)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true)
                            ) { viewModel.retakeHandBack() }
                    ) {
                        val backFile = FileUtil.getRecordImageFile(context, uiState.handBackPath)?.takeIf { it.exists() }
                        val backModel = backFile ?: handBackShot
                        if (backModel != null) {
                            AsyncImage(
                                model = backModel,
                                contentDescription = "手背",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PlaceholderHandTile(
                                title = "手背",
                                hint = if (uiState.state == CheckState.HAND_BACK_CHECKING) "请对准手背" else "等待拍摄",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (backModel != null && uiState.handBackInfos.isNotEmpty()) {
                            val frameW = uiState.handBackFrameWidth ?: lastFrameWidth
                            val frameH = uiState.handBackFrameHeight ?: lastFrameHeight
                            HandResultOverlay(
                                handInfos = uiState.handBackInfos,
                                frameWidth = frameW,
                                frameHeight = frameH,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                        val hasIssue = uiState.handBackInfos.any { it.hasForeignObject }
                        val issueSummary = uiState.handBackInfos.flatMap { info ->
                            if (info.foreignObjects.isNotEmpty()) {
                                info.foreignObjects.map { it.label }
                            } else if (info.hasForeignObject) {
                                listOf(info.label)
                            } else {
                                emptyList()
                            }
                        }.distinct().joinToString("，")

                        if (hasIssue) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = issueSummary.ifBlank { "异常" },
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = Dimens.TextSizeSmall
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BrandGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.finalizeCheckRecord() },
                enabled = !uiState.isSubmitting && uiState.state != CheckState.SYMPTOM_CHECKING && !uiState.handHasIssue,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight),
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
                Text("提交并上岗")
            }
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = showSymptomDialog,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) with androidx.compose.animation.fadeOut(tween(200)) },
        label = "SymptomDialog"
    ) { visible ->
        if (visible) {
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
    }

    androidx.compose.animation.AnimatedContent(
        targetState = showSuccessOverlay,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(220)) with androidx.compose.animation.fadeOut(tween(220)) },
        label = "SuccessOverlay"
    ) { visible ->
        if (visible) {
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

    androidx.compose.animation.AnimatedContent(
        targetState = showHandFailDialog,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) with androidx.compose.animation.fadeOut(tween(200)) },
        label = "HandFailDialog"
    ) { visible ->
        if (visible) {
            AlertDialog(
                onDismissRequest = { showHandFailDialog = false },
                title = {
                    Text(text = "发现异物/伤口", color = MaterialTheme.colorScheme.error, fontSize = Dimens.TextSizeLarge)
                },
                text = {
                    val issueSummary = uiState.handDetectionResults.joinToString("，")
                    Text(
                        text = if (issueSummary.isBlank()) "手部检测异常，请人工复核" else "手部检测异常：$issueSummary",
                        fontSize = Dimens.TextSizeNormal
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showHandFailDialog = false
                            viewModel.finalizeCheckRecord()
                            viewModel.reset()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(text = "确认", fontSize = Dimens.TextSizeNormal, color = Color.White)
                    }
                }
            )
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = showAllPassDialog,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) with androidx.compose.animation.fadeOut(tween(200)) },
        label = "AllPassDialog"
    ) { visible ->
        if (visible) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = "晨检通过", color = BrandGreen, fontSize = Dimens.TextSizeLarge)
                },
                text = {
                    Text(
                        text = "体温正常，手部检测正常",
                        fontSize = Dimens.TextSizeNormal
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAllPassDialog = false
                            viewModel.finalizeCheckRecord()
                            viewModel.reset()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                    ) {
                        Text(text = "确认", fontSize = Dimens.TextSizeNormal, color = Color.White)
                    }
                }
            )
        }
    }
}

private fun Bitmap?.safeRecycle() {
    try {
        if (this != null && !isRecycled) {
            recycle()
        }
    } catch (_: Exception) {
    }
}


@Composable
private fun TransitionMask() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "mask")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "maskAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            Text(
                text = "正在准备手部检测",
                color = Color.White,
                fontSize = Dimens.TextSizeNormal,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HandGuideOverlay(
    hasDetection: Boolean,
    isCaptured: Boolean,
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        isCaptured -> BrandGreen
        hasDetection -> Color(0xFFFFD54F)
        else -> Color.White
    }
    val guideColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(220), label = "handGuide")
    val guideAlpha by animateFloatAsState(
        targetValue = if (isCaptured) 0.9f else 0.55f,
        animationSpec = tween(180),
        label = "handGuideAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(10.dp.toPx(), 8.dp.toPx())
            )
        )
        val guideWidth = size.width * 0.36f
        val guideHeight = size.height * 0.52f
        val left = (size.width - guideWidth) / 2f
        val top = (size.height - guideHeight) / 2f
        val palmHeight = guideHeight * 0.55f
        val fingerHeight = guideHeight * 0.35f
        val fingerWidth = guideWidth * 0.16f
        val gap = guideWidth * 0.03f

        val color = guideColor.copy(alpha = guideAlpha)

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left + guideWidth * 0.2f, top + fingerHeight),
            size = androidx.compose.ui.geometry.Size(guideWidth * 0.6f, palmHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = stroke
        )

        val fingerTop = top
        val fingerBaseLeft = left + guideWidth * 0.22f
        for (i in 0 until 4) {
            val fx = fingerBaseLeft + i * (fingerWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(fx, fingerTop),
                size = androidx.compose.ui.geometry.Size(fingerWidth, fingerHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = stroke
            )
        }

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left, top + fingerHeight + palmHeight * 0.25f),
            size = androidx.compose.ui.geometry.Size(guideWidth * 0.18f, palmHeight * 0.35f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = stroke
        )

        if (isCaptured) {
            drawRect(color = BrandGreen.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun HandForeignObjectOverlay(
    handInfos: List<HandInfo>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val renderWidth = size.width
        val renderHeight = size.height

        fun mapBox(
            box: android.graphics.RectF,
            cropLeft: Float,
            cropTop: Float,
            scale: Float,
            offsetX: Float,
            offsetY: Float
        ): android.graphics.RectF {
            val left = offsetX + (cropLeft + min(box.left, box.right)) * scale
            val top = offsetY + (cropTop + min(box.top, box.bottom)) * scale
            val right = offsetX + (cropLeft + max(box.left, box.right)) * scale
            val bottom = offsetY + (cropTop + max(box.top, box.bottom)) * scale
            return android.graphics.RectF(left, top, right, bottom)
        }

        handInfos.forEach { hand ->
            val cropScaleFactor = 1.5f
            val box = hand.box
            val cropWidth = box.width() * cropScaleFactor
            val cropHeight = box.height() * cropScaleFactor
            val cropLeft = max(0f, box.centerX() - cropWidth / 2f)
            val cropTop = max(0f, box.centerY() - cropHeight / 2f)

            val scale = min(
                renderWidth / cropWidth.coerceAtLeast(1f),
                renderHeight / cropHeight.coerceAtLeast(1f)
            )
            val offsetX = (renderWidth - cropWidth * scale) / 2f
            val offsetY = (renderHeight - cropHeight * scale) / 2f

            val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
                hand.foreignObjects
            } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                val tl = hand.keyPoints[0]
                val br = hand.keyPoints[1]
                listOf(
                    ForeignObjectInfo(
                        box = android.graphics.RectF(tl.x, tl.y, br.x, br.y),
                        score = hand.score,
                        label = hand.label
                    )
                )
            } else {
                emptyList()
            }

            foreignObjects.forEach { fo ->
                val mapped = mapBox(fo.box, cropLeft, cropTop, scale, offsetX, offsetY)
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(mapped.left, mapped.top),
                    size = androidx.compose.ui.geometry.Size(mapped.width(), mapped.height()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
        }
    }
}

@Composable
private fun HandResultOverlay(
    handInfos: List<HandInfo>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier
) {
    if (frameWidth <= 0 || frameHeight <= 0) return
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val renderWidth = size.width
        val renderHeight = size.height

        val scaleX = renderWidth / frameWidth.toFloat().coerceAtLeast(1f)
        val scaleY = renderHeight / frameHeight.toFloat().coerceAtLeast(1f)
        val scale = min(scaleX, scaleY)
        val offsetX = (renderWidth - frameWidth * scale) / 2f
        val offsetY = (renderHeight - frameHeight * scale) / 2f

        fun mapBox(box: android.graphics.RectF): android.graphics.RectF {
            val left = offsetX + min(box.left, box.right) * scale
            val top = offsetY + min(box.top, box.bottom) * scale
            val right = offsetX + max(box.left, box.right) * scale
            val bottom = offsetY + max(box.top, box.bottom) * scale
            return android.graphics.RectF(left, top, right, bottom)
        }

        handInfos.forEach { hand ->
            val handBox = mapBox(hand.box)
            drawRect(
                color = if (hand.hasForeignObject) Color.Red else BrandGreen,
                topLeft = androidx.compose.ui.geometry.Offset(handBox.left, handBox.top),
                size = androidx.compose.ui.geometry.Size(handBox.width(), handBox.height()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
                hand.foreignObjects
            } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                val tl = hand.keyPoints[0]
                val br = hand.keyPoints[1]
                listOf(
                    ForeignObjectInfo(
                        box = android.graphics.RectF(tl.x, tl.y, br.x, br.y),
                        score = hand.score,
                        label = hand.label
                    )
                )
            } else {
                emptyList()
            }

            foreignObjects.forEach { fo ->
                val mapped = mapBox(fo.box)
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(mapped.left, mapped.top),
                    size = androidx.compose.ui.geometry.Size(mapped.width(), mapped.height()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
        }
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

@Composable
private fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = Dimens.TextSizeSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PlaceholderHandTile(
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = hint, fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
        }
    }
}

private fun formatHealthCert(days: Int?): String {
    return when {
        days == null -> "--"
        days < 0 -> "已过期 ${kotlin.math.abs(days)} 天"
        else -> "剩余 ${days} 天"
    }
}

private fun createPreviewBitmap(source: Bitmap, maxWidth: Int = 320): Bitmap {
    if (source.isRecycled) return source
    if (source.width <= maxWidth) return source
    val ratio = source.height.toFloat() / source.width.toFloat()
    val targetHeight = (maxWidth * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, maxWidth, targetHeight, true)
}
