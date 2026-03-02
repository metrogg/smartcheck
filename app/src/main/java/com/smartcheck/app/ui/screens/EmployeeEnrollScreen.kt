package com.smartcheck.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.ui.components.CameraType
import com.smartcheck.app.ui.components.DualCameraPreview
import com.smartcheck.app.ui.components.CameraPreview
import com.smartcheck.app.viewmodel.EmployeeEnrollViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import java.time.LocalDate
import java.time.ZoneId
import android.app.DatePickerDialog
import java.time.format.DateTimeFormatter
import android.widget.DatePicker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EmployeeEnrollScreen(
    onNavigateBack: () -> Unit,
    viewModel: EmployeeEnrollViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val lastFrameRef = remember { AtomicReference<Bitmap?>(null) }
    val lastHandFrameRef = remember { AtomicReference<Bitmap?>(null) }

    var employeeId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var idCardNumber by remember { mutableStateOf("") }
    var healthCertStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var healthCertEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var healthCertImagePath by remember { mutableStateOf("") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val pickHealthCertLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            healthCertImagePath = uri.toString()
        }
    }

    if (showStartDatePicker) {
        LaunchedEffect(Unit) {
            showStartDatePicker = false
            showDatePicker(context) { date ->
                healthCertStartDate = date
            }
        }
    }

    if (showEndDatePicker) {
        LaunchedEffect(Unit) {
            showEndDatePicker = false
            showDatePicker(context) { date ->
                healthCertEndDate = date
            }
        }
    }

    var isRegistering by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("员工录入") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (cameraPermissionState.status.isGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameAnalyzed = { bitmap ->
                            lastFrameRef.set(bitmap)
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("授予相机权限")
                    }
                }
            }

            if (cameraPermissionState.status.isGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    DualCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        cameraType = CameraType.HAND,
                        preferredCameraId = "102",
                        onFrameAnalyzed = { bitmap ->
                            lastHandFrameRef.set(bitmap)
                        }
                    )
                }
            }

            OutlinedTextField(
                value = employeeId,
                onValueChange = {
                    employeeId = it
                    resultText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("工号") },
                singleLine = true
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    resultText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("姓名") },
                singleLine = true
            )

            OutlinedTextField(
                value = department,
                onValueChange = {
                    department = it
                    resultText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("部门") },
                singleLine = true
            )

            OutlinedTextField(
                value = idCardNumber,
                onValueChange = {
                    idCardNumber = it
                    resultText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("身份证") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = healthCertStartDate?.format(DateTimeFormatter.ISO_DATE) ?: "",
                    onValueChange = { },
                    modifier = Modifier.weight(1f),
                    label = { Text("健康证起始日期") },
                    readOnly = true
                )
                OutlinedTextField(
                    value = healthCertEndDate?.format(DateTimeFormatter.ISO_DATE) ?: "",
                    onValueChange = { },
                    modifier = Modifier.weight(1f),
                    label = { Text("健康证截止日期") },
                    readOnly = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { showStartDatePicker = true }
                ) {
                    Text("选择起始")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { showEndDatePicker = true }
                ) {
                    Text("选择截止")
                }
            }

            OutlinedTextField(
                value = healthCertImagePath,
                onValueChange = { },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("健康证照片") },
                readOnly = true,
                trailingIcon = {
                    Row {
                        IconButton(onClick = { pickHealthCertLauncher.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "选择健康证照片")
                        }
                        IconButton(onClick = {
                            val frame = lastHandFrameRef.get()
                            if (frame != null) {
                                val savedPath = saveHealthCertSnapshot(context.cacheDir, frame)
                                if (savedPath.isNotBlank()) {
                                    healthCertImagePath = savedPath
                                }
                            }
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "拍摄健康证照片")
                        }
                    }
                }
            )

            if (healthCertEndDate != null && healthCertStartDate != null) {
                val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    healthCertEndDate
                ).toInt()
                val isInvalidRange = healthCertEndDate!!.isBefore(healthCertStartDate)
                Text(
                    text = if (isInvalidRange) {
                        "健康证日期无效"
                    } else {
                        "健康证剩余天数：${daysRemaining} 天"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInvalidRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            Button(
                enabled = !isRegistering && cameraPermissionState.status.isGranted && employeeId.trim().isNotEmpty() && name.trim().isNotEmpty(),
                onClick = {
                    val frame = lastFrameRef.get()
                    if (frame == null) {
                        resultText = "未获取到相机画面"
                        return@Button
                    }

                    scope.launch {
                        isRegistering = true
                        resultText = null
                        try {
                            viewModel.enrollWithFrame(
                                name = name,
                                employeeId = employeeId,
                                department = department,
                                idCardNumber = idCardNumber,
                                healthCertImagePath = healthCertImagePath,
                                healthCertStartDate = healthCertStartDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                                healthCertEndDate = healthCertEndDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                                frame = frame,
                                onResult = { userId ->
                                    resultText = if (userId != null) {
                                        "录入成功: userId=$userId"
                                    } else {
                                        "录入失败"
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            resultText = "录入异常"
                        } finally {
                            isRegistering = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(if (isRegistering) "录入中..." else "采集并录入")
            }

            if (resultText != null) {
                Text(
                    text = resultText ?: "",
                    color = if (resultText?.contains("成功") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "已录入员工 (${users.size})",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(users) { user ->
                    UserRow(user)
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "${user.name}（${user.employeeId}）", style = MaterialTheme.typography.titleSmall)
            if (user.department.isNotBlank()) {
                Text(text = user.department, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = if (user.faceEmbedding != null) "人脸：已录入" else "人脸：未录入",
                style = MaterialTheme.typography.bodySmall,
                color = if (user.faceEmbedding != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun saveHealthCertSnapshot(cacheDir: File, bitmap: Bitmap): String {
    return try {
        val file = File(cacheDir, "health_cert_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        ""
    }
}

private fun showDatePicker(
    context: android.content.Context,
    onDateSelected: (LocalDate) -> Unit
) {
    val now = LocalDate.now()
    DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, day: Int ->
            onDateSelected(LocalDate.of(year, month + 1, day))
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth
    ).show()
}
