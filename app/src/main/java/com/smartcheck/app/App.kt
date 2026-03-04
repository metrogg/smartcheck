package com.smartcheck.app

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.smartcheck.app.api.KtorServerManager
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.HiltAndroidApp
import com.smartcheck.app.utils.FileLoggingTree
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), CameraXConfig.Provider {

    @Inject
    lateinit var ktorServerManager: KtorServerManager

    override fun onCreate() {
        super.onCreate()

        // 先初始化 Timber（确保日志可用）
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 同时记录到文件（带日志轮转和自动清理）
        Timber.plant(FileLoggingTree(this))
        
        Timber.d("[App] Application onCreate 开始")

        // 初始化设备授权
        DeviceAuth.init(this)

        Timber.d("SmartCheck Application Started")

        installCrashHandler()

        initHandDetector()

        // 启动 Ktor API 服务器
        startKtorServer()
    }

    private fun startKtorServer() {
        try {
            // 延迟启动，确保依赖注入完成
            android.os.Handler(mainLooper).postDelayed({
                if (::ktorServerManager.isInitialized) {
                    ktorServerManager.start()
                    Timber.i("Ktor server auto-started on app launch")
                } else {
                    Timber.w("KtorServerManager not initialized yet, will retry...")
                    // 再延迟 2 秒重试
                    android.os.Handler(mainLooper).postDelayed({
                        if (::ktorServerManager.isInitialized) {
                            ktorServerManager.start()
                            Timber.i("Ktor server started on retry")
                        } else {
                            Timber.e("KtorServerManager failed to initialize")
                        }
                    }, 2000)
                }
            }, 1000)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Ktor server")
        }
    }

    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logsDir = File(filesDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(System.currentTimeMillis())
                val file = File(logsDir, "crash_$stamp.txt")
                
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                
                pw.println("=== SmartCheck Crash Report ===")
                pw.println("Time: $stamp")
                pw.println("Thread: ${thread.name} (id: ${thread.id})")
                pw.println("Priority: ${thread.priority}")
                pw.println()
                
                pw.println("--- Device Info ---")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                pw.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                pw.println("Hardware: ${Build.HARDWARE}")
                pw.println("Board: ${Build.BOARD}")
                pw.println()
                
                pw.println("--- Memory Info ---")
                val runtime = Runtime.getRuntime()
                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                pw.println("Total: ${totalMemory / (1024*1024)} MB")
                pw.println("Free: ${freeMemory / (1024*1024)} MB")
                pw.println("Max: ${maxMemory / (1024*1024)} MB")
                pw.println("Used: ${(totalMemory - freeMemory) / (1024*1024)} MB")
                pw.println()
                
                pw.println("--- Stack Trace ---")
                throwable.printStackTrace(pw)
                pw.flush()
                
                file.writeText(sw.toString())
                
                Timber.tag("Crash")
                    .e("CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}")
                Timber.tag("Crash")
                    .e("Crash log saved: ${file.absolutePath}")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to write crash log")
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    private fun initHandDetector() {
        try {
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            val product = Build.PRODUCT.lowercase()
            val isRockchip = hardware.contains("rk") || board.contains("rk") || product.contains("rk")
            if (!isRockchip) {
                Timber.w("Skipping HandDetector init on non-RK device. hardware=$hardware board=$board product=$product")
                return
            }

            val result = HandDetector.init(this)
            if (result == 0) {
                Timber.i("HandDetector initialized successfully")
            } else {
                Timber.e("HandDetector initialization failed with code: $result")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize HandDetector")
        }
    }


    override fun getCameraXConfig(): CameraXConfig {
        val defaultConfig = Camera2Config.defaultConfig()
        val builder = CameraXConfig.Builder.fromConfig(defaultConfig)
        val preferredIds = setOf("100", "102")

        builder.setAvailableCamerasLimiter(
            CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter {
                        runCatching { Camera2CameraInfo.from(it).cameraId in preferredIds }
                            .getOrDefault(false)
                    }
                }
                .build()
        )
        return builder.build()
    }
}
