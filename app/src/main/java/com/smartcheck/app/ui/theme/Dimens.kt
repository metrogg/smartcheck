package com.smartcheck.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Dimens {
    // 【720p 适配】高度压缩：按钮高度降为 48dp (标准触摸高度)
    val ButtonHeight = 48.dp
    val InputHeight = 48.dp

    // 间距压缩
    val PaddingSmall = 8.dp
    val PaddingNormal = 12.dp  // 原来16dp，现在改小
    val PaddingLarge = 20.dp

    // 【720p 适配】字号微调：避免大字号撑爆布局
    val TextSizeSmall = 14.sp
    val TextSizeNormal = 16.sp  // 正文标准
    val TextSizeLarge = 20.sp   // 强调文字
    val TextSizeTitle = 24.sp   // 页面大标题 (原28sp，降级)

    // 圆角
    val CornerRadius = 8.dp

    // 列表项高度 (员工管理/记录)
    val ListItemHeight = 60.dp
}
