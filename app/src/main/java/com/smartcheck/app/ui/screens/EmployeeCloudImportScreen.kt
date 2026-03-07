package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.CloudImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCloudImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: CloudImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.importSuccess) {
        if (uiState.importSuccess) {
            // 导入成功后会弹出对话框，这里不需要额外处理
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("从云端新增员工") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // 参数设置区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingLarge),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.PaddingLarge)
                ) {
                    Text(
                        text = "接口参数设置",
                        fontWeight = FontWeight.Bold,
                        color = BrandGreen
                    )
                    Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
                    OutlinedTextField(
                        value = uiState.deviceSn,
                        onValueChange = { viewModel.setDeviceSn(it) },
                        label = { Text("设备编码 (yg_sn)") },
                        placeholder = { Text("请输入设备编码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                    ) {
                        OutlinedTextField(
                            value = uiState.pageIndex.toString(),
                            onValueChange = { 
                                val index = it.toIntOrNull() ?: 0
                                viewModel.setPageIndex(index.coerceAtLeast(0))
                            },
                            label = { Text("页码") },
                            placeholder = { Text("0") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.pageSize,
                            onValueChange = { viewModel.setPageSize(it) },
                            label = { Text("每页条数(1-100)") },
                            placeholder = { Text("50") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // 获取按钮
            Button(
                onClick = { viewModel.fetchEmployees() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.PaddingLarge),
                enabled = uiState.deviceSn.isNotBlank() && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("获取员工信息")
            }

            // 加载状态
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }

            // 错误信息
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingLarge),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Dimens.PaddingLarge)
                    )
                    Button(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.align(Alignment.End).padding(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("关闭")
                    }
                }
            }

            // 员工列表
            if (uiState.employees.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.PaddingLarge),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${uiState.total} 条记录，当前 ${uiState.employees.size} 条",
                        color = Color.Gray
                    )
                    Row {
                        TextButton(onClick = { viewModel.selectAll(true) }) {
                            Text("全选")
                        }
                        TextButton(onClick = { viewModel.selectAll(false) }) {
                            Text("取消")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.PaddingLarge),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.employees) { employee ->
                        EmployeeCloudItem(
                            employee = employee,
                            onToggle = { viewModel.toggleEmployeeSelection(employee.employeeId) }
                        )
                    }
                }

                // 导入按钮
                val selectedCount = uiState.employees.count { it.selected }
                Button(
                    onClick = { viewModel.importSelectedEmployees() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingLarge),
                    enabled = selectedCount > 0 && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入已选员工 ($selectedCount)")
                }
            }
        }

        // 导入结果对话框
        if (uiState.importSuccess && uiState.importResult != null) {
            AlertDialog(
                onDismissRequest = { 
                    viewModel.clearImportResult()
                    onNavigateBack()
                },
                title = { Text("导入完成") },
                text = {
                    Column {
                        Text("总数：${uiState.importResult?.total}")
                        Text("成功：${uiState.importResult?.success}")
                        Text("失败：${uiState.importResult?.failed}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.clearImportResult()
                        onNavigateBack()
                    }) {
                        Text("确定")
                    }
                }
            )
        }
    }
}

@Composable
private fun EmployeeCloudItem(
    employee: CloudImportViewModel.CloudEmployeeItem,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (employee.selected) Color(0xFFE8F5E9) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingNormal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (employee.selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (employee.selected) "取消选择" else "选择",
                    tint = if (employee.selected) BrandGreen else Color.Gray
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employee.name,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "工号：${employee.employeeId}",
                    color = Color.Gray,
                    fontSize = Dimens.TextSizeSmall
                )
                if (employee.phone.isNotBlank()) {
                    Text(
                        text = "手机：${employee.phone}",
                        color = Color.Gray,
                        fontSize = Dimens.TextSizeSmall
                    )
                }
                if (employee.position.isNotBlank()) {
                    Text(
                        text = "职位：${employee.position}",
                        color = Color.Gray,
                        fontSize = Dimens.TextSizeSmall
                    )
                }
                if (employee.healthCertCode.isNotBlank()) {
                    Text(
                        text = "健康证：${employee.healthCertCode}",
                        color = Color.Gray,
                        fontSize = Dimens.TextSizeSmall
                    )
                }
            }
        }
    }
}
