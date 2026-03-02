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

    override suspend fun initialize(): Result<Unit> {
        return try {
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
