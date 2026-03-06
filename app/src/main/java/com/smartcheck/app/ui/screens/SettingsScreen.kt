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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.app.utils.DeviceInfo
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.AdminAuthViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AdminAuthViewModel = hiltViewModel()
) {
    val adminName by viewModel.adminName.collectAsState()
    val account by viewModel.account.collectAsState()
    val canteenName by viewModel.canteenName.collectAsState()
    val loginTitle by viewModel.loginTitle.collectAsState()
    val loginBackground by viewModel.loginBackground.collectAsState()
    val adminAvatar by viewModel.adminAvatar.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val context = LocalContext.current

    val currentAccount by authViewModel.account.collectAsState()
    val currentRole by authViewModel.currentRole.collectAsState()
    
    val deviceId = remember { DeviceInfo.getDeviceId(context) }
    val deviceModel = remember { DeviceInfo.getDeviceModel() }
    val appVersion = remember { DeviceInfo.getAppVersion(context) }

    var dialogLabel by remember { mutableStateOf("") }
    var dialogValue by remember { mutableStateOf("") }
    var onDialogConfirm by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showUpdateLoading by remember { mutableStateOf(false) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showBackgroundMenu by remember { mutableStateOf(false) }
    
    // 密码修改对话框
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordLoading by remember { mutableStateOf(false) }

    fun openEdit(label: String, value: String, onConfirm: (String) -> Unit) {
        dialogLabel = label
        dialogValue = value
        onDialogConfirm = onConfirm
        showDialog = true
    }

    fun showPasswordChangeDialog() {
        oldPassword = ""
        newPassword = ""
        confirmPassword = ""
        passwordError = null
        showPasswordDialog = true
    }

    fun confirmPasswordChange() {
        passwordError = null
        if (oldPassword.isBlank()) {
            passwordError = "请输入原密码"
            return
        }
        if (newPassword.isBlank()) {
            passwordError = "请输入新密码"
            return
        }
        if (newPassword.length < 6) {
            passwordError = "新密码长度不能少于6位"
            return
        }
        if (newPassword != confirmPassword) {
            passwordError = "两次输入的密码不一致"
            return
        }
        
        passwordLoading = true
        authViewModel.changePassword(oldPassword, newPassword) { result ->
            passwordLoading = false
            result.fold(
                onSuccess = {
                    showPasswordDialog = false
                    oldPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                },
                onFailure = {
                    passwordError = it.message ?: "修改失败"
                }
            )
        }
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

                // 重置授权按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            DeviceAuth.clearActivation()
                            CoroutineScope(Dispatchers.Main).launch {
                                onLogout()
                            }
                        }
                        .padding(vertical = Dimens.PaddingNormal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "重置授权",
                        fontSize = Dimens.TextSizeNormal,
                        color = Color(0xFFDC2626)
                    )
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFDC2626)
                    )
                }

                // 当前登录用户信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                        .padding(Dimens.PaddingNormal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "当前登录: $currentAccount",
                            fontSize = Dimens.TextSizeNormal,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        if (currentRole != null) {
                            Text(
                                text = "角色: ${if (currentRole == "admin") "管理员" else "员工"}",
                                fontSize = Dimens.TextSizeSmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }

                // 设备信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                        .padding(Dimens.PaddingNormal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "设备SN",
                            fontSize = Dimens.TextSizeNormal,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            text = deviceId,
                            fontSize = Dimens.TextSizeSmall,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = deviceModel,
                            fontSize = Dimens.TextSizeSmall,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "v$appVersion",
                            fontSize = Dimens.TextSizeSmall,
                            color = Color(0xFF6B7280)
                        )
                    }
                }

                // 退出登录按钮
                Button(
                    onClick = {
                        authViewModel.logout()
                        CoroutineScope(Dispatchers.Main).launch {
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(text = "退出登录", color = Color.White, fontSize = Dimens.TextSizeNormal)
                }

                Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

                SettingRow(
                    label = "管理员姓名",
                    value = if (adminName.isBlank()) "赵某某" else adminName,
                    onEdit = { openEdit("管理员姓名", adminName) { viewModel.setAdminName(it) } }
                )
                SettingRow(
                    label = "当前账号",
                    value = currentAccount,
                    onEdit = null
                )
                SettingRow(
                    label = "修改密码",
                    value = "******",
                    onEdit = { showPasswordChangeDialog() }
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

    // 密码修改对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(text = "修改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it; passwordError = null },
                        label = { Text("原密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; passwordError = null },
                        label = { Text("新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; passwordError = null },
                        label = { Text("确认新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = Dimens.TextSizeSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmPasswordChange() },
                    enabled = !passwordLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                ) {
                    if (passwordLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    }
                    Text(text = "确定", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showPasswordDialog = false },
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
    onEdit: (() -> Unit)? = null,
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
            if (onEdit != null) {
                Text(
                    text = "修改",
                    color = Color(0xFF2563EB),
                    fontSize = Dimens.TextSizeNormal,
                    modifier = Modifier.clickable(onClick = onEdit)
                )
            }
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
