package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.BrandLightGreen
import com.smartcheck.app.ui.theme.Dimens

@Composable
fun DashboardScreen(
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "欢迎进入 上海交通大学荔园三食堂",
                color = BrandGreen,
                fontSize = Dimens.TextSizeNormal,
                modifier = Modifier.padding(start = 16.dp)
            )
            IconButton(onClick = onNavigateSettings) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "设置",
                    tint = BrandGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            StepGuidePanel(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .background(BrandLightGreen)
                    .padding(Dimens.PaddingNormal)
            )

            EntryGrid(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .padding(Dimens.PaddingNormal),
                onNavigateCheck = onNavigateCheck,
                onNavigateEmployees = onNavigateEmployees,
                onNavigateRecords = onNavigateRecords,
                onNavigateExport = onNavigateExport
            )
        }
    }
}

@Composable
private fun StepGuidePanel(modifier: Modifier = Modifier) {
    val steps = listOf("刷脸验温", "手部双面识别", "身体不适确认", "生成晨检记录")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        steps.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(BrandGreen, CircleShape)
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = Dimens.PaddingSmall),
                    fontSize = Dimens.TextSizeNormal,
                    color = Color.Black
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(2.dp)
                        .height(24.dp)
                        .background(BrandGreen)
                )
            }
        }
    }
}

@Composable
private fun EntryGrid(
    modifier: Modifier = Modifier,
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit
) {
    val entries = listOf(
        DashboardEntry("我要晨检", Icons.Default.VerifiedUser, onNavigateCheck),
        DashboardEntry("员工管理", Icons.Default.Badge, onNavigateEmployees),
        DashboardEntry("晨检记录", Icons.Default.ListAlt, onNavigateRecords),
        DashboardEntry("报表导出", Icons.Default.Assessment, onNavigateExport)
    )

    BoxWithConstraints(modifier = modifier) {
        val totalSpacing = Dimens.PaddingNormal
        val itemHeight = (maxHeight - totalSpacing) / 2

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
        ) {
            items(entries) { entry ->
                EntryCard(
                    label = entry.label,
                    icon = entry.icon,
                    onClick = entry.onClick,
                    modifier = Modifier.height(itemHeight)
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = BrandGreen)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Box(modifier = Modifier.height(Dimens.PaddingSmall))
            Text(
                text = label,
                color = Color.White,
                fontSize = Dimens.TextSizeLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class DashboardEntry(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)
