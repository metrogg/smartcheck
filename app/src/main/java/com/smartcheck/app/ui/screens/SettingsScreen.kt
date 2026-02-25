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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val adminName by viewModel.adminName.collectAsState()
    val account by viewModel.account.collectAsState()
    val canteenName by viewModel.canteenName.collectAsState()
    val loginTitle by viewModel.loginTitle.collectAsState()
    val loginBackground by viewModel.loginBackground.collectAsState()
    val adminAvatar by viewModel.adminAvatar.collectAsState()
    val context = LocalContext.current

    var dialogLabel by remember { mutableStateOf("") }
    var dialogValue by remember { mutableStateOf("") }
    var onDialogConfirm by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showUpdateLoading by remember { mutableStateOf(false) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showBackgroundMenu by remember { mutableStateOf(false) }

    fun openEdit(label: String, value: String, onConfirm: (String) -> Unit) {
        dialogLabel = label
        dialogValue = value
        onDialogConfirm = onConfirm
        showDialog = true
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setAdminAvatar(uri.toString())
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setLoginBackground(uri.toString())
        }
    }

    val cameraImageFile = remember {
        val fileName = "avatar_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())}.jpg"
        File(context.cacheDir, fileName)
    }
    val cameraImageUri = remember(cameraImageFile) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cameraImageFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.setAdminAvatar(cameraImageUri.toString())
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxSize()
                .background(BrandGreen)
                .padding(Dimens.PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
        ) {
            Text(
                text = "设置中心",
                color = Color.White,
                fontSize = Dimens.TextSizeTitle,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFFE11D48), RoundedCornerShape(10.dp))
                        .padding(horizontal = Dimens.PaddingNormal),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "管理员设置",
                        color = Color.White,
                        fontSize = Dimens.TextSizeNormal
                    )
                }
                Text(
                    text = "设备设置",
                    color = Color.White,
                    fontSize = Dimens.TextSizeNormal
                )
                Text(
                    text = "导出管理",
                    color = Color.White,
                    fontSize = Dimens.TextSizeNormal
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.75f)
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimens.PaddingLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = BrandGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE5E7EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (adminAvatar.isNotBlank()) {
                            AsyncImage(
                                model = adminAvatar,
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "头像",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                    Text(
                        text = "修改头像",
                        color = Color(0xFF2563EB),
                        fontSize = Dimens.TextSizeNormal,
                        modifier = Modifier.clickable { showAvatarMenu = true }
                    )
                    DropdownMenu(
                        expanded = showAvatarMenu,
                        onDismissRequest = { showAvatarMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("拍照") },
                            onClick = {
                                showAvatarMenu = false
                                cameraLauncher.launch(cameraImageUri)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("从相册选择") },
                            onClick = {
                                showAvatarMenu = false
                                avatarPicker.launch("image/*")
                            }
                        )
                    }
                }

                SettingRow(
                    label = "管理员姓名",
                    value = if (adminName.isBlank()) "赵某某" else adminName,
                    onEdit = { openEdit("管理员姓名", adminName) { viewModel.setAdminName(it) } }
                )
                SettingRow(
                    label = "登录账号",
                    value = if (account.isBlank()) "12345678901" else account,
                    onEdit = { openEdit("登录账号", account) { viewModel.setAccount(it) } }
                )
                SettingRow(
                    label = "登录密码",
                    value = "......",
                    onEdit = { openEdit("登录密码", "") { viewModel.setPassword(it) } }
                )
                SettingRow(
                    label = "食堂名称",
                    value = if (canteenName.isBlank()) "上海交通大学荔园三食堂" else canteenName,
                    onEdit = { openEdit("食堂名称", canteenName) { viewModel.setCanteenName(it) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "当前版本",
                        fontSize = Dimens.TextSizeNormal,
                        color = Color(0xFF111827)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "V1.0.6",
                            fontSize = Dimens.TextSizeNormal,
                            color = Color(0xFF111827)
                        )
                        Spacer(modifier = Modifier.width(Dimens.PaddingNormal))
                        Button(
                            onClick = {
                                if (showUpdateLoading) return@Button
                                showUpdateLoading = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                        ) {
                            if (showUpdateLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                            }
                            Text(text = "获取新版", color = Color.White)
                        }
                    }
                }

                SettingRow(
                    label = "登录页标题",
                    value = if (loginTitle.isBlank()) "欢迎使用智能晨检仪" else loginTitle,
                    onEdit = { openEdit("登录页标题", loginTitle) { viewModel.setLoginTitle(it) } },
                    preview = if (loginTitle.isBlank()) "欢迎使用智能晨检仪" else loginTitle
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "登录页背景图",
                            fontSize = Dimens.TextSizeNormal,
                            color = Color(0xFF111827)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(120.dp, 70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE5E7EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loginBackground.isNotBlank()) {
                                AsyncImage(
                                    model = loginBackground,
                                    contentDescription = "背景缩略图",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(text = "无", color = Color.Gray)
                            }
                        }
                    }
                    Text(
                        text = "修改",
                        color = Color(0xFF2563EB),
                        fontSize = Dimens.TextSizeNormal,
                        modifier = Modifier.clickable {
                            showBackgroundMenu = true
                        }
                    )
                    DropdownMenu(
                        expanded = showBackgroundMenu,
                        onDismissRequest = { showBackgroundMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("从相册选择") },
                            onClick = {
                                showBackgroundMenu = false
                                backgroundPicker.launch("image/*")
                            }
                        )
                    }
                }

                Button(
                    onClick = { viewModel.clearRecordImages() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(text = "清理历史记录照片", color = Color.White, fontSize = Dimens.TextSizeNormal)
                }
            }
        }
    }

    if (showUpdateLoading) {
        LaunchedEffect(Unit) {
            delay(1500)
            showUpdateLoading = false
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "修改$dialogLabel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    OutlinedTextField(
                        value = dialogValue,
                        onValueChange = { dialogValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (dialogLabel == "登录页标题") {
                        Text(
                            text = "预览：${dialogValue.ifBlank { "欢迎使用智能晨检仪" }}",
                            fontSize = Dimens.TextSizeSmall,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDialogConfirm?.invoke(dialogValue)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                ) {
                    Text(text = "确定", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB))
                ) {
                    Text(text = "取消", color = Color.Black)
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
    preview: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$label：$value",
                fontSize = Dimens.TextSizeNormal,
                color = Color(0xFF111827)
            )
            Text(
                text = "修改",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeNormal,
                modifier = Modifier.clickable(onClick = onEdit)
            )
        }
        if (preview != null) {
            Text(
                text = "预览：$preview",
                fontSize = Dimens.TextSizeSmall,
                color = Color(0xFF6B7280)
            )
        }
    }
}
