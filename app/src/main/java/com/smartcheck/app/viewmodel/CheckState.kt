package com.smartcheck.app.viewmodel

import com.smartcheck.sdk.HandInfo

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
    SYMPTOM_FAIL
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
