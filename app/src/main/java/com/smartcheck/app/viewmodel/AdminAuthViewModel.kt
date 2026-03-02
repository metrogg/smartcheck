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

    init {
        viewModelScope.launch {
            adminAuthService.observeLoginState().collect { _isLoggedIn.value = it }
        }
        viewModelScope.launch {
            adminAuthService.observeAccount().collect { _account.value = it }
        }
    }

    fun login(account: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = adminAuthService.login(account, password)
            result.fold(
                onSuccess = { onResult(true) },
                onFailure = { onResult(false) }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            adminAuthService.logout()
        }
    }

    fun setPassword(newPassword: String) {
        viewModelScope.launch {
            adminAuthService.changePassword("123456", newPassword)
        }
    }

    fun setAccount(newAccount: String) {
        viewModelScope.launch {
            adminAuthService.changeAccount(newAccount, "123456")
        }
    }
}
