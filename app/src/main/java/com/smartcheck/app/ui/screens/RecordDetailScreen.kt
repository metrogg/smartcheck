package com.smartcheck.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.viewmodel.RecordDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
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
        symptomFlags = value.symptomFlags.joinToString(",") { it.name }
        remark = value.remark
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("晨检记录详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "检测信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoField(label = "体温", value = temperature, enabled = isEditing) { temperature = it }
            InfoField(label = "手部情况", value = handStatus, enabled = isEditing) { handStatus = it }
            InfoField(label = "健康证状态", value = healthCertStatus, enabled = isEditing) { healthCertStatus = it }
            InfoField(label = "身体不适", value = symptomFlags, enabled = isEditing) { symptomFlags = it }
            InfoField(label = "备注", value = remark, enabled = isEditing) { remark = it }

            if (isEditing) {
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
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled
    )
}
