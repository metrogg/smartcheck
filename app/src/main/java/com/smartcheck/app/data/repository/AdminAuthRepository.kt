package com.smartcheck.app.data.repository

import android.content.Context
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.repository.IAdminAuthService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminAuthRepository @Inject constructor(
    @ApplicationContext context: Context
) : IAdminAuthService {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_LOGGED_IN, false))
    override fun observeLoginState(): Flow<Boolean> = _isLoggedIn.asStateFlow()

    private val _account = MutableStateFlow(prefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT)
    override fun observeAccount(): Flow<String> = _account.asStateFlow()

    init {
        if (!prefs.contains(KEY_PASSWORD_HASH)) {
            setPassword(DEFAULT_PASSWORD)
        }
        if (!prefs.contains(KEY_ACCOUNT)) {
            setAccount(DEFAULT_ACCOUNT)
        }
    }

    override suspend fun login(account: String, password: String): Result<String> {
        val storedAccount = prefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT
        if (!storedAccount.equals(account.trim(), ignoreCase = false)) {
            return Result.failure(AppError.Unauthorized)
        }
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(AppError.Unauthorized)
        val inputHash = sha256(password.trim())
        val ok = expectedHash.equals(inputHash, ignoreCase = true)
        if (ok) {
            prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply()
            _isLoggedIn.value = true
            return Result.success("token_$account")
        }
        return Result.failure(AppError.Unauthorized)
    }

    override suspend fun logout(): Result<Unit> {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        _isLoggedIn.value = false
        return Result.success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(AppError.Unauthorized)
        val inputHash = sha256(currentPassword.trim())
        if (!expectedHash.equals(inputHash, ignoreCase = true)) {
            return Result.failure(AppError.Unauthorized)
        }
        val hash = sha256(newPassword.trim())
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
        _isLoggedIn.value = false
        return Result.success(Unit)
    }

    override suspend fun changeAccount(newAccount: String, password: String): Result<Unit> {
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(AppError.Unauthorized)
        val inputHash = sha256(password.trim())
        if (!expectedHash.equals(inputHash, ignoreCase = true)) {
            return Result.failure(AppError.Unauthorized)
        }
        val trimmed = newAccount.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(AppError.ValidationError("account", "账号不能为空"))
        }
        prefs.edit()
            .putString(KEY_ACCOUNT, trimmed)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
        _account.value = trimmed
        _isLoggedIn.value = false
        return Result.success(Unit)
    }

    override fun getCurrentToken(): String? {
        return if (_isLoggedIn.value) "token_${_account.value}" else null
    }

    fun setPassword(newPassword: String) {
        val hash = sha256(newPassword.trim())
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
        _isLoggedIn.value = false
    }

    fun setAccount(newAccount: String) {
        val trimmed = newAccount.trim()
        if (trimmed.isEmpty()) return
        prefs.edit()
            .putString(KEY_ACCOUNT, trimmed)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
        _account.value = trimmed
        _isLoggedIn.value = false
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }
    }

    companion object {
        private const val PREF_NAME = "admin_auth"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_ACCOUNT = "account"

        const val DEFAULT_ACCOUNT = "admin"
        const val DEFAULT_PASSWORD = "123456"
    }
}
