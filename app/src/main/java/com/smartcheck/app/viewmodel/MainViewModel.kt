package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.HardwareRepository
import com.smartcheck.app.data.repository.RecordRepository
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.data.upload.RecordUploadReporter
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.app.voice.VoicePrompter
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 主 ViewModel - 晨检状态机核心逻辑
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val faceEngine: FaceEngine,
    private val hardwareRepository: HardwareRepository,
    private val voicePrompter: VoicePrompter,
    private val recordUploadReporter: RecordUploadReporter,
    private val userRepository: UserRepository,
    private val recordRepository: RecordRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // 手部检测结果数据流 (用于可视化)
    private val _handDetectionState = MutableStateFlow<List<com.smartcheck.sdk.HandInfo>>(emptyList())
    val handDetectionState: StateFlow<List<com.smartcheck.sdk.HandInfo>> = _handDetectionState.asStateFlow()

    private val _isHandDetecting = MutableStateFlow(false)
    val isHandDetecting: StateFlow<Boolean> = _isHandDetecting.asStateFlow()

    private val _faceDetectionBoxes = MutableStateFlow<List<Rect>>(emptyList())
    val faceDetectionBoxes: StateFlow<List<Rect>> = _faceDetectionBoxes.asStateFlow()
    
    private var tempMeasureJob: Job? = null
    private var resetJob: Job? = null

    private var handDetectionJob: Job? = null
    private val handDetectionSeq = AtomicInteger(0)

    private val isHandStepProcessing = AtomicBoolean(false)
    private var handOkFrames = 0
    private var handStepStartAt = 0L

    private val isRockchip: Boolean = run {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val product = Build.PRODUCT.lowercase()
        hardware.contains("rk") || board.contains("rk") || product.contains("rk")
    }
    
    init {
        Timber.d("MainViewModel initialized")
        hardwareRepository.init()
    }

    fun processFrame(frame: Bitmap) {
        when (_uiState.value.state) {
            CheckState.IDLE -> processCameraFrame(frame)
            CheckState.HAND_PALM_CHECKING, CheckState.HAND_BACK_CHECKING -> processHandStepFrame(frame)
            else -> Unit
        }
    }
    
    /**
     * 处理实时手部检测帧
     */
    fun processHandDetection(frame: Bitmap) {
        // Real-time frames come in frequently. If we cancel and restart on each frame,
        // detection may never finish and UI will stay in "detecting" state forever.
        // Instead, drop frames while a detection is in-flight.
        if (handDetectionJob?.isActive == true) return

        handDetectionJob = viewModelScope.launch {
            _isHandDetecting.value = true
            try {
                val results = withContext(Dispatchers.Default) {
                    HandDetector.detect(frame)
                }
                _handDetectionState.value = results
            } catch (e: CancellationException) {
                // Normal control path (e.g. screen leaving).
            } catch (e: Exception) {
                Timber.e(e, "Hand detection error")
            } finally {
                _isHandDetecting.value = false
            }
        }
    }

    fun clearHandDetection() {
        handDetectionJob?.cancel()
        _handDetectionState.value = emptyList()
        _isHandDetecting.value = false
    }
    
    /**
     * 处理摄像头帧（人脸识别）
     */
    fun processCameraFrame(frame: Bitmap) {
        if (_uiState.value.state != CheckState.IDLE) {
            _faceDetectionBoxes.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            try {
                val result = faceEngine.detectAndRecognize(frame)
                _faceDetectionBoxes.value = if (result != null && !result.boundingBox.isEmpty) {
                    listOf(result.boundingBox)
                } else {
                    emptyList()
                }

                if (result != null && result.userId != null) {
                    onFaceRecognized(result.userId, result.userName ?: "未知用户", result.confidence)
                }
            } catch (e: Exception) {
                Timber.e(e, "Face recognition error")
            }
        }
    }
    
    /**
     * 人脸识别成功回调
     */
    private fun onFaceRecognized(userId: Long, userName: String, confidence: Float) {
        Timber.d("Face recognized: $userName (confidence: $confidence)")

        _faceDetectionBoxes.value = emptyList()
        
        _uiState.update {
            it.copy(
                state = CheckState.FACE_PASS,
                currentUserId = userId,
                currentUserName = userName,
                faceConfidence = confidence,
                message = "欢迎，$userName"
            )
        }
        
        hardwareRepository.beep("success")
        voicePrompter.speak("欢迎，$userName")
        
        // 1 秒后自动进入测温
        viewModelScope.launch {
            delay(1000)
            startTemperatureMeasure()
        }
    }
    
    /**
     * 开始测温
     */
    private fun startTemperatureMeasure() {
        Timber.d("Starting temperature mea surement")
        
        _uiState.update {
            it.copy(
                state = CheckState.TEMP_MEASURING,
                message = "正在测温，请稍候..."
            )
        }
        
        voicePrompter.speak("正在测温")
        
        tempMeasureJob?.cancel()
        tempMeasureJob = viewModelScope.launch {
            hardwareRepository.getTemperatureFlow()
                .take(3) // 取 3 次读数
                .collect { temp ->
                    Timber.d("Temperature reading: $temp")
                    _uiState.update { it.copy(currentTemp = temp) }
                }
            
            // 测温完成，判断结果
            val avgTemp = _uiState.value.currentTemp
            if (avgTemp < 37.3f) {
                onTemperatureNormal(avgTemp)
            } else {
                onTemperatureAbnormal(avgTemp)
            }
        }
    }
    
    /**
     * 体温正常
     */
    private fun onTemperatureNormal(temp: Float) {
        Timber.d("Temperature normal: $temp")

        hardwareRepository.beep("success")
        startHandPalmCheck()
    }
    
    /**
     * 体温异常
     */
    private fun onTemperatureAbnormal(temp: Float) {
        Timber.w("Temperature abnormal: $temp")
        
        _uiState.update {
            it.copy(
                state = CheckState.TEMP_FAIL,
                message = "体温异常：${String.format("%.1f", temp)}°C，请复测"
            )
        }
        
        hardwareRepository.beep("error")
        voicePrompter.speak("体温异常，请复测")
        
        // 保存失败记录
        saveCheckRecord(isPassed = false, isTempNormal = false, isHandNormal = true)
        
        // 5 秒后重置
        scheduleReset(5000)
    }
    
    /**
     * 执行手部检测
     */
    private fun performHandCheck() {
        Timber.d("Performing hand check")
        startHandPalmCheck()
    }
    
    /**
     * 手检通过
     */
    private fun onHandCheckPass() {
        Timber.d("Hand check passed")

        _handDetectionState.value = emptyList()
        _uiState.update {
            it.copy(
                state = CheckState.SYMPTOM_CHECKING,
                message = "请回答健康询问"
            )
        }
        voicePrompter.speak("请回答健康询问")
    }
    
    /**
     * 手检失败
     */
    private fun onHandCheckFail(issues: List<String>) {
        Timber.w("Hand check failed: $issues")
        
        _uiState.update {
            it.copy(
                state = CheckState.HAND_FAIL,
                message = "手部检测不合格：${issues.joinToString(", ")}",
                handDetectionResults = issues
            )
        }
        
        hardwareRepository.beep("error")
        voicePrompter.speak("手部检测不合格")
        
        // 保存失败记录
        saveCheckRecord(
            isPassed = false,
            isTempNormal = true,
            isHandNormal = false,
            remark = issues.joinToString(", ")
        )
        
        // 5 秒后重置
        scheduleReset(5000)
    }

    fun submitSymptoms(symptoms: List<String>) {
        if (_uiState.value.state != CheckState.SYMPTOM_CHECKING) return

        val trimmed = symptoms.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) {
            onAllPass(remark = "无")
        } else {
            onSymptomFail(trimmed)
        }
    }

    private fun onAllPass(remark: String) {
        _uiState.update {
            it.copy(
                state = CheckState.ALL_PASS,
                message = "晨检通过！"
            )
        }

        hardwareRepository.beep("success")
        voicePrompter.speak("晨检通过")
        hardwareRepository.openDoor()

        saveCheckRecord(
            isPassed = true,
            isTempNormal = true,
            isHandNormal = true,
            remark = remark
        )

        scheduleReset(3000)
    }

    private fun onSymptomFail(symptoms: List<String>) {
        _uiState.update {
            it.copy(
                state = CheckState.SYMPTOM_FAIL,
                message = "有不适症状：${symptoms.joinToString(", ")}" 
            )
        }

        hardwareRepository.beep("error")
        voicePrompter.speak("请人工复核")

        saveCheckRecord(
            isPassed = false,
            isTempNormal = true,
            isHandNormal = true,
            remark = "症状：${symptoms.joinToString(", ")}" 
        )

        scheduleReset(5000)
    }

    private fun startHandPalmCheck() {
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        _handDetectionState.value = emptyList()

        _uiState.update {
            it.copy(
                state = CheckState.HAND_PALM_CHECKING,
                message = "请将手心对准摄像头"
            )
        }

        voicePrompter.speak("请将手心对准摄像头")
    }

    private fun startHandBackCheck() {
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        _handDetectionState.value = emptyList()

        _uiState.update {
            it.copy(
                state = CheckState.HAND_BACK_CHECKING,
                message = "请将手背对准摄像头"
            )
        }

        voicePrompter.speak("请将手背对准摄像头")
    }

    private fun processHandStepFrame(frame: Bitmap) {
        val state = _uiState.value.state
        if (state != CheckState.HAND_PALM_CHECKING && state != CheckState.HAND_BACK_CHECKING) return
        if (!isHandStepProcessing.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (!isRockchip) {
                    if (now - handStepStartAt >= 800L) {
                        if (state == CheckState.HAND_PALM_CHECKING) {
                            startHandBackCheck()
                        } else {
                            onHandCheckPass()
                        }
                    }
                    return@launch
                }

                val results = withContext(Dispatchers.Default) {
                    HandDetector.detect(frame)
                }
                _handDetectionState.value = results

                if (results.isEmpty()) {
                    handOkFrames = 0
                    return@launch
                }

                val hasForeignObject = results.any { it.hasForeignObject }
                if (hasForeignObject) {
                    val issues = results.filter { it.hasForeignObject }.map { it.label }.ifEmpty { results.map { it.label } }
                    onHandCheckFail(issues)
                    return@launch
                }

                handOkFrames++
                if (handOkFrames >= 3) {
                    if (state == CheckState.HAND_PALM_CHECKING) {
                        hardwareRepository.beep("success")
                        startHandBackCheck()
                    } else {
                        hardwareRepository.beep("success")
                        onHandCheckPass()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Hand step check error")
                onHandCheckFail(listOf("检测异常"))
            } finally {
                isHandStepProcessing.set(false)
            }
        }
    }
    
    /**
     * 保存晨检记录
     */
    private fun saveCheckRecord(
        isPassed: Boolean,
        isTempNormal: Boolean,
        isHandNormal: Boolean,
        remark: String = ""
    ) {
        val state = _uiState.value
        if (state.currentUserId == null) return
        
        viewModelScope.launch {
            try {
                val user = userRepository.getUserById(state.currentUserId)
                val record = RecordEntity(
                    userId = state.currentUserId,
                    userName = state.currentUserName,
                    employeeId = user?.employeeId ?: "",
                    temperature = state.currentTemp,
                    isTempNormal = isTempNormal,
                    isHandNormal = isHandNormal,
                    isPassed = isPassed,
                    remark = remark
                )
                val recordId = recordRepository.insertRecord(record)
                val savedRecord = record.copy(id = recordId)
                Timber.d("Record saved: $savedRecord")

                try {
                    withContext(Dispatchers.IO) {
                        recordUploadReporter.upload(savedRecord)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to upload record")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save record")
            }
        }
    }
    
    /**
     * 定时重置状态机
     */
    private fun scheduleReset(delayMs: Long) {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(delayMs)
            reset()
        }
    }
    
    /**
     * 重置状态机到 IDLE
     */
    fun reset() {
        Timber.d("Resetting state machine")
        
        tempMeasureJob?.cancel()
        resetJob?.cancel()
        
        _uiState.update {
            UiState(
                state = CheckState.IDLE,
                message = "请正视摄像头"
            )
        }

        _handDetectionState.value = emptyList()
        _faceDetectionBoxes.value = emptyList()
        handOkFrames = 0
        handStepStartAt = 0L
    }
    
    override fun onCleared() {
        super.onCleared()
        tempMeasureJob?.cancel()
        resetJob?.cancel()
        voicePrompter.shutdown()
        Timber.d("MainViewModel cleared")
    }
}
