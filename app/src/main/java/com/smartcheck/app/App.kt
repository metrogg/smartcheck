package com.smartcheck.app

import android.app.Application
import android.os.Build
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

@HiltAndroidApp
class App : Application(), CameraXConfig.Provider {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Timber 日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("SmartCheck Application Started")

        installCrashHandler()
        
        // 初始化 HandDetector
        initHandDetector()

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
                pw.println("Thread: ${thread.name}")
                pw.println("Time: $stamp")
                pw.println()
                throwable.printStackTrace(pw)
                pw.flush()
                file.writeText(sw.toString())
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
