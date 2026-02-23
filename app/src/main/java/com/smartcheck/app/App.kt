package com.smartcheck.app

import android.app.Application
import android.os.Build
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class App : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Timber 日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("SmartCheck Application Started")
        
        // 初始化 HandDetector
        initHandDetector()
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
}
