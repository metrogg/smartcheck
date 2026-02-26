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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.viewmodel.AdminAuthViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel
import com.smartcheck.app.ui.theme.BrandGreen
import com.smartcheck.app.ui.theme.Dimens
import androidx.compose.ui.text.font.FontWeight
import com.smartcheck.app.data.repository.AdminAuthRepository

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

    val storedAccount by viewModel.account.collectAsState()
    val loginTitle by settingsViewModel.loginTitle.collectAsState()
    val loginBackground by settingsViewModel.loginBackground.collectAsState()
    val canteenName by settingsViewModel.canteenName.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.logout()
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
                    val ok = viewModel.login(account, password)
                    if (ok) {
                        onLoginSuccess()
                    } else {
                        error = "账号或密码错误"
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
        }
        }
    }
}
