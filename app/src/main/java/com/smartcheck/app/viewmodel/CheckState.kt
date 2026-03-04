package com.smartcheck.app.viewmodel

import com.smartcheck.sdk.HandInfo
import timber.log.Timber

/**
 * 晨检状态枚举
 * 
 * 严格的线性状态机流程
 */
enum class CheckState {
    IDLE,           // 待机：等待人脸识别
    FACE_PASS,      // 人脸通过：准备测温
    TEMP_MEASURING, // 测温中：正在读取温度
    TEMP_FAIL,      // 体温异常：需要复测或拒绝
    HAND_CHECKING,  // 手检中：正在检测手部
    HAND_FAIL,      // 手检不合格：检测到异物/伤口
    AUTO_SUBMITTING,
    ALL_PASS,        // 全部通过：晨检完成
    HAND_PALM_CHECKING,
    HAND_BACK_CHECKING,
    SYMPTOM_CHECKING,
    SYMPTOM_FAIL,
    HEALTH_CERT_EXPIRED  // 健康证过期：禁止上岗
}

/**
 * UI 状态数据类
 */
data class UiState(
    val state: CheckState = CheckState.IDLE,
    val currentUserName: String = "",
    val currentUserId: Long? = null,
    val currentTemp: Float = 0.0f,
    val message: String = "请正视摄像头",
    val isProcessing: Boolean = false,
    val healthCertEndDate: Long? = null,
    val healthCertDaysRemaining: Int? = null,
    val faceImagePath: String? = null,
    val handPalmPath: String? = null,
    val handBackPath: String? = null,
    val handPalmInfos: List<HandInfo> = emptyList(),
    val handBackInfos: List<HandInfo> = emptyList(),
    val handPalmFrameWidth: Int? = null,
    val handPalmFrameHeight: Int? = null,
    val handBackFrameWidth: Int? = null,
    val handBackFrameHeight: Int? = null,
    val handCapturePulse: Boolean = false,
    val autoSubmitRemainingSec: Int? = null,
    val autoSubmitTotalSec: Int = 3,
    val isSubmitting: Boolean = false,
    val isRecordFinalized: Boolean = false,
    val symptomFlags: String = "",
    val handHasIssue: Boolean = false,
    
    // 检测结果详情
    val faceConfidence: Float = 0.0f,
    val handDetectionResults: List<String> = emptyList()
)

/**
 * 晨检状态转换日志记录器
 */
object MorningCheckLogger {
    private const val TAG = "[晨检流程]"
    
    private var lastState: CheckState = CheckState.IDLE
    private var stateEnterTime: Long = System.currentTimeMillis()
    
    /**
     * 记录状态转换
     */
    fun logStateTransition(from: CheckState, to: CheckState) {
        val duration = System.currentTimeMillis() - stateEnterTime
        Timber.tag(TAG).i("状态转换: $from -> $to (耗时: ${duration}ms)")
        lastState = to
        stateEnterTime = System.currentTimeMillis()
    }
    
    /**
     * 记录人脸识别结果
     */
    fun logFaceRecognized(userId: Long, name: String, confidence: Float) {
        Timber.tag(TAG).i("人脸识别: userId=$userId, name=$name, confidence=${String.format("%.2f", confidence)}")
    }
    
    /**
     * 记录体温测量结果
     */
    fun logTemperature(temp: Float, isNormal: Boolean) {
        val status = if (isNormal) "正常" else "异常"
        Timber.tag(TAG).i("体温测量: ${String.format("%.1f", temp)}°C, 状态=$status")
    }
    
    /**
     * 记录手部检测结果
     */
    fun logHandCheck(handCount: Int, hasIssue: Boolean, details: List<String> = emptyList()) {
        val status = if (hasIssue) "异常" else "正常"
        Timber.tag(TAG).i("手部检测: 数量=$handCount, 状态=$status")
        details.forEach { detail ->
            Timber.tag(TAG).i("  - $detail")
        }
    }
    
    /**
     * 记录晨检完成
     */
    fun logComplete(recordId: Long?, totalDurationMs: Long, isPassed: Boolean) {
        val result = if (isPassed) "通过" else "未通过"
        Timber.tag(TAG).i("晨检完成: recordId=$recordId, 结果=$result, 总耗时=${totalDurationMs}ms")
    }
    
    /**
     * 记录错误信息
     */
    fun logError(step: String, error: String) {
        Timber.tag(TAG).e("错误 [$step]: $error")
    }
    
    /**
     * 记录警告信息
     */
    fun logWarning(step: String, message: String) {
        Timber.tag(TAG).w("警告 [$step]: $message")
    }
}
