package com.smartcheck.sdk

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log

/**
 * 单个异物检测结果（坐标相对于 hand crop）
 */
data class ForeignObjectInfo(
    val box: RectF,
    val score: Float,
    val label: String,
)

/**
 * 手部检测结果数据类
 */
data class HandInfo(
    val id: Int,
    val box: RectF,           // 手部检测框
    val score: Float,         // 置信度
    val keyPoints: List<PointF>, // 21个关键点坐标
    val hasForeignObject: Boolean, // 是否有异物/伤口
    val label: String,         // 标签：Normal, Wound, Foreign Object
    val foreignObjects: List<ForeignObjectInfo> = emptyList(),
) {
    // Backward-compatible constructor for older JNI signature.
    constructor(
        id: Int,
        box: RectF,
        score: Float,
        keyPoints: List<PointF>,
        hasForeignObject: Boolean,
        label: String,
    ) : this(
        id = id,
        box = box,
        score = score,
        keyPoints = keyPoints,
        hasForeignObject = hasForeignObject,
        label = label,
        foreignObjects = emptyList(),
    )
}

/**
 * 手部异物检测 SDK
 * 
 * 负责加载 RKNN 模型并进行推理
 */
object HandDetector {
    
    private const val TAG = "HandDetector"
    private const val HAND_MODEL_NAME = "hand_check_rk3566.rknn"
    private const val FOREIGN_MODEL_NAME = "yiwu_check_rk3566.rknn"
    
    private var isInitialized = false
    
    // 加载 native 库
    init {
        try {
            System.loadLibrary("hand_detector")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
    
    /**
     * 初始化检测器
     * @param context Android Context
     * @param licenseKey 授权密钥（预留）
     * @return 0=成功, -1=失败
     */
    fun init(context: Context, licenseKey: String = ""): Int {
        if (isInitialized) {
            Log.w(TAG, "Already initialized")
            return 0
        }
        
        try {
            // 检查模型文件是否存在
            val assetManager = context.assets
            try {
                assetManager.open(HAND_MODEL_NAME).close()
                assetManager.open(FOREIGN_MODEL_NAME).close()
            } catch (e: Exception) {
                Log.e(TAG, "Model files not found in assets/")
                Log.e(TAG, "Required files: $HAND_MODEL_NAME, $FOREIGN_MODEL_NAME")
                Log.e(TAG, "Please check hand-sdk/src/main/assets/ directory")
                return -1
            }
            
            val ret = nativeInit(assetManager, HAND_MODEL_NAME, FOREIGN_MODEL_NAME)
            
            if (ret == 0) {
                isInitialized = true
                Log.i(TAG, "HandDetector initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize HandDetector: $ret")
                Log.e(TAG, "Possible reasons:")
                Log.e(TAG, "  1. RKNN runtime library not found")
                Log.e(TAG, "  2. Model format incompatible with device")
                Log.e(TAG, "  3. Insufficient memory")
            }
            
            return ret
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            return -1
        }
    }
    
    /**
     * 执行手部异物检测
     * @param bitmap 摄像头的图片帧
     * @return 检测到的手部列表
     */
    fun detect(bitmap: Bitmap): List<HandInfo> {
        if (!isInitialized) {
            Log.w(TAG, "HandDetector not initialized")
            return emptyList()
        }
        
        try {
            val results = nativeDetect(bitmap)
            val handList = results?.toList() ?: emptyList()
            
            // 记录手部检测结果详情
            if (handList.isNotEmpty()) {
                Log.i(TAG, "[手部检测] 检测到 ${handList.size} 只手")
                handList.forEachIndexed { index, hand ->
                    Log.i(TAG, "[手部检测] 手 #$index: " +
                            "label=${hand.label}, " +
                            "hasForeignObject=${hand.hasForeignObject}, " +
                            "score=${String.format("%.2f", hand.score)}, " +
                            "foreignObjects=${hand.foreignObjects.size}")
                    
                    // 记录异物详情
                    hand.foreignObjects.forEachIndexed { objIndex, obj ->
                        Log.i(TAG, "[手部检测] 异物 #$objIndex: " +
                                "label=${obj.label}, " +
                                "score=${String.format("%.2f", obj.score)}, " +
                                "box=[${String.format("%.1f", obj.box.left)}, " +
                                "${String.format("%.1f", obj.box.top)}, " +
                                "${String.format("%.1f", obj.box.width())}, " +
                                "${String.format("%.1f", obj.box.height())}]")
                    }
                }
            }
            
            return handList
        } catch (e: Exception) {
            Log.e(TAG, "Exception during detection", e)
            return emptyList()
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (!isInitialized) {
            return
        }
        
        try {
            nativeRelease()
            isInitialized = false
            Log.i(TAG, "HandDetector released")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during release", e)
        }
    }
    
    // ==================== JNI 方法 ====================
    
    /**
     * 初始化 RKNN 模型
     */
    private external fun nativeInit(
        assetManager: AssetManager,
        handModelName: String,
        foreignModelName: String
    ): Int
    
    /**
     * 执行检测
     */
    private external fun nativeDetect(bitmap: Bitmap): Array<HandInfo>?
    
    /**
     * 释放资源
     */
    private external fun nativeRelease()
}
