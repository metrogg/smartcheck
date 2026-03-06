package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.viewmodel.EmployeeListViewModel

@Composable
fun EmployeeListScreen(
    onNavigateBack: () -> Unit,
    onNavigateEmployeeDetail: (String) -> Unit,
    onNavigateEmployeeNew: () -> Unit,
    viewModel: EmployeeListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingLarge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "员工管理",
                color = BrandGreen,
                fontSize = Dimens.TextSizeTitle,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "查找员工",
                    color = Color.Black,
                    fontSize = Dimens.TextSizeNormal
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.setQuery(it) },
                    modifier = Modifier
                        .width(250.dp)
                        .height(Dimens.InputHeight),
                    singleLine = true,
                    trailingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        }

        val items = listOf<EmployeeListViewModel.EmployeeListItem?>(null) + uiState.items

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(items) { index, employee ->
                if (index == 0) {
                    AddEmployeeCard(onClick = onNavigateEmployeeNew)
                } else if (employee != null) {
                    EmployeeCard(
                        employee = employee,
                        onClick = { onNavigateEmployeeDetail(employee.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEmployeeCard(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = BrandGreen)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新增员工",
                    tint = BrandGreen,
                    modifier = Modifier.size(60.dp)
                )
            }
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: EmployeeListViewModel.EmployeeListItem,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val daysLeft = employee.daysRemaining
    val daysColor = if (daysLeft < 7) MaterialTheme.colorScheme.error else Color.White
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = BrandGreen)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "姓名：",
                        color = Color.Gray,
                        fontSize = Dimens.TextSizeSmall
                    )
                    Text(
                        text = employee.name,
                        color = Color.Black,
                        fontSize = Dimens.TextSizeSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "操作",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = Dimens.TextSizeSmall
                )
            }

            val imageFile = FileUtil.getRecordImageFile(context, employee.faceImagePath)
            val request = ImageRequest.Builder(context)
                .data(imageFile)
                .size(width = 200, height = 200)
                .crossfade(true)
                .build()
            SubcomposeAsyncImage(
                model = request,
                contentDescription = employee.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            ) {
                when (painter.state) {
                    is coil.compose.AsyncImagePainter.State.Loading,
                    is coil.compose.AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFE5E7EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "健康证剩余：",
                    color = Color.Gray,
                    fontSize = Dimens.TextSizeSmall
                )
                Text(
                    text = formatDays(daysLeft),
                    color = Color.Black,
                    fontSize = Dimens.TextSizeSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatDays(days: Int): String = when {
    days < 0 -> "已过期 ${kotlin.math.abs(days)}天"
    else -> "$days 天"
}
