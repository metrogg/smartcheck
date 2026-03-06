package com.smartcheck.app.data.upload

import android.content.Context
import android.util.Base64
import com.smartcheck.app.api.model.CloudCheckRecordRequest
import com.smartcheck.app.api.model.CloudCheckRecordResponse
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRecordService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL = "http://api.qhk12.iyouxin.cn:50082"
        private const val ENDPOINT = "/kitchen/morningCheck/saveData"
    }

    suspend fun uploadCheckRecord(record: Record, deviceSn: String): Result<CloudCheckRecordResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("=== Cloud Record Upload Start ===")
                Timber.d("Record: personCode=${record.employeeId}, personName=${record.userName}, temp=${record.temperature}, isPassed=${record.isPassed}")
                Timber.d("Device SN: $deviceSn")
                
                val deviceIp = getDeviceIp() ?: ""
                Timber.d("Device IP: $deviceIp")
                
                val facePhoto = getImageBase64(record.faceImagePath)
                val handPalmPhoto = getImageBase64(record.handPalmPath)
                val handBackPhoto = getImageBase64(record.handBackPath)
                Timber.d("Image sizes - face: ${facePhoto.length}, palm: ${handPalmPhoto.length}, back: ${handBackPhoto.length}")

                val temperatureType = if (record.isTempNormal) 0 else 1
                val result = if (record.isPassed) "1" else "0"
                Timber.d("temperatureType=$temperatureType, result=$result")

                val request = CloudCheckRecordRequest(
                    deviceIp = deviceIp,
                    deviceSn = deviceSn,
                    personCode = record.employeeId,
                    personName = record.userName,
                    photo = facePhoto,
                    timestamp = record.checkTime,
                    verificationMode = 9,
                    temperature = record.temperature.toString(),
                    temperatureType = temperatureType,
                    result = result,
                    handPalmPhoto = handPalmPhoto,
                    handBackPhoto = handBackPhoto,
                    recognitionType = 1,
                    livenessType = 1,
                    maskType = 1,
                    healthyState = 0,
                    passType = 1,
                    serverVerify = "0",
                    verificationType = 0
                )

                Timber.d("Sending POST request to: $BASE_URL$ENDPOINT")

                val response = httpClient.post("$BASE_URL$ENDPOINT") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                Timber.d("Response status: ${response.status}")

                if (response.status.isSuccess()) {
                    // 打印原始响应
                    val rawBody: String = response.body()
                    Timber.d("Raw response body: $rawBody")
                    
                    val responseBody = response.body<CloudCheckRecordResponse>()
                    Timber.d("Response body: code=${responseBody.code}, isSuccess=${responseBody.isSuccess}, message=${responseBody.message}")
                    
                    if (responseBody.isSuccess) {
                        Timber.d("=== Cloud Record Upload SUCCESS ===")
                        Result.success(responseBody)
                    } else {
                        Timber.e("=== Cloud Record Upload FAILED: ${responseBody.message} ===")
                        Result.failure(Exception(responseBody.message))
                    }
                } else {
                    Timber.e("=== Cloud Record Upload HTTP ERROR: ${response.status} ===")
                    Result.failure(Exception("HTTP ${response.status}"))
                }
            } catch (e: java.util.concurrent.CancellationException) {
                Timber.w("Cloud record upload cancelled (ViewModel destroyed)")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "=== Cloud Record Upload EXCEPTION ===")
                Result.failure(e)
            }
        }
    }

    private fun getImageBase64(imagePath: String?): String {
        if (imagePath.isNullOrBlank()) return ""
        return try {
            val bitmap = FileUtil.loadBitmapFromInternal(context, imagePath)
            if (bitmap != null) {
                val bos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, bos)
                val bytes = bos.toByteArray()
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else {
                ""
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to convert image to base64: $imagePath")
            ""
        }
    }

    private fun getDeviceIp(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val ip = address.hostAddress
                        if (ip.contains(".")) {
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to get device IP")
            null
        }
    }
}
