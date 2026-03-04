package com.smartcheck.app.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class FileLoggingTree(private val context: Context) : Timber.Tree() {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    private var currentDate: String = dateFormat.format(Date())
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    
    init {
        // 创建日志目录
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        // 清理旧日志
        cleanOldLogs(logDir)
        
        // 获取当天的日志文件
        logFile = File(logDir, "smartcheck_$currentDate.log")
        
        try {
            fileWriter = FileWriter(logFile, true)
        } catch (e: Exception) {
            Log.e("FileLogging", "Failed to create log file", e)
        }
    }
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // 检查是否需要轮转（日期变更）
        val today = dateFormat.format(Date())
        if (today != currentDate) {
            rotateLog(today)
        }
        
        // 写入文件
        try {
            val timestamp = timestampFormat.format(Date())
            val priorityChar = when (priority) {
                Log.VERBOSE -> 'V'
                Log.DEBUG -> 'D'
                Log.INFO -> 'I'
                Log.WARN -> 'W'
                Log.ERROR -> 'E'
                Log.ASSERT -> 'A'
                else -> '?'
            }
            
            val logLine = "$timestamp $priorityChar/$tag: $message\n"
            fileWriter?.append(logLine)
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e("FileLogging", "Failed to write log", e)
        }
    }
    
    private fun rotateLog(newDate: String) {
        try {
            fileWriter?.close()
            currentDate = newDate
            logFile = File(context.filesDir, "logs/smartcheck_$newDate.log")
            fileWriter = FileWriter(logFile, true)
        } catch (e: Exception) {
            Log.e("FileLogging", "Failed to rotate log", e)
        }
    }
    
    private fun cleanOldLogs(logDir: File) {
        try {
            val files = logDir.listFiles() ?: return
            val today = Date()
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7) // 保留7天
            
            for (file in files) {
                if (file.isFile && file.name.startsWith("smartcheck_") && file.name.endsWith(".log")) {
                    // 删除7天前的日志
                    if (file.lastModified() < calendar.timeInMillis) {
                        file.delete()
                        Log.d("FileLogging", "Deleted old log: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileLogging", "Failed to clean old logs", e)
        }
    }
    
    fun close() {
        try {
            fileWriter?.close()
        } catch (e: Exception) {
            Log.e("FileLogging", "Failed to close log file", e)
        }
    }
}
