package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.smartcheck.app.data.repository.AdminAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.data.repository.RecordRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val adminAuthRepository: AdminAuthRepository,
    private val recordRepository: RecordRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _account = MutableStateFlow("admin")
    val account: StateFlow<String> = _account.asStateFlow()

    init {
        viewModelScope.launch {
            adminAuthRepository.observeCurrentUsername().collect { value ->
                if (value != null) {
                    _account.value = value
                }
            }
        }
    }

    val voiceEnabled: StateFlow<Boolean> = settingsRepository.voiceEnabled
    val adminName: StateFlow<String> = settingsRepository.adminName
    
    val canteenName: StateFlow<String> = settingsRepository.canteenName
    val loginTitle: StateFlow<String> = settingsRepository.loginTitle
    val loginBackground: StateFlow<String> = settingsRepository.loginBackground
    val adminAvatar: StateFlow<String> = settingsRepository.adminAvatar
    val deviceSn: StateFlow<String> = settingsRepository.deviceSn

    fun setVoiceEnabled(enabled: Boolean) {
        settingsRepository.setVoiceEnabled(enabled)
    }

    fun setAdminName(value: String) = settingsRepository.setAdminName(value)

    fun setCanteenName(value: String) = settingsRepository.setCanteenName(value)

    fun setLoginTitle(value: String) = settingsRepository.setLoginTitle(value)

    fun setLoginBackground(value: String) = settingsRepository.setLoginBackground(value)

    fun setAdminAvatar(value: String) = settingsRepository.setAdminAvatar(value)

    fun setDeviceSn(value: String) = settingsRepository.setDeviceSn(value)

    fun setAccount(value: String) {
        settingsRepository.setAccount(value)
        adminAuthRepository.setAccount(value)
    }

    fun setPassword(value: String) = adminAuthRepository.setPassword(value)

    fun clearRecordImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = FileUtil.getRecordsDir(appContext)
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            recordRepository.deleteAllRecords()
        }
    }
}
