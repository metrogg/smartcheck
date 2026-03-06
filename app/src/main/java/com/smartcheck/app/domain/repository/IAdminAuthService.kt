package com.smartcheck.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface IAdminAuthService {

    fun observeLoginState(): Flow<Boolean>

    fun observeAccount(): Flow<String>

    fun observeCurrentUsername(): Flow<String?>

    fun getDefaultAccount(): String

    suspend fun login(account: String, password: String): Result<String>

    suspend fun logout(): Result<Unit>

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    suspend fun changeAccount(newAccount: String, password: String): Result<Unit>

    fun getCurrentToken(): String?
}
