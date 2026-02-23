package com.smartcheck.app.data.serial

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 串口管理器
 * 
 * 负责与硬件设备通信：
 * - 红外测温模块
 * - 蜂鸣器
 * - 继电器（开门）
 * 
 * TODO: 接入真实串口库（如 android-serialport-api）
 * 当前为 Mock 实现
 */
@Singleton
class SerialPortManager @Inject constructor() {
    
    private var isOpen = false
    
    /**
     * 打开串口
     * @param portPath 串口路径，如 "/dev/ttyS1"
     * @param baudRate 波特率
     */
    fun open(portPath: String = "/dev/ttyS1", baudRate: Int = 9600): Boolean {
        Timber.d("Opening serial port: $portPath, baudRate: $baudRate")
        // TODO: 实际打开串口
        isOpen = true
        return true
    }
    
    /**
     * 关闭串口
     */
    fun close() {
        Timber.d("Closing serial port")
        isOpen = false
    }
    
    /**
     * 读取温度数据流
     * @return 温度值 Flow（单位：摄氏度）
     */
    fun readTemperature(): Flow<Float> = flow {
        if (!isOpen) {
            Timber.w("Serial port not open")
            return@flow
        }
        
        // TODO: 实际从串口读取温度数据
        // 当前模拟：每秒生成一个随机温度值
        while (isOpen) {
            val temp = 36.0f + (Math.random() * 1.5f).toFloat()
            emit(temp)
            kotlinx.coroutines.delay(1000)
        }
    }
    
    /**
     * 发送蜂鸣器指令
     * @param durationMs 蜂鸣时长（毫秒）
     */
    fun beep(durationMs: Int = 200) {
        if (!isOpen) {
            Timber.w("Serial port not open")
            return
        }
        
        Timber.d("Beep: ${durationMs}ms")
        // TODO: 发送实际串口指令
        // 示例：sendCommand(byteArrayOf(0x01, 0x02, ...))
    }
    
    /**
     * 控制继电器（开门）
     * @param open true=开门, false=关门
     */
    fun controlDoor(open: Boolean) {
        if (!isOpen) {
            Timber.w("Serial port not open")
            return
        }
        
        Timber.d("Door: ${if (open) "OPEN" else "CLOSE"}")
        // TODO: 发送实际串口指令
    }
    
    /**
     * 发送原始指令
     */
    private fun sendCommand(data: ByteArray) {
        // TODO: 实际写入串口
        Timber.d("Send command: ${data.joinToString(" ") { "%02X".format(it) }}")
    }
}
