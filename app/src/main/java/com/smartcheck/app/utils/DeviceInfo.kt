package com.smartcheck.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import timber.log.Timber

object DeviceInfo {

    fun getDeviceId(context: Context): String {
        return try {
            // 优先使用 Android ID
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrEmpty()) {
                Timber.d("[DeviceInfo] Android ID: $androidId")
                return androidId
            }
            
            // 备选：序列号
            val serial = Build.SERIAL
            if (!serial.isNullOrEmpty() && serial != "unknown") {
                Timber.d("[DeviceInfo] Serial: $serial")
                return serial
            }
            
            // 最后：随机 UUID（不应该走到这里）
            Timber.w("[DeviceInfo] 无法获取设备ID，使用随机ID")
            java.util.UUID.randomUUID().toString()
        } catch (e: Exception) {
            Timber.e(e, "[DeviceInfo] 获取设备ID失败")
            java.util.UUID.randomUUID().toString()
        }
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
