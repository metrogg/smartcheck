package com.smartcheck.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.ui.components.CameraCaptureDialog
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.EmployeeDetailViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EmployeeDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: EmployeeDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val faceBitmap by viewModel.faceBitmap.collectAsState()
    val certBitmap by viewModel.certBitmap.collectAsState()

    var name by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var certStart by remember { mutableStateOf<LocalDate?>(null) }
    var certEnd by remember { mutableStateOf<LocalDate?>(null) }
    var healthCertImagePath by remember { mutableStateOf("") }

    var showFaceCamera by remember { mutableStateOf(false) }
    var showCertCamera by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        val value = user ?: return@LaunchedEffect
        name = value.name
        employeeId = value.employeeId
        idCard = value.idCardNumber
        phone = value.phone
        position = value.position
        department = value.department
        certStart = value.healthCertStartDate?.let { millisToLocalDate(it) }
        certEnd = value.healthCertEndDate?.let { millisToLocalDate(it) }
        healthCertImagePath = value.healthCertImagePath
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is EmployeeDetailViewModel.UiEvent.Saved -> onNavigateBack()
                is EmployeeDetailViewModel.UiEvent.Error -> {
                    // TODO: show toast/snackbar
                }
            }
        }
    }

    if (showStartPicker) {
        LaunchedEffect(Unit) {
            showStartPicker = false
            showDatePicker(context) { date ->
                certStart = date
            }
        }
    }

    if (showEndPicker) {
        LaunchedEffect(Unit) {
            showEndPicker = false
            showDatePicker(context) { date ->
                certEnd = date
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Dimens.PaddingLarge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (user == null) "新增员工" else "编辑员工",
                color = BrandGreen,
                fontSize = Dimens.TextSizeTitle,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dimens.PaddingLarge),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            FormRow(label = "姓名") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "编号") {
                OutlinedTextField(
                    value = employeeId,
                    onValueChange = { employeeId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "身份证") {
                OutlinedTextField(
                    value = idCard,
                    onValueChange = { idCard = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "手机") {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "职位") {
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "部门") {
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "人脸") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                            .clickable { showFaceCamera = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val face = faceBitmap
                        if (face != null) {
                            Image(
                                bitmap = face.asImageBitmap(),
                                contentDescription = "人脸",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "拍摄人脸",
                                tint = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                    Text(
                        text = "备注：调取人脸摄像头拍摄",
                        fontSize = Dimens.TextSizeSmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }
            FormRow(label = "健康证") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(120.dp)
                            .background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                            .clickable { showCertCamera = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val cert = certBitmap
                        if (cert != null) {
                            Image(
                                bitmap = cert.asImageBitmap(),
                                contentDescription = "健康证",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "拍摄健康证",
                                tint = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                    Text(
                        text = "备注：调取手部摄像头拍摄",
                        fontSize = Dimens.TextSizeSmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }
            FormRow(label = "起始日期") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartPicker = true }
                ) {
                    OutlinedTextField(
                        value = certStart?.toString().orEmpty(),
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                }
            }
            FormRow(label = "到期日期") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndPicker = true }
                ) {
                    OutlinedTextField(
                        value = certEnd?.toString().orEmpty(),
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = Dimens.PaddingLarge),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (user != null) {
                Button(
                    onClick = { viewModel.deleteUser() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = "删除员工")
                }
                Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
            }
            Button(
                onClick = {
                    if (user == null) {
                        viewModel.saveEmployee(
                            name = name,
                            employeeId = employeeId,
                            idCardNumber = idCard,
                            phone = phone,
                            position = position,
                            department = department,
                            healthCertStartDate = certStart?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                            healthCertEndDate = certEnd?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                            onSuccess = onNavigateBack
                        )
                    } else {
                        viewModel.updateUser(
                            name = name,
                            employeeId = employeeId,
                            idCardNumber = idCard,
                            phone = phone,
                            position = position,
                            department = department,
                            healthCertImagePath = healthCertImagePath,
                            healthCertStartDate = certStart?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                            healthCertEndDate = certEnd?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                Text(text = "提交", color = Color.White)
            }
        }
    }

    if (showFaceCamera) {
        CameraCaptureDialog(
            cameraId = "100",
            onCapture = {
                viewModel.updateFaceBitmap(it)
                showFaceCamera = false
            },
            onDismiss = { showFaceCamera = false }
        )
    }

    if (showCertCamera) {
        CameraCaptureDialog(
            cameraId = "102",
            onCapture = {
                viewModel.updateCertBitmap(it)
                showCertCamera = false
            },
            onDismiss = { showCertCamera = false }
        )
    }
}

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            textAlign = TextAlign.End,
            color = Color.Black,
            fontSize = Dimens.TextSizeNormal
        )
        Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    onDateSelected: (LocalDate) -> Unit
) {
    val now = LocalDate.now()
    DatePickerDialog(
        context,
        { _, year: Int, month: Int, day: Int ->
            onDateSelected(LocalDate.of(year, month + 1, day))
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth
    ).show()
}

private fun millisToLocalDate(millis: Long): LocalDate {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}
