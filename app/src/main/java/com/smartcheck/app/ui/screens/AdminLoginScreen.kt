package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.app.viewmodel.AdminAuthViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.data.repository.AdminAuthRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    viewModel: AdminAuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPassword by remember { mutableStateOf(true) }
    
    // 激活相关
    var showActivationDialog by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("http://n629a758.natappfree.cc") }
    var activationCode by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }
    var isActivating by remember { mutableStateOf(false) }
    var activationSuccess by remember { mutableStateOf(false) }

    val storedAccount by viewModel.account.collectAsState()
    val loginTitle by settingsViewModel.loginTitle.collectAsState()
    val loginBackground by settingsViewModel.loginBackground.collectAsState()
    val canteenName by settingsViewModel.canteenName.collectAsState()
    val context = LocalContext.current
    
    val prefs = remember {
        context.getSharedPreferences("admin_auth", android.content.Context.MODE_PRIVATE)
    }
    
    LaunchedEffect(Unit) {
        viewModel.logout()
        // 加载记住的凭据
        val rememberedUsername = prefs.getString("remembered_username", null)
        val rememberedPassword = prefs.getString("remembered_password", null)
        if (rememberedUsername != null && rememberedPassword != null) {
            account = rememberedUsername
            password = rememberedPassword
            rememberPassword = true
        }
    }
    LaunchedEffect(storedAccount) {
        if (account.isBlank()) {
            account = if (storedAccount.isBlank()) AdminAuthRepository.DEFAULT_ACCOUNT else storedAccount
        }
        if (password.isBlank()) {
            password = AdminAuthRepository.DEFAULT_PASSWORD
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loginBackground.isNotBlank()) {
            AsyncImage(
                model = loginBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.88f))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(BrandGreen)
                .padding(Dimens.PaddingLarge),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                Text(
                    text = if (canteenName.isBlank()) "某某科技公司" else canteenName,
                    fontSize = Dimens.TextSizeNormal,
                    color = Color.White
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "当前版本：1.0.6",
                    fontSize = Dimens.TextSizeSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(Color.White),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (loginTitle.isBlank()) "欢迎使用智能晨检仪" else loginTitle,
                fontSize = Dimens.TextSizeTitle,
                fontWeight = FontWeight.Bold,
                color = BrandGreen
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            OutlinedTextField(
                value = account,
                onValueChange = {
                    account = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                label = { Text("账号", fontSize = Dimens.TextSizeNormal) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = Dimens.TextSizeNormal),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                label = { Text("密码", fontSize = Dimens.TextSizeNormal) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = Dimens.TextSizeNormal),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "隐藏" else "显示"
                        )
                    }
                },
                isError = error != null
            )
            if (activationSuccess) {
                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                Text(
                    text = "激活成功！",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = Dimens.TextSizeSmall
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = Dimens.TextSizeSmall
                )
            }
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            Button(
                onClick = {
                    viewModel.login(account, password) { result ->
                        result.fold(
                            onSuccess = { 
                                // 保存或清除凭据
                                if (rememberPassword) {
                                    prefs.edit()
                                        .putString("remembered_username", account)
                                        .putString("remembered_password", password)
                                        .apply()
                                } else {
                                    prefs.edit()
                                        .remove("remembered_username")
                                        .remove("remembered_password")
                                        .apply()
                                }
                                onLoginSuccess() 
                            },
                            onFailure = { error = it.message ?: "登录失败" }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(Dimens.ButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
            ) {
                Text("登录", fontSize = Dimens.TextSizeNormal)
            }
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            Row(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it }
                    )
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text(text = "记住密码", fontSize = Dimens.TextSizeSmall)
                }
                TextButton(onClick = { }) {
                    Text(text = "忘记密码？", fontSize = Dimens.TextSizeSmall, color = BrandGreen)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { showActivationDialog = true }) {
                Text(text = "激活设备", fontSize = Dimens.TextSizeSmall, color = BrandGreen)
            }
        }
        
        // 激活对话框
        if (showActivationDialog) {
            AlertDialog(
                onDismissRequest = { showActivationDialog = false },
                title = { Text("激活设备") },
                text = {
                    Column {
                        if (activationSuccess) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "✓",
                                        fontSize = 48.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "激活成功！",
                                        fontSize = Dimens.TextSizeTitle,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = { Text("激活服务器地址") },
                                placeholder = { Text("http://n629a758.natappfree.cc") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            OutlinedTextField(
                                value = activationCode,
                                onValueChange = { activationCode = it },
                                label = { Text("激活码") },
                                placeholder = { Text("请输入激活码") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (activationError != null) {
                                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                                Text(
                                    text = activationError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = Dimens.TextSizeSmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!activationSuccess) {
                        Button(
                            onClick = {
                                isActivating = true
                                activationError = null
                                DeviceAuth.setActivationServerUrl(serverUrl)
                                GlobalScope.launch {
                                    val result = DeviceAuth.activate(activationCode)
                                    isActivating = false
                                    result.fold(
                                        onSuccess = {
                                            activationSuccess = true
                                            kotlinx.coroutines.delay(1500)
                                            showActivationDialog = false
                                            activationSuccess = false
                                            error = null
                                        },
                                        onFailure = {
                                            activationError = it.message ?: "激活失败"
                                        }
                                    )
                                }
                            },
                            enabled = !isActivating && serverUrl.isNotBlank() && activationCode.isNotBlank()
                        ) {
                            Text(if (isActivating) "激活中..." else "确认")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showActivationDialog = false }) {
                        Text(if (activationSuccess) "完成" else "取消")
                    }
                }
            )
        }
        }
    }
}
