package com.smartcheck.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.ReportExportViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReportExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportExportViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val records by viewModel.records.collectAsState()
    var dateFilter by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val historyItems = remember { mutableStateListOf<ExportHistoryItem>() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        historyItems.clear()
        historyItems.addAll(loadExportHistory(context))
    }

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
                text = "数据导出",
                color = BrandGreen,
                fontSize = Dimens.TextSizeTitle,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = BrandGreen)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingNormal))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = dateFilter,
                onValueChange = { dateFilter = it },
                modifier = Modifier
                    .width(420.dp)
                    .height(Dimens.InputHeight),
                singleLine = true,
                label = { Text("日期筛选") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            Button(
                onClick = {
                    if (exporting) return@Button
                    exporting = true
                    val result = exportRecordsCsv(
                        context = context,
                        records = records,
                        dateFilter = dateFilter
                    )
                    exporting = false
                    if (result != null) {
                        val rangeText = buildRangeText(records, dateFilter)
                        val item = ExportHistoryItem(
                            id = System.currentTimeMillis().toString(),
                            createdAt = System.currentTimeMillis(),
                            year = SimpleDateFormat("yyyy", Locale.getDefault()).format(System.currentTimeMillis()),
                            rangeText = rangeText,
                            fileName = result.displayName,
                            uri = result.uri.toString(),
                            absolutePath = result.absolutePath,
                            dateFilter = dateFilter
                        )
                        historyItems.add(0, item)
                        writeExportHistory(context, historyItems)
                        Toast.makeText(context, "已保存到: ${result.absolutePath}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .height(Dimens.InputHeight)
                    .width(220.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                Text(text = "确认导出", color = Color.White, fontSize = Dimens.TextSizeNormal)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

        Text(
            text = "导出记录",
            fontSize = Dimens.TextSizeLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB))
        ) {
            ExportHeaderRow()
            if (historyItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无导出记录", color = Color(0xFF6B7280))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(historyItems) { index, item ->
                        ExportRow(
                            index = index + 1,
                            item = item,
                            onDownload = {
                                handleDownload(context, records, historyItems, item)
                            },
                            onShare = {
                                handleShare(context, records, historyItems, item)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "下载即下载到设备本地文件；转发可选择本机微信进行分享",
                fontSize = Dimens.TextSizeSmall,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun ExportHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = Dimens.PaddingNormal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(text = "序号", width = 80.dp)
        HeaderCell(text = "年份", width = 100.dp)
        HeaderCell(text = "日期范围", width = 200.dp)
        HeaderCell(text = "文件名", width = 220.dp)
        HeaderCell(text = "操作", width = 140.dp)
    }
}

@Composable
private fun ExportRow(
    index: Int,
    item: ExportHistoryItem,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = Dimens.PaddingNormal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(text = index.toString(), width = 80.dp)
        BodyCell(text = item.year, width = 100.dp)
        BodyCell(text = item.rangeText, width = 200.dp)
        BodyCell(text = "晨检记录表格.xlsx", width = 220.dp)
        Row(
            modifier = Modifier.width(140.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "下载",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onDownload)
            )
            Text(
                text = "转发",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onShare)
            )
        }
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
private fun BodyCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = Color(0xFF111827),
        fontSize = Dimens.TextSizeSmall
    )
}

private fun shareExportFile(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "分享导出文件")
    context.startActivity(chooser)
}

private fun exportRecordsCsv(
    context: Context,
    records: List<RecordEntity>,
    dateFilter: String
): ExportResult? {
    return try {
        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val displayName = "check_report_${format.format(System.currentTimeMillis())}.csv"
        val csvContent = buildCsvContent(records, dateFilter)
        saveToDownloads(context, displayName, csvContent)
    } catch (_: Exception) {
        null
    }
}

private data class ExportResult(
    val uri: Uri,
    val displayName: String,
    val absolutePath: String
)

private fun buildCsvContent(
    records: List<RecordEntity>,
    dateFilter: String
): ByteArray {
    val dateKey = dateFilter.trim()
    val filtered = records.filter { record ->
        dateKey.isBlank() || SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(record.checkTime)
            .contains(dateKey)
    }
    val header = "姓名,工号,体温,手部情况,健康证状态,身体不适,结果,时间,人脸照片,手心照片,手背照片\n"
    val builder = StringBuilder(header)
    filtered.forEach { record ->
        val row = listOf(
            record.userName,
            record.employeeId,
            String.format(Locale.getDefault(), "%.1f", record.temperature),
            record.handStatus,
            record.healthCertStatus,
            record.symptomFlags,
            if (record.isPassed) "通过" else "未通过",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(record.checkTime),
            record.faceImagePath.orEmpty(),
            record.handPalmPath.orEmpty(),
            record.handBackPath.orEmpty()
        ).joinToString(",") { it.replace(",", " ") }
        builder.append(row).append("\n")
    }
    return builder.toString().toByteArray()
}

private fun saveToDownloads(
    context: Context,
    displayName: String,
    content: ByteArray
): ExportResult? {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val absolutePath = File(downloadsDir, displayName).absolutePath

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content)
        }
        ExportResult(uri = uri, displayName = displayName, absolutePath = absolutePath)
    } else {
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, displayName)
        FileOutputStream(file).use { out ->
            out.write(content)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        ExportResult(uri = uri, displayName = displayName, absolutePath = absolutePath)
    }
}

private data class ExportHistoryItem(
    val id: String,
    val createdAt: Long,
    val year: String,
    val rangeText: String,
    val fileName: String,
    val uri: String,
    val absolutePath: String,
    val dateFilter: String
)

private fun buildRangeText(records: List<RecordEntity>, dateFilter: String): String {
    val dateKey = dateFilter.trim()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val filtered = records.filter { record ->
        dateKey.isBlank() || dateFormat.format(record.checkTime).contains(dateKey)
    }
    if (filtered.isEmpty()) return "--"
    val sorted = filtered.sortedBy { it.checkTime }
    val start = dateFormat.format(sorted.first().checkTime)
    val end = dateFormat.format(sorted.last().checkTime)
    return "$start - $end"
}

private fun handleDownload(
    context: Context,
    records: List<RecordEntity>,
    history: List<ExportHistoryItem>,
    item: ExportHistoryItem
) {
    val existing = resolveHistoryUri(context, item)
    if (existing != null) {
        Toast.makeText(context, "已保存到: ${item.absolutePath}", Toast.LENGTH_SHORT).show()
        return
    }
    val result = exportRecordsCsv(context, records, item.dateFilter)
    if (result != null) {
        val updated = item.copy(
            uri = result.uri.toString(),
            absolutePath = result.absolutePath
        )
        writeExportHistory(context, history.map { if (it.id == item.id) updated else it })
        Toast.makeText(context, "已保存到: ${result.absolutePath}", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
    }
}

private fun handleShare(
    context: Context,
    records: List<RecordEntity>,
    history: List<ExportHistoryItem>,
    item: ExportHistoryItem
) {
    val existing = resolveHistoryUri(context, item)
    if (existing != null) {
        shareExportFile(context, existing)
        return
    }
    val result = exportRecordsCsv(context, records, item.dateFilter)
    if (result != null) {
        val updated = item.copy(
            uri = result.uri.toString(),
            absolutePath = result.absolutePath
        )
        writeExportHistory(context, history.map { if (it.id == item.id) updated else it })
        shareExportFile(context, result.uri)
    } else {
        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
    }
}

private fun resolveHistoryUri(context: Context, item: ExportHistoryItem): Uri? {
    return try {
        val uri = Uri.parse(item.uri)
        context.contentResolver.openInputStream(uri)?.close()
        uri
    } catch (_: FileNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun historyFile(context: Context): File {
    return File(context.filesDir, "export_history.csv")
}

private fun loadExportHistory(context: Context): List<ExportHistoryItem> {
    val file = historyFile(context)
    if (!file.exists()) return emptyList()
    return runCatching {
        file.readLines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 8) return@mapNotNull null
            ExportHistoryItem(
                id = parts[0],
                createdAt = parts[1].toLongOrNull() ?: 0L,
                year = parts[2],
                rangeText = parts[3],
                fileName = parts[4],
                uri = parts[5],
                absolutePath = parts[6],
                dateFilter = parts[7]
            )
        }
    }.getOrDefault(emptyList())
}

private fun writeExportHistory(context: Context, items: List<ExportHistoryItem>) {
    val file = historyFile(context)
    val content = buildString {
        items.forEach { item ->
            append(
                listOf(
                    item.id,
                    item.createdAt.toString(),
                    item.year,
                    item.rangeText,
                    item.fileName,
                    item.uri,
                    item.absolutePath,
                    item.dateFilter
                ).joinToString("|")
            )
            append("\n")
        }
    }
    file.writeText(content)
}
