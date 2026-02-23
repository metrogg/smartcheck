package com.smartcheck.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.viewmodel.RecordsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 管理后台界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onNavigateEmployeeEnroll: (() -> Unit)? = null,
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val query by viewModel.query.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("晨检记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (onNavigateEmployeeEnroll != null) {
                        TextButton(onClick = onNavigateEmployeeEnroll) {
                            Text("员工录入")
                        }
                    }
                    if (onLogout != null) {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.Logout, contentDescription = "退出登录")
                        }
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
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("姓名 / 工号") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = timeFilter == RecordsViewModel.TimeFilter.TODAY,
                    onClick = { viewModel.setTimeFilter(RecordsViewModel.TimeFilter.TODAY) },
                    label = { Text("今天") }
                )
                FilterChip(
                    selected = timeFilter == RecordsViewModel.TimeFilter.WEEK,
                    onClick = { viewModel.setTimeFilter(RecordsViewModel.TimeFilter.WEEK) },
                    label = { Text("7天") }
                )
                FilterChip(
                    selected = timeFilter == RecordsViewModel.TimeFilter.MONTH,
                    onClick = { viewModel.setTimeFilter(RecordsViewModel.TimeFilter.MONTH) },
                    label = { Text("30天") }
                )
                FilterChip(
                    selected = timeFilter == RecordsViewModel.TimeFilter.ALL,
                    onClick = { viewModel.setTimeFilter(RecordsViewModel.TimeFilter.ALL) },
                    label = { Text("全部") }
                )
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.TopCenter
                ) {
                    Text(
                        text = "暂无记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(records) { record ->
                        RecordItem(record)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordItem(record: RecordEntity) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = record.userName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (record.isPassed) "✓ 通过" else "✗ 未通过",
                    color = if (record.isPassed) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "工号：${record.employeeId}",
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = "体温：%.1f°C".format(record.temperature),
                style = MaterialTheme.typography.bodySmall
            )

            if (record.remark.isNotBlank()) {
                Text(
                    text = "备注：${record.remark}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "时间：${dateFormat.format(Date(record.checkTime))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
