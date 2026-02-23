package com.smartcheck.app.data.repository

import com.smartcheck.app.data.serial.SerialPortManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 硬件仓库层
 * 
 * 对上层（ViewModel）提供统一的硬件访问接口
 */
@Singleton
class HardwareRepository @Inject constructor(
    private val serialPortManager: SerialPortManager
) {
    
    /**
     * 初始化硬件
     */
    fun init(): Boolean {
        Timber.d("Initializing hardware...")
        return serialPortManager.open()
    }
    
    /**
     * 获取温度数据流
     */
    fun getTemperatureFlow(): Flow<Float> = serialPortManager.readTemperature()
    
    /**
     * 蜂鸣提示
     * @param type 提示类型：success, warning, error
     */
    fun beep(type: String = "success") {
        val duration = when (type) {
            "success" -> 200
            "warning" -> 400
            "error" -> 600
            else -> 200
        }
        serialPortManager.beep(duration)
    }
    
    /**
     * 开门
     */
    fun openDoor() {
        Timber.d("Opening door...")
        serialPortManager.controlDoor(true)
        // 3 秒后自动关门
        CoroutineScope(Dispatchers.IO).launch {
            delay(3000)
            serialPortManager.controlDoor(false)
        }
    }
    
    /**
     * 释放硬件资源
     */
    fun release() {
        Timber.d("Releasing hardware...")
        serialPortManager.close()
    }
}
