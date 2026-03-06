package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.repository.IAdminAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminAuthViewModel @Inject constructor(
    private val adminAuthService: IAdminAuthService
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _account = MutableStateFlow("admin")
    val account: StateFlow<String> = _account.asStateFlow()

    private val _currentRole = MutableStateFlow<String?>(null)
    val currentRole: StateFlow<String?> = _currentRole.asStateFlow()

    init {
        viewModelScope.launch {
            adminAuthService.observeLoginState().collect { _isLoggedIn.value = it }
        }
        viewModelScope.launch {
            // 优先使用当前登录用户名，如果为 null 则使用 SharedPrefs 中的账号
            adminAuthService.observeCurrentUsername().collect { username ->
                _account.value = username ?: adminAuthService.getDefaultAccount()
            }
        }
    }

    fun setCurrentRole(role: String?) {
        _currentRole.value = role
    }

    fun login(account: String, password: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = adminAuthService.login(account, password)
            result.fold(
                onSuccess = { 
                    // 登录成功后设置角色
                    val role = if (account == "admin") "admin" else "employee"
                    _currentRole.value = role
                    onResult(Result.success("登录成功")) 
                },
                onFailure = { 
                    val errorMsg = it.message ?: "登录失败"
                    onResult(Result.failure(Exception(errorMsg)))
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            adminAuthService.logout()
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = adminAuthService.changePassword(currentPassword, newPassword)
            result.fold(
                onSuccess = { onResult(Result.success(Unit)) },
                onFailure = { 
                    val errorMsg = it.message ?: "修改失败"
                    onResult(Result.failure(Exception(errorMsg)))
                }
            )
        }
    }

    fun changeAccount(newAccount: String, password: String) {
        viewModelScope.launch {
            adminAuthService.changeAccount(newAccount, password)
        }
    }
}
