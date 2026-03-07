package com.smartcheck.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _voiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ENABLED, true))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _adminName = MutableStateFlow(prefs.getString(KEY_ADMIN_NAME, "") ?: "")
    val adminName: StateFlow<String> = _adminName.asStateFlow()

    private val _account = MutableStateFlow(prefs.getString(KEY_ACCOUNT, "") ?: "")
    val account: StateFlow<String> = _account.asStateFlow()

    private val _canteenName = MutableStateFlow(prefs.getString(KEY_CANTEEN_NAME, "") ?: "")
    val canteenName: StateFlow<String> = _canteenName.asStateFlow()

    private val _loginTitle = MutableStateFlow(prefs.getString(KEY_LOGIN_TITLE, "") ?: "")
    val loginTitle: StateFlow<String> = _loginTitle.asStateFlow()

    private val _loginBackground = MutableStateFlow(prefs.getString(KEY_LOGIN_BG, "") ?: "")
    val loginBackground: StateFlow<String> = _loginBackground.asStateFlow()

    private val _adminAvatar = MutableStateFlow(prefs.getString(KEY_ADMIN_AVATAR, "") ?: "")
    val adminAvatar: StateFlow<String> = _adminAvatar.asStateFlow()

    private val _deviceSn = MutableStateFlow(prefs.getString(KEY_DEVICE_SN, "") ?: "")
    val deviceSn: StateFlow<String> = _deviceSn.asStateFlow()

    fun setVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
        _voiceEnabled.value = enabled
    }

    fun setAdminName(value: String) {
        prefs.edit().putString(KEY_ADMIN_NAME, value).apply()
        _adminName.value = value
    }

    fun setAccount(value: String) {
        prefs.edit().putString(KEY_ACCOUNT, value).apply()
        _account.value = value
    }

    fun setCanteenName(value: String) {
        prefs.edit().putString(KEY_CANTEEN_NAME, value).apply()
        _canteenName.value = value
    }

    fun setLoginTitle(value: String) {
        prefs.edit().putString(KEY_LOGIN_TITLE, value).apply()
        _loginTitle.value = value
    }

    fun setLoginBackground(value: String) {
        prefs.edit().putString(KEY_LOGIN_BG, value).apply()
        _loginBackground.value = value
    }

    fun setAdminAvatar(value: String) {
        prefs.edit().putString(KEY_ADMIN_AVATAR, value).apply()
        _adminAvatar.value = value
    }

    fun setDeviceSn(value: String) {
        prefs.edit().putString(KEY_DEVICE_SN, value).apply()
        _deviceSn.value = value
    }

    fun isVoiceEnabled(): Boolean = _voiceEnabled.value

    fun getDeviceSn(): String = _deviceSn.value

    companion object {
        private const val PREF_NAME = "smartcheck_settings"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_ADMIN_NAME = "admin_name"
        private const val KEY_ACCOUNT = "admin_account"
        private const val KEY_CANTEEN_NAME = "canteen_name"
        private const val KEY_LOGIN_TITLE = "login_title"
        private const val KEY_LOGIN_BG = "login_background"
        private const val KEY_ADMIN_AVATAR = "admin_avatar"
        private const val KEY_DEVICE_SN = "device_sn"
    }
}
