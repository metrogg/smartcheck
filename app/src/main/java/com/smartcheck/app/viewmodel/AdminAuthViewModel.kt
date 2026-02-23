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

    fun login(password: String): Boolean = adminAuthRepository.login(password)

    fun logout() = adminAuthRepository.logout()

    fun setPassword(newPassword: String) = adminAuthRepository.setPassword(newPassword)
}
