package com.smartcheck.app.viewmodel

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
    
    // 检测结果详情
    val faceConfidence: Float = 0.0f,
    val handDetectionResults: List<String> = emptyList()
)
