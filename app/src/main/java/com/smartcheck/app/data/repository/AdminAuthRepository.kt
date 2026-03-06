package com.smartcheck.app.data.repository

import android.content.Context
import com.smartcheck.app.data.db.SystemUserDao
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.repository.IAdminAuthService
import com.smartcheck.app.utils.DeviceAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : IAdminAuthService {

    private var _systemUserDao: SystemUserDao? = null
    private val systemUserDao: SystemUserDao
        get() {
            if (_systemUserDao == null) {
                val db = androidx.room.Room.databaseBuilder(
                    context,
                    com.smartcheck.app.data.db.AppDatabase::class.java,
                    "smartcheck_db"
                ).build()
                _systemUserDao = db.systemUserDao()
            }
            return _systemUserDao!!
        }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _currentUserId = MutableStateFlow<Long?>(null)
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _currentRole = MutableStateFlow<String?>(null)
    val currentRole: StateFlow<String?> = _currentRole.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_LOGGED_IN, false))
    override fun observeLoginState(): Flow<Boolean> = _isLoggedIn.asStateFlow()

    private val _account = MutableStateFlow(prefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT)
    override fun observeAccount(): Flow<String> = _account.asStateFlow()

    override fun observeCurrentUsername(): Flow<String?> = _currentUsername.asStateFlow()

    override fun getDefaultAccount(): String {
        return prefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT
    }

    init {
        if (!prefs.contains(KEY_PASSWORD_HASH)) {
            setPassword(DEFAULT_PASSWORD)
        }
        if (!prefs.contains(KEY_ACCOUNT)) {
            setAccount(DEFAULT_ACCOUNT)
        }
    }

    override suspend fun login(account: String, password: String): Result<String> {
        // 检查是否已激活
        if (!DeviceAuth.isActivated()) {
            Timber.d("[AdminAuth] 设备未激活")
            return Result.failure(AppError.Unauthorized("请先激活设备"))
        } else {
            Timber.d("[AdminAuth] 设备已激活")
        }

        // 优先从 system_users 表验证（API同步的账号）
        val systemUser = systemUserDao.getActiveUserByUsername(account.trim())
        if (systemUser != null) {
            val inputHash = sha256(password.trim())
            val passwordMatch = when (systemUser.passwordType) {
                "plain" -> systemUser.passwordHash == password.trim()
                "md5" -> systemUser.passwordHash.equals(inputHash, ignoreCase = true)
                "bcrypt" -> systemUser.passwordHash == password.trim() // 简化处理
                else -> systemUser.passwordHash == password.trim()
            }
            if (passwordMatch) {
                prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply()
                _isLoggedIn.value = true
                _currentUserId.value = systemUser.id
                _currentUsername.value = systemUser.username
                _currentRole.value = systemUser.role
                return Result.success("token_${systemUser.username}")
            }
            return Result.failure(AppError.Unauthorized("密码错误"))
        }

        // 回退到 SharedPreferences 中的 admin 账号
        val storedAccount = prefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT
        if (!storedAccount.equals(account.trim(), ignoreCase = false)) {
            return Result.failure(AppError.Unauthorized("账号错误"))
        }
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(AppError.Unauthorized("密码未设置"))
        val inputHash = sha256(password.trim())
        val ok = expectedHash.equals(inputHash, ignoreCase = true)
        if (ok) {
            prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply()
            _isLoggedIn.value = true
            _currentUserId.value = 1L
            _currentUsername.value = storedAccount
            _currentRole.value = "admin"
            return Result.success("token_$account")
        }
        return Result.failure(AppError.Unauthorized("密码错误"))
    }

    override suspend fun logout(): Result<Unit> {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        _isLoggedIn.value = false
        _currentUserId.value = null
        _currentUsername.value = null
        _currentRole.value = null
        return Result.success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val currentUser = _currentUsername.value ?: return Result.failure(AppError.Unauthorized("未登录"))

        // 优先从 system_users 表修改
        val systemUser = systemUserDao.getUserByUsername(currentUser)
        if (systemUser != null) {
            // 验证原密码
            val inputHash = sha256(currentPassword.trim())
            val passwordMatch = when (systemUser.passwordType) {
                "plain" -> systemUser.passwordHash == currentPassword.trim()
                "md5" -> systemUser.passwordHash.equals(inputHash, ignoreCase = true)
                else -> systemUser.passwordHash == currentPassword.trim()
            }
            if (!passwordMatch) {
                return Result.failure(AppError.Unauthorized("原密码错误"))
            }
            // 更新密码
            val newHash = sha256(newPassword.trim())
            val updated = systemUser.copy(passwordHash = newHash, passwordType = "md5", updatedAt = System.currentTimeMillis())
            systemUserDao.updateUser(updated)
            return Result.success(Unit)
        }

        // 回退到 SharedPreferences 中的 admin 账号
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(AppError.Unauthorized("无法验证"))
        val inputHash = sha256(currentPassword.trim())
        if (!expectedHash.equals(inputHash, ignoreCase = true)) {
            return Result.failure(AppError.Unauthorized("原密码错误"))
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
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(AppError.Unauthorized("无法验证"))
        val inputHash = sha256(password.trim())
        if (!expectedHash.equals(inputHash, ignoreCase = true)) {
            return Result.failure(AppError.Unauthorized("密码错误"))
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

    suspend fun verifyPassword(username: String, password: String): Boolean {
        // 优先从 system_users 表验证
        val systemUser = systemUserDao.getActiveUserByUsername(username.trim())
        if (systemUser != null) {
            val inputHash = sha256(password.trim())
            return when (systemUser.passwordType) {
                "plain" -> systemUser.passwordHash == password.trim()
                "md5" -> systemUser.passwordHash.equals(inputHash, ignoreCase = true)
                "bcrypt" -> systemUser.passwordHash == password.trim()
                else -> systemUser.passwordHash == password.trim()
            }
        }

        // 回退到 SharedPreferences 中的 admin 账号
        val storedAccount = prefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT
        if (!storedAccount.equals(username.trim(), ignoreCase = false)) {
            return false
        }
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val inputHash = sha256(password.trim())
        return expectedHash.equals(inputHash, ignoreCase = true)
    }

    fun getCurrentUserRole(): String? = _currentRole.value

    fun saveRememberedCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_REMEMBERED_USERNAME, username)
            .putString(KEY_REMEMBERED_PASSWORD, password)
            .apply()
    }

    fun getRememberedCredentials(): Pair<String, String>? {
        val username = prefs.getString(KEY_REMEMBERED_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_REMEMBERED_PASSWORD, null) ?: return null
        return Pair(username, password)
    }

    fun clearRememberedCredentials() {
        prefs.edit()
            .remove(KEY_REMEMBERED_USERNAME)
            .remove(KEY_REMEMBERED_PASSWORD)
            .apply()
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
        private const val KEY_REMEMBERED_USERNAME = "remembered_username"
        private const val KEY_REMEMBERED_PASSWORD = "remembered_password"

        const val DEFAULT_ACCOUNT = "admin"
        // 默认密码
        const val DEFAULT_PASSWORD = "123456"
    }
}
