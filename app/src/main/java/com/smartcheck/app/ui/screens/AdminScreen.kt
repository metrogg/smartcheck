package com.smartcheck.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.ui.components.RecordDetailDialog
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.RecordsViewModel
import com.smartcheck.app.viewmodel.RecordsViewModel.TimeFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    onNavigateExport: (() -> Unit)? = null,
    onNavigateRecordDetail: ((Long) -> Unit)? = null,
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val query by viewModel.query.collectAsState()
    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.TODAY) }
    var statusFilter by remember { mutableStateOf("") }
    var selectedRecord by remember { mutableStateOf<RecordEntity?>(null) }

    // 同步时间筛选
    LaunchedEffect(selectedTimeFilter) {
        viewModel.setTimeFilter(selectedTimeFilter)
    }

    // 同步状态筛选
    LaunchedEffect(statusFilter) {
        val statusValue = when (statusFilter) {
            "通过" -> "NORMAL"
            "不合格" -> "ABNORMAL"
            else -> ""
        }
        viewModel.setHandStatusFilter(statusValue)
    }

    // 时间筛选选项
    val timeFilterOptions = listOf(
        "今天" to TimeFilter.TODAY,
        "本周" to TimeFilter.WEEK,
        "本月" to TimeFilter.MONTH,
        "全部" to TimeFilter.ALL
    )

    // 状态筛选选项
    val statusOptions = listOf("全部", "通过", "不合格")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(Dimens.PaddingLarge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "晨检记录",
                color = BrandGreen,
                fontSize = Dimens.TextSizeTitle,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = BrandGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingNormal))

        // 筛选栏
        Column {
            // 日期和状态筛选按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日期筛选
                Text("日期:", fontSize = Dimens.TextSizeNormal)
                timeFilterOptions.forEach { (label, filter) ->
                    val isSelected = selectedTimeFilter == filter
                    Text(
                        text = label,
                        color = if (isSelected) BrandGreen else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedTimeFilter = filter }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                
                // 状态筛选
                Text("状态:", fontSize = Dimens.TextSizeNormal)
                statusOptions.forEach { option ->
                    val isSelected = (option == "全部" && statusFilter.isEmpty()) || statusFilter == option
                    Text(
                        text = option,
                        color = if (isSelected) BrandGreen else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { 
                                statusFilter = if (option == "全部") "" else option 
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            
            // 人员搜索
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterField(
                    label = "人员",
                    value = query,
                    onValueChange = viewModel::setQuery,
                    trailingIcon = Icons.Default.Search,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingNormal))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            TableHeader()
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无记录",
                        color = Color(0xFF6B7280),
                        fontSize = Dimens.TextSizeNormal
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records) { record ->
                        RecordTableRow(
                            record = record,
                            onView = {
                                selectedRecord = record
                            },
                            onEdit = {
                                onNavigateRecordDetail?.invoke(record.id)
                            },
                            onClick = { selectedRecord = record }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingNormal))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onNavigateExport?.invoke() },
                modifier = Modifier
                    .height(64.dp)
                    .width(320.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                Text(text = "导出当前晨检记录", color = Color.White, fontSize = Dimens.TextSizeNormal)
            }
        }
    }

    selectedRecord?.let { record ->
        RecordDetailDialog(
            record = record,
            onDismiss = { selectedRecord = null }
        )
    }
}

@Composable
private fun FilterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = Dimens.TextSizeNormal,
            color = Color(0xFF111827),
            modifier = Modifier.width(72.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .height(Dimens.InputHeight)
                .fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                Icon(trailingIcon, contentDescription = null)
            }
        )
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp, horizontal = Dimens.PaddingNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(text = "日期", width = 180.dp)
        HeaderCell(text = "姓名", width = 120.dp)
        HeaderCell(text = "体温", width = 90.dp)
        HeaderCell(text = "手部情况", width = 120.dp)
        HeaderCell(text = "其他身体不适", width = 180.dp)
        HeaderCell(text = "操作", width = 140.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = Color(0xFF6B7280),
        fontSize = Dimens.TextSizeSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RecordTableRow(
    record: RecordEntity,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val tempHigh = record.temperature >= 37.3f
    val handIssue = record.handStatus.contains("异常") || !record.isHandNormal
    val hasSymptom = record.symptomFlags.isNotBlank() && record.symptomFlags != "无"
    val warningRow = tempHigh || handIssue
    val background = if (warningRow) Color(0xFFFFF1F1) else Color.White
    val textColor = if (warningRow) MaterialTheme.colorScheme.error else Color(0xFF111827)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = Dimens.PaddingNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(text = dateFormat.format(Date(record.checkTime)), width = 180.dp, color = textColor)
        BodyCell(text = record.userName, width = 120.dp, color = textColor)
        BodyCell(text = "%.1f°C".format(record.temperature), width = 90.dp, color = textColor)
        BodyCell(text = record.handStatus.ifBlank { "--" }, width = 120.dp, color = textColor)
        BodyCell(text = record.symptomFlags.ifBlank { "无" }, width = 180.dp, color = textColor)
        Row(
            modifier = Modifier.width(140.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onView)
            )
            Text(
                text = "编辑",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onEdit)
            )
        }
    }
}

@Composable
private fun BodyCell(text: String, width: Dp, color: Color) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = color,
        fontSize = Dimens.TextSizeSmall
    )
}
