package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import com.smartcheck.app.data.repository.AdminAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AdminAuthViewModel @Inject constructor(
    private val adminAuthRepository: AdminAuthRepository
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = adminAuthRepository.isLoggedIn
    val account: StateFlow<String> = adminAuthRepository.account

    fun login(account: String, password: String): Boolean = adminAuthRepository.login(account, password)

    fun logout() = adminAuthRepository.logout()

    fun setPassword(newPassword: String) = adminAuthRepository.setPassword(newPassword)

    fun setAccount(newAccount: String) = adminAuthRepository.setAccount(newAccount)
}
