package com.smartcheck.app.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.camera.core.CameraSelector
import com.smartcheck.app.ui.components.CameraType
import com.smartcheck.app.ui.components.DualCameraPreview
import com.smartcheck.app.ui.components.FaceOverlay
import com.smartcheck.app.ui.components.HandOverlay
import com.smartcheck.app.ui.components.ResultCard
import com.smartcheck.app.viewmodel.CheckState
import com.smartcheck.app.viewmodel.MainViewModel
import timber.log.Timber

/**
 * 晨检主界面
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateAdmin: (() -> Unit)? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val handInfos by viewModel.handDetectionState.collectAsState()
    val faceBoxes by viewModel.faceDetectionBoxes.collectAsState()

    var lastFrameWidth by remember { mutableIntStateOf(0) }
    var lastFrameHeight by remember { mutableIntStateOf(0) }
    var cameraLensFacing by remember { mutableIntStateOf(-1) }
    var isCameraRunning by remember { mutableStateOf(false) }

    val currentCameraType = when (uiState.state) {
        CheckState.HAND_PALM_CHECKING, CheckState.HAND_BACK_CHECKING -> CameraType.HAND
        else -> CameraType.FACE
    }
    val preferredCameraId = if (currentCameraType == CameraType.HAND) "102" else "100"

    val symptomOptions = remember {
        listOf(
            "咳嗽",
            "咽痛",
            "流鼻涕",
            "乏力",
            "腹泻",
            "其他不适"
        )
    }

    var showSymptomDialog by remember { mutableStateOf(false) }
    var selectedSymptoms by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CheckState.SYMPTOM_CHECKING) {
            selectedSymptoms = emptySet()
            showSymptomDialog = true
        } else {
            showSymptomDialog = false
        }
    }
    
    // 请求相机权限
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 摄像头预览层
        if (cameraPermissionState.status.isGranted) {
            DualCameraPreview(
                modifier = Modifier.fillMaxSize(),
                cameraType = currentCameraType,
                preferredCameraId = preferredCameraId,
                onFrameAnalyzed = { bitmap ->
                    lastFrameWidth = bitmap.width
                    lastFrameHeight = bitmap.height
                    viewModel.processFrame(bitmap)
                },
                onCameraInfo = { cameraId, lensFacingValue ->
                    cameraLensFacing = lensFacingValue
                    isCameraRunning = true
                }
            )

            if (uiState.state == CheckState.IDLE && faceBoxes.isNotEmpty()) {
                FaceOverlay(
                    faceBoxes = faceBoxes,
                    frameWidth = lastFrameWidth,
                    frameHeight = lastFrameHeight,
                    mirrorX = cameraLensFacing == CameraSelector.LENS_FACING_FRONT,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.state == CheckState.HAND_PALM_CHECKING || uiState.state == CheckState.HAND_BACK_CHECKING) {
                HandOverlay(
                    handInfos = handInfos,
                    frameWidth = lastFrameWidth,
                    frameHeight = lastFrameHeight,
                    contentScale = ContentScale.FillBounds,
                    mirrorX = cameraLensFacing == CameraSelector.LENS_FACING_FRONT,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // 权限未授予提示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("需要相机权限以进行人脸识别")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("授予权限")
                    }
                }
            }
        }

        if (onNavigateAdmin != null) {
            TextButton(
                onClick = onNavigateAdmin,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp,
                        bottom = 8.dp
                    )
            ) {
                Text("后台")
            }
        }
        
        // 结果卡片层（覆盖在摄像头上方）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp
                ),
            verticalArrangement = Arrangement.Bottom
        ) {
            ResultCard(
                userName = uiState.currentUserName,
                temperature = uiState.currentTemp,
                state = uiState.state,
                message = uiState.message,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
            )
        }
    }

    if (showSymptomDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("健康询问") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("今天是否有以下不适？")
                    symptomOptions.forEach { option ->
                        val checked = selectedSymptoms.contains(option)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedSymptoms = if (isChecked) {
                                        selectedSymptoms + option
                                    } else {
                                        selectedSymptoms - option
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSymptomDialog = false
                        viewModel.submitSymptoms(emptyList())
                    }
                ) {
                    Text("无")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSymptomDialog = false
                        viewModel.submitSymptoms(selectedSymptoms.toList())
                    }
                ) {
                    Text("提交")
                }
            }
        )
    }
}
