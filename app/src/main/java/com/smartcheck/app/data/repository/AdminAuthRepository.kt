package com.smartcheck.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminAuthRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_LOGGED_IN, false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        if (!prefs.contains(KEY_PASSWORD_HASH)) {
            setPassword(DEFAULT_PASSWORD)
        }
    }

    fun login(password: String): Boolean {
        val expectedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val inputHash = sha256(password.trim())
        val ok = expectedHash.equals(inputHash, ignoreCase = true)
        if (ok) {
            prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply()
            _isLoggedIn.value = true
        }
        return ok
    }

    fun logout() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        _isLoggedIn.value = false
    }

    fun setPassword(newPassword: String) {
        val hash = sha256(newPassword.trim())
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
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

        const val DEFAULT_PASSWORD = "123456"
    }
}
