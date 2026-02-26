package com.smartcheck.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcheck.app.viewmodel.CheckState

/**
 * 检测结果卡片组件
 */
@Composable
fun ResultCard(
    userName: String,
    temperature: Float,
    state: CheckState,
    message: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(getStateColor(state))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态消息
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            if (userName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                
                // 用户名
                Text(
                    text = userName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // 温度显示
                if (temperature > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "体温：%.1f°C".format(temperature),
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 根据状态返回对应颜色
 */
private fun getStateColor(state: CheckState): Color {
    return when (state) {
        CheckState.IDLE -> Color(0xFF2196F3) // 蓝色
        CheckState.FACE_PASS -> Color(0xFF4CAF50) // 绿色
        CheckState.TEMP_MEASURING -> Color(0xFFFF9800) // 橙色
        CheckState.TEMP_FAIL -> Color(0xFFF44336) // 红色
        CheckState.HAND_CHECKING -> Color(0xFFFF9800) // 橙色
        CheckState.HAND_PALM_CHECKING -> Color(0xFFFF9800) // 橙色
        CheckState.HAND_BACK_CHECKING -> Color(0xFFFF9800) // 橙色
        CheckState.HAND_FAIL -> Color(0xFFF44336) // 红色
        CheckState.SYMPTOM_CHECKING -> Color(0xFFFF9800) // 橙色
        CheckState.SYMPTOM_FAIL -> Color(0xFFF44336) // 红色
        CheckState.AUTO_SUBMITTING -> Color(0xFF4CAF50) // 绿色
        CheckState.ALL_PASS -> Color(0xFF4CAF50) // 绿色
    }
}
