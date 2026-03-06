package com.smartcheck.app.data.repository

import com.smartcheck.app.data.serial.SerialPortManager
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.repository.ITemperatureService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemperatureServiceImpl @Inject constructor(
    private val serialPortManager: SerialPortManager
) : ITemperatureService {

    @Volatile
    private var initialized = false

    companion object {
        // 测温模块配置
        const val TEMP_DEVICE_PATH = "/dev/ttyS7"
        const val TEMP_BAUD_RATE = 115200
    }

    override suspend fun initialize(): Result<Unit> {
        return try {
            // 配置串口参数
            serialPortManager.configure(TEMP_DEVICE_PATH, TEMP_BAUD_RATE)
            
            // 打开串口
            val opened = serialPortManager.open()
            if (opened) {
                initialized = true
                Result.success(Unit)
            } else {
                Result.failure(AppError.HardwareError("serial", "Failed to open serial port"))
            }
        } catch (e: Exception) {
            Result.failure(AppError.HardwareError("serial", e.message ?: "unknown"))
        }
    }

    override suspend fun release(): Result<Unit> {
        return try {
            serialPortManager.close()
            initialized = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.HardwareError("serial", e.message ?: "unknown"))
        }
    }

    override fun isInitialized(): Boolean = initialized

    override fun observeTemperature(): Flow<Float> = serialPortManager.readTemperature()
}
