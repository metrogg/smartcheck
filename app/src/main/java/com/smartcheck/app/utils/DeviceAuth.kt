package com.smartcheck.app.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

object DeviceAuth {

    private const val PREFS_NAME = "device_auth"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_ACTIVATED_TIME = "activated_time"
    private const val KEY_ACTIVATION_SERVER_URL = "activation_server_url"

    private var activationServerUrl: String = ""

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        activationServerUrl = prefs.getString(KEY_ACTIVATION_SERVER_URL, "") ?: ""
    }

    fun setActivationServerUrl(url: String) {
        activationServerUrl = url
        prefs.edit().putString(KEY_ACTIVATION_SERVER_URL, url).apply()
        Timber.d("[DeviceAuth] 激活服务器地址: $url")
    }

    fun getActivationServerUrl(): String = activationServerUrl

    /**
     * 检查是否已激活
     */
    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_ACTIVATED, false)
    }

    /**
     * 获取激活时间
     */
    fun getActivatedTime(): Long {
        return prefs.getLong(KEY_ACTIVATED_TIME, 0)
    }

    /**
     * 请求激活
     */
    suspend fun activate(activationCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("[DeviceAuth] === 开始激活流程 ===")
        
        if (activationServerUrl.isEmpty()) {
            Timber.e("[DeviceAuth] 激活服务器地址未设置")
            return@withContext Result.failure(Exception("请先设置激活服务器地址"))
        }

        Timber.d("[DeviceAuth] 服务器地址: $activationServerUrl")
        Timber.d("[DeviceAuth] 请求激活: code=$activationCode")
        
        try {
            Timber.d("[DeviceAuth] 正在连接服务器...")

            val json = """
                {
                    "activationCode": "$activationCode"
                }
            """.trimIndent()

            // 确保 URL 以 / 结尾，然后添加路径
            val baseUrl = activationServerUrl.trimEnd('/')
            val fullUrl = "$baseUrl/api/device/activate"
            Timber.d("[DeviceAuth] 完整URL: $fullUrl")
            
            val url = URL(fullUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.outputStream.use { output ->
                output.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            Timber.d("[DeviceAuth] 响应码: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Timber.d("[DeviceAuth] 响应: $response")

                // 解析响应
                // 解析 JSON 响应
                try {
                    val json = org.json.JSONObject(response)
                    val code = json.optInt("code", -1)
                    val activated = json.optJSONObject("data")?.optBoolean("activated", false) ?: false
                    
                    if (code == 0 || activated) {
                        saveActivation()
                        return@withContext Result.success(true)
                    }
                    
                    val errorMsg = json.optString("message", "激活失败")
                    Timber.w("[DeviceAuth] 激活失败: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                } catch (e: Exception) {
                    Timber.e(e, "[DeviceAuth] 解析响应失败: $response")
                    return@withContext Result.failure(Exception("响应解析失败"))
                }
            }

            Timber.w("[DeviceAuth] 激活失败: $responseCode")
            Result.failure(Exception("激活失败: $responseCode"))
        } catch (e: Exception) {
            Timber.e(e, "[DeviceAuth] 激活异常")
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    private fun parseErrorMessage(response: String): String? {
        return try {
            // 简单解析 message 字段
            val regex = """\"message\"\s*:\s*\"([^\"]+)\"""".toRegex()
            regex.find(response)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveActivation() {
        prefs.edit().apply {
            putBoolean(KEY_ACTIVATED, true)
            putLong(KEY_ACTIVATED_TIME, System.currentTimeMillis())
            apply()
        }
        Timber.d("[DeviceAuth] 激活状态已保存")
    }

    /**
     * 清除激活状态（用于重置）
     */
    fun clearActivation() {
        prefs.edit().clear().apply()
        Timber.d("[DeviceAuth] 激活状态已清除")
    }
}
