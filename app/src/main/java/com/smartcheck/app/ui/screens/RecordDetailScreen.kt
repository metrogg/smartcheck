package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.viewmodel.RecordDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    var isEditing by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf("") }
    var handStatus by remember { mutableStateOf("") }
    var healthCertStatus by remember { mutableStateOf("") }
    var symptomFlags by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    LaunchedEffect(record) {
        val value = record ?: return@LaunchedEffect
        temperature = value.temperature.toString()
        handStatus = value.handStatus.name
        healthCertStatus = value.healthCertStatus.name
        symptomFlags = value.symptomFlags.joinToString(", ") { it.name }
        remark = value.remark
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑晨检记录" else "晨检记录详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Default.Edit, contentDescription = if (isEditing) "取消编辑" else "编辑")
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentRecord = record
        if (currentRecord == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在")
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxSize()
                        .background(Color(0xFFF4F6F8))
                        .padding(Dimens.PaddingNormal)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                ) {
                    val faceFile = FileUtil.getRecordImageFile(context, currentRecord.faceImagePath)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(Dimens.CornerRadius))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = faceFile,
                            contentDescription = "人脸抓拍",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        text = "人脸抓拍",
                        fontSize = Dimens.TextSizeSmall,
                        color = Color(0xFF6B7280)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                    ) {
                        val palmFile = FileUtil.getRecordImageFile(context, currentRecord.handPalmPath)
                        val backFile = FileUtil.getRecordImageFile(context, currentRecord.handBackPath)
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Dimens.CornerRadius))
                                .background(Color.White),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = palmFile,
                                contentDescription = "手心",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "手心", fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Dimens.CornerRadius))
                                .background(Color.White),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = backFile,
                                contentDescription = "手背",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "手背", fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxSize()
                        .background(BrandGreen.copy(alpha = 0.1f))
                        .padding(Dimens.PaddingLarge)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                ) {
                    Text(
                        text = dateFormat.format(Date(currentRecord.checkTime)),
                        fontSize = Dimens.TextSizeNormal,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    
                    InfoDivider()
                    
                    InfoRow(label = "姓名", value = currentRecord.userName)
                    InfoRow(label = "工号", value = currentRecord.employeeId.ifBlank { "--" })
                    
                    val tempText = String.format(Locale.getDefault(), "%.1f℃", currentRecord.temperature)
                    InfoRow(
                        label = "体温",
                        value = tempText,
                        valueColor = if (currentRecord.temperature >= 37.3f) MaterialTheme.colorScheme.error else BrandGreen
                    )
                    
                    val symptomValue = currentRecord.symptomFlags
                    val symptomDisplay = if (symptomValue.isEmpty()) "无" else symptomValue.joinToString(", ") { it.name }
                    InfoRow(
                        label = "症状",
                        value = symptomDisplay,
                        valueColor = if (symptomDisplay == "无") BrandGreen else MaterialTheme.colorScheme.error
                    )

                    InfoDivider()

                    Text(
                        text = "编辑信息",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    EditField(label = "体温", value = temperature, enabled = isEditing) { temperature = it }
                    EditField(label = "手部情况", value = handStatus, enabled = isEditing) { handStatus = it }
                    EditField(label = "健康证状态", value = healthCertStatus, enabled = isEditing) { healthCertStatus = it }
                    EditField(label = "身体不适", value = symptomFlags, enabled = isEditing) { symptomFlags = it }
                    EditField(label = "备注", value = remark, enabled = isEditing) { remark = it }

                    if (isEditing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.updateRecord(
                                    temperature = temperature.toFloatOrNull() ?: 0f,
                                    handStatus = handStatus,
                                    healthCertStatus = healthCertStatus,
                                    symptomFlags = symptomFlags,
                                    remark = remark
                                )
                                isEditing = false
                            }
                        ) {
                            Text("保存修改")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E7EB))
    )
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label：", fontSize = Dimens.TextSizeSmall, color = Color.Gray)
        Text(text = value, fontSize = Dimens.TextSizeNormal, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = Dimens.TextSizeSmall) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium
    )
}
