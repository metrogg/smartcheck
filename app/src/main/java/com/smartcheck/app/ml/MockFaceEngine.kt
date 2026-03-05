package com.smartcheck.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock 人脸识别引擎
 * 
 * 用于开发阶段测试业务流程，将来替换为 SeetaFaceEngine
 */
@Singleton
class MockFaceEngine @Inject constructor() : FaceEngine {
    
    private var isInitialized = false
    private var frameCount = 0
    
    override fun init(context: Context) {
        Timber.d("MockFaceEngine: Initializing...")
        isInitialized = true
        Timber.d("MockFaceEngine: Initialized successfully")
    }
    
    override suspend fun detectAndRecognize(frame: Bitmap): FaceResult? {
        if (!isInitialized) {
            Timber.w("MockFaceEngine: Not initialized")
            return null
        }
        
        // 模拟检测延时
        delay(100)
        
        frameCount++
        
        // 每 3 帧模拟识别到一个用户
        return if (frameCount % 3 == 0) {
            FaceResult(
                userId = 1L,
                userName = "测试用户",
                confidence = 0.92f,
                boundingBox = Rect(200, 100, 500, 400),
                isLive = true
            )
        } else {
            null
        }
    }
    
    override suspend fun registerUser(userId: Long, frames: List<Bitmap>): Boolean {
        Timber.d("MockFaceEngine: Registering user $userId with ${frames.size} frames")
        delay(500)
        return true
    }

    override fun refreshUserCache() {
        Timber.d("MockFaceEngine: refreshUserCache called (no-op)")
    }

    override fun release() {
        Timber.d("MockFaceEngine: Released")
        isInitialized = false
        frameCount = 0
    }
}
