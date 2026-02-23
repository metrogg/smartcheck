package com.smartcheck.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.ui.components.CameraPreview
import com.smartcheck.app.viewmodel.EmployeeEnrollViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EmployeeEnrollScreen(
    onNavigateBack: () -> Unit,
    viewModel: EmployeeEnrollViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val lastFrameRef = remember { AtomicReference<Bitmap?>(null) }

    var employeeId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
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
                            val userId = viewModel.enrollWithFrame(
                                name = name,
                                employeeId = employeeId,
                                department = department,
                                frame = frame
                            )
                            resultText = if (userId != null) {
                                "录入成功: userId=$userId"
                            } else {
                                "录入失败"
                            }
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
private fun UserRow(user: UserEntity) {
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
