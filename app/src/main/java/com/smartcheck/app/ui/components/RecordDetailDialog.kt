package com.smartcheck.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.BrandLightGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.utils.FileUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordDetailDialog(
    record: RecordEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.85f),
            shape = RoundedCornerShape(Dimens.CornerRadius),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingNormal),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "晨检记录详情",
                        fontSize = Dimens.TextSizeTitle,
                        color = Color.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxSize()
                            .background(Color(0xFFF4F6F8))
                            .padding(Dimens.PaddingNormal),
                        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                    ) {
                        val faceFile = FileUtil.getRecordImageFile(context, record.faceImagePath)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
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
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                        ) {
                            val palmFile = FileUtil.getRecordImageFile(context, record.handPalmPath)
                            val backFile = FileUtil.getRecordImageFile(context, record.handBackPath)
                            Column(
                                modifier = Modifier.weight(1f),
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
                                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                                Text(text = "手心", fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
                            }
                            Column(
                                modifier = Modifier.weight(1f),
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
                                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                                Text(text = "手背", fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxSize()
                            .background(BrandLightGreen)
                            .padding(Dimens.PaddingLarge)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                    ) {
                        Text(
                            text = dateFormat.format(Date(record.checkTime)),
                            fontSize = Dimens.TextSizeNormal,
                            color = Color.Black
                        )
                        InfoRow(label = "姓名", value = record.userName)
                        InfoRow(label = "工号", value = record.employeeId.ifBlank { "--" })
                        val tempText = String.format(Locale.getDefault(), "%.1f℃", record.temperature)
                        InfoRow(
                            label = "体温",
                            value = tempText,
                            valueColor = if (record.temperature >= 37.3f) MaterialTheme.colorScheme.error else BrandGreen
                        )
                        val symptomValue = record.symptomFlags.ifBlank { "无" }
                        InfoRow(
                            label = "症状",
                            value = symptomValue,
                            valueColor = if (symptomValue == "无") BrandGreen else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
        Text(text = value, fontSize = Dimens.TextSizeNormal, color = valueColor)
    }
}
