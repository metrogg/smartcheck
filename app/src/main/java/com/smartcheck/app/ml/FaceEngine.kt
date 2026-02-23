package com.smartcheck.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect

/**
 * 人脸识别结果
 * 
 * @param userId 匹配到的用户 ID（null 表示未识别）
 * @param userName 用户姓名
 * @param confidence 识别置信度 [0.0, 1.0]
 * @param boundingBox 人脸检测框
 * @param isLive 活体检测结果（预留，将来接入 SeetaFace 活体模块）
 */
data class FaceResult(
    val userId: Long?,
    val userName: String?,
    val confidence: Float,
    val boundingBox: Rect,
    val isLive: Boolean = true
)

/**
 * 人脸识别引擎接口
 * 
 * 设计基于 SeetaFace2 的能力：
 * - 人脸检测（FaceDetector）
 * - 关键点定位（FaceLandmarker）
 * - 人脸识别（FaceRecognizer）
 * - 活体检测（可选）
 * 
 * 当前提供 Mock 实现，将来接入 SeetaFace2 C++ SDK 时：
 * 1. 在 app/src/main/cpp/ 下实现 JNI 封装
 * 2. 加载 SeetaFace2 模型文件
 * 3. 实现 SeetaFaceEngine 替换 MockFaceEngine
 */
interface FaceEngine {
    
    /**
     * 初始化引擎
     * @param context 上下文，用于访问模型文件
     */
    fun init(context: Context)
    
    /**
     * 检测并识别人脸
     * @param frame 摄像头帧
     * @return 识别结果，null 表示未检测到人脸或识别失败
     */
    suspend fun detectAndRecognize(frame: Bitmap): FaceResult?
    
    /**
     * 注册新用户人脸
     * @param userId 用户 ID
     * @param frames 多帧人脸图像（用于提取稳定特征）
     * @return 是否注册成功
     */
    suspend fun registerUser(userId: Long, frames: List<Bitmap>): Boolean
    
    /**
     * 释放资源
     */
    fun release()
}
