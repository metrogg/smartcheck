package com.smartcheck.app.domain.usecase

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

object SystemMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _systemState = MutableStateFlow(SystemState())
    val systemState: StateFlow<SystemState> = _systemState.asStateFlow()

    private var isMonitoring = false

    fun startMonitoring(context: Context, intervalMs: Long = 60000) {
        if (isMonitoring) return
        isMonitoring = true

        scope.launch {
            while (isActive) {
                performHealthCheck(context)
                delay(intervalMs)
            }
        }

        Timber.tag("SystemMonitor").d("System monitoring started")
    }

    fun stopMonitoring() {
        isMonitoring = false
        Timber.tag("SystemMonitor").d("System monitoring stopped")
    }

    fun performHealthCheck(context: Context): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsage = usedMemory.toFloat() / maxMemory

        _systemState.value = _systemState.value.copy(memoryUsage = memoryUsage)

        if (memoryUsage > 0.8f) {
            issues.add(HealthIssue(
                type = IssueType.MEMORY_HIGH,
                severity = Severity.WARNING,
                message = "Memory usage ${(memoryUsage * 100).toInt()}%"
            ))
            Timber.tag("SystemMonitor")
                .w("Memory usage HIGH: ${(memoryUsage * 100).toInt()}%")
        }

        val availableStorage = getAvailableStorage()
        _systemState.value = _systemState.value.copy(availableStorage = availableStorage)

        if (availableStorage < 100 * 1024 * 1024) {
            issues.add(HealthIssue(
                type = IssueType.STORAGE_LOW,
                severity = Severity.ERROR,
                message = "Storage low: ${formatSize(availableStorage)}"
            ))
            Timber.tag("SystemMonitor")
                .e("Storage LOW: ${formatSize(availableStorage)}")
        }

        _systemState.value = _systemState.value.copy(lastHealthCheck = System.currentTimeMillis())

        return issues
    }

    fun updateAiModelState(loaded: Boolean) {
        _systemState.value = _systemState.value.copy(aiModelLoaded = loaded)
        Timber.tag("SystemMonitor")
            .d("AI Model state: ${if (loaded) "loaded" else "not loaded"}")
    }

    fun updateCameraState(ready: Boolean) {
        _systemState.value = _systemState.value.copy(cameraReady = ready)
        Timber.tag("SystemMonitor")
            .d("Camera state: ${if (ready) "ready" else "not ready"}")
    }

    fun updateTemperatureState(ready: Boolean) {
        _systemState.value = _systemState.value.copy(temperatureReady = ready)
        Timber.tag("SystemMonitor")
            .d("Temperature module state: ${if (ready) "ready" else "not ready"}")
    }

    fun updateRecordCount(count: Int) {
        _systemState.value = _systemState.value.copy(recordCount = count)
    }

    private fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

data class SystemState(
    val memoryUsage: Float = 0f,
    val availableStorage: Long = 0L,
    val recordCount: Int = 0,
    val aiModelLoaded: Boolean = false,
    val cameraReady: Boolean = false,
    val temperatureReady: Boolean = false,
    val lastHealthCheck: Long = 0L
)

data class HealthIssue(
    val type: IssueType,
    val severity: Severity,
    val message: String
)

enum class IssueType {
    MEMORY_HIGH,
    STORAGE_LOW,
    DATABASE_LARGE,
    AI_MODEL_FAILED,
    CAMERA_UNAVAILABLE,
    TEMPERATURE_UNAVAILABLE
}

enum class Severity {
    INFO,
    WARNING,
    ERROR
}
