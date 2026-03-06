package com.smartcheck.app.data.serial

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 串口管理器
 * 
 * 负责与硬件设备通信：
 * - 红外测温模块（/dev/ttyS7, 115200）
 * - 蜂鸣器
 * - 继电器（开门）
 * 
 * 协议说明：
 * - 上电后模块每 300ms 左右发送一次人体温度数据
 * - 输出格式：{36.53}（以"{"开头，"}"结尾）
 * - 温度以字符形式显示
 */
@Singleton
class SerialPortManager @Inject constructor() {
    
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var isOpen = false

    // 串口配置
    companion object {
        const val TAG = "SerialPortManager"
        const val DEFAULT_DEVICE_PATH = "/dev/ttyS7"
        const val DEFAULT_BAUD_RATE = 115200
    }

    private var currentDevicePath = DEFAULT_DEVICE_PATH
    private var currentBaudRate = DEFAULT_BAUD_RATE
    
    private val stringBuffer = StringBuilder()

    /**
     * 配置串口参数
     */
    fun configure(path: String, baudRate: Int) {
        currentDevicePath = path
        currentBaudRate = baudRate
        Timber.d(TAG, "Serial port configured: $path @ $baudRate")
    }

    /**
     * 打开串口
     * @param portPath 串口路径，如 "/dev/ttyS7"
     * @param baudRate 波特率
     */
    fun open(portPath: String = currentDevicePath, baudRate: Int = currentBaudRate): Boolean {
        if (isOpen) {
            Timber.d(TAG, "Serial port already open")
            return true
        }

        try {
            Timber.d(TAG, "Opening serial port: $portPath, baudRate: $baudRate")
            
            val deviceFile = File(portPath)
            if (!deviceFile.exists()) {
                Timber.e(TAG, "Serial port device not found: $portPath")
                return false
            }
            
            // 以读写方式打开串口设备
            inputStream = FileInputStream(deviceFile)
            outputStream = FileOutputStream(deviceFile)
            
            // 配置串口参数（需要 native 方法或 shell 命令）
            configureSerialPort(portPath, baudRate)
            
            isOpen = true
            
            Timber.i(TAG, "Serial port opened successfully: $portPath")
            return true
        } catch (e: Exception) {
            Timber.e(TAG, "Failed to open serial port: ${e.message}")
            close()
            return false
        }
    }
    
    /**
     * 配置串口参数（通过 stty 命令）
     */
    private fun configureSerialPort(portPath: String, baudRate: Int) {
        try {
            // 使用 stty 命令配置串口参数
            val process = Runtime.getRuntime().exec(arrayOf(
                "stty",
                "-F", portPath,
                "$baudRate",
                "cs8",
                "cstopb",
                "-parenb",
                "-echo"
            ))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Timber.d(TAG, "Serial port configured successfully: $baudRate baud")
            } else {
                Timber.w(TAG, "stty command failed with exit code: $exitCode")
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to configure serial port with stty: ${e.message}")
        }
    }
    
    /**
     * 关闭串口
     */
    fun close() {
        Timber.d(TAG, "Closing serial port")
        try {
            isOpen = false
            inputStream?.close()
            outputStream?.close()
            inputStream = null
            outputStream = null
            Timber.i(TAG, "Serial port closed")
        } catch (e: Exception) {
            Timber.e(TAG, "Error closing serial port: ${e.message}")
        }
    }

    /**
     * 获取可用的串口设备列表
     */
    fun getAvailableDevices(): List<String> {
        return try {
            val devices = mutableListOf<String>()
            val devDir = File("/dev")
            devDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("tty") && (file.name.contains("S") || file.name.contains("USB"))) {
                    devices.add(file.absolutePath)
                }
            }
            devices
        } catch (e: Exception) {
            Timber.e(TAG, "Failed to get available devices: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 读取温度数据流
     * @return 温度值 Flow（单位：摄氏度）
     * 
     * 协议格式：{36.53}
     * - 以"{"开头，"}"结尾
     * - 温度值范围：32.00 ~ 45.00
     */
    fun readTemperature(): Flow<Float> = flow {
        if (!isOpen || inputStream == null) {
            Timber.w(TAG, "Serial port not open")
            return@flow
        }

        val buffer = ByteArray(256)
        
        while (isOpen && inputStream != null) {
            try {
                // 检查是否有数据可读
                val available = inputStream?.available() ?: 0
                
                if (available > 0) {
                    val bytesRead = inputStream?.read(buffer) ?: 0
                    
                    if (bytesRead > 0) {
                        val data = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        Timber.d(TAG, "Received raw: $data")
                        
                        // 解析数据
                        for (char in data) {
                            when (char) {
                                '{' -> {
                                    // 开始标记，清空缓冲区
                                    stringBuffer.clear()
                                }
                                '}' -> {
                                    // 结束标记，解析温度值
                                    val tempStr = stringBuffer.toString()
                                    try {
                                        val temp = tempStr.toFloat()
                                        // 验证温度范围（32.00 ~ 45.00）
                                        if (temp in 32.0f..45.0f) {
                                            Timber.i(TAG, "Valid temperature: $temp°C")
                                            emit(temp)
                                        } else {
                                            Timber.w(TAG, "Temperature out of range: $temp")
                                        }
                                    } catch (e: NumberFormatException) {
                                        Timber.w(TAG, "Invalid temperature format: $tempStr")
                                    }
                                    stringBuffer.clear()
                                }
                                else -> {
                                    // 累积数据
                                    stringBuffer.append(char)
                                }
                            }
                        }
                    }
                } else {
                    // 无数据时短暂等待
                    kotlinx.coroutines.delay(50)
                }
                
            } catch (e: Exception) {
                Timber.e(TAG, "Error reading from serial port: ${e.message}")
                kotlinx.coroutines.delay(500)
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 发送蜂鸣器指令
     * @param durationMs 蜂鸣时长（毫秒）
     */
    fun beep(durationMs: Int = 200) {
        if (!isOpen || outputStream == null) {
            Timber.w(TAG, "Serial port not open, cannot beep")
            return
        }
        
        try {
            // TODO: 根据实际设备协议发送指令
            Timber.d(TAG, "Beep: ${durationMs}ms")
        } catch (e: Exception) {
            Timber.e(TAG, "Error sending beep command: ${e.message}")
        }
    }
    
    /**
     * 控制继电器（开门）
     * @param open true=开门, false=关门
     */
    fun controlDoor(open: Boolean) {
        if (!isOpen || outputStream == null) {
            Timber.w(TAG, "Serial port not open, cannot control door")
            return
        }
        
        try {
            // TODO: 根据实际设备协议发送指令
            Timber.d(TAG, "Door: ${if (open) "OPEN" else "CLOSE"}")
        } catch (e: Exception) {
            Timber.e(TAG, "Error controlling door: ${e.message}")
        }
    }
    
    /**
     * 发送原始指令
     */
    private fun sendCommand(data: ByteArray) {
        if (!isOpen || outputStream == null) {
            Timber.w(TAG, "Serial port not open, cannot send command")
            return
        }
        
        try {
            outputStream?.write(data)
            Timber.d(TAG, "Send command: ${data.joinToString(" ") { "%02X".format(it) }}")
        } catch (e: Exception) {
            Timber.e(TAG, "Error sending command: ${e.message}")
        }
    }

    fun isOpened(): Boolean = isOpen
}
