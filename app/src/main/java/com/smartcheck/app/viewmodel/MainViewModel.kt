package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.repository.HardwareRepository
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.data.upload.RecordUploadReporter
import com.smartcheck.app.domain.model.toEntity
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.domain.repository.IVoiceService
import com.smartcheck.app.domain.usecase.HandCheckResult
import com.smartcheck.app.domain.usecase.ImageStorageUseCase
import com.smartcheck.app.domain.usecase.MorningCheckUseCase
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 主 ViewModel - 晨检状态机核心逻辑
 * 
 * 注意：此 ViewModel 仍包含大量业务逻辑，待逐步迁移到 UseCase 层
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val faceEngine: FaceEngine,
    private val hardwareRepository: HardwareRepository,
    private val voiceService: IVoiceService,
    private val recordUploadReporter: RecordUploadReporter,
    private val settingsRepository: SettingsRepository,
    private val userRepository: IUserRepository,
    private val recordRepository: IRecordRepository,
    private val morningCheckUseCase: MorningCheckUseCase,
    private val imageStorageUseCase: ImageStorageUseCase
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

    data class PerfMetrics(
        val faceDuration: Duration = Duration.ZERO,
        val handDuration: Duration = Duration.ZERO,
        val tempDuration: Duration = Duration.ZERO
    )

    private val _perfMetrics = MutableStateFlow(PerfMetrics())
    val perfMetrics: StateFlow<PerfMetrics> = _perfMetrics.asStateFlow()
    
    private var tempMeasureJob: Job? = null
    private var resetJob: Job? = null

    private var handDetectionJob: Job? = null
    private val handDetectionSeq = AtomicInteger(0)

    private val isHandStepProcessing = AtomicBoolean(false)
    private var handOkFrames = 0
    private var handStepStartAt = 0L
    private var handCooldownJob: Job? = null
    private var autoSubmitJob: Job? = null

    private var currentFacePath: String? = null
    private var currentPalmPath: String? = null
    private var currentBackPath: String? = null
    private var currentFaceBitmap: Bitmap? = null
    private var currentPalmBitmap: Bitmap? = null
    private var currentBackBitmap: Bitmap? = null
    private var lastFaceFrameAt: Long = 0L
    private var lastFaceDetectAt: Long = 0L
    private var faceDetectJob: Job? = null
    private var faceSaveJob: Job? = null
    private var palmSaveJob: Job? = null
    private var backSaveJob: Job? = null

    private val minFreeBytes = 200L * 1024L * 1024L

    private val isRockchip: Boolean = run {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val product = Build.PRODUCT.lowercase()
        hardware.contains("rk") || board.contains("rk") || product.contains("rk")
    }
    
    init {
        Timber.tag("MainViewModel").d("MainViewModel initialized")
        hardwareRepository.init()
        voiceService.setEnabled(settingsRepository.isVoiceEnabled())
        viewModelScope.launch {
            settingsRepository.voiceEnabled.collect { enabled ->
                voiceService.setEnabled(enabled)
            }
        }
    }

    fun processFrame(frame: Bitmap) {
        val now = System.currentTimeMillis()
        if (_uiState.value.state == CheckState.IDLE) {
            if (now - lastFaceFrameAt >= 300L) {
                lastFaceFrameAt = now
                currentFaceBitmap = frame.copy(Bitmap.Config.ARGB_8888, false)
            }
        } else {
            if (faceDetectJob?.isActive == true) {
                faceDetectJob?.cancel()
            }
        }
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
                val startAt = SystemClock.elapsedRealtime()
                val results = withContext(Dispatchers.Default) {
                    HandDetector.detect(frame)
                }
                val elapsed = SystemClock.elapsedRealtime() - startAt
                _perfMetrics.update { it.copy(handDuration = elapsed.milliseconds) }
                _handDetectionState.value = results
            } catch (e: CancellationException) {
                // Normal control path (e.g. screen leaving).
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Hand detection error")
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
        val now = SystemClock.elapsedRealtime()
        if (now - lastFaceDetectAt < 150L) {
            frame.safeRecycle()
            return
        }
        if (faceDetectJob?.isActive == true) {
            frame.safeRecycle()
            return
        }
        lastFaceDetectAt = now

        faceDetectJob = viewModelScope.launch {
            val safeBitmap = frame.copy(Bitmap.Config.ARGB_8888, true)
            try {
                val startAt = SystemClock.elapsedRealtime()
                val result = withContext(NonCancellable) {
                    faceEngine.detectAndRecognize(safeBitmap)
                }
                val elapsed = SystemClock.elapsedRealtime() - startAt
                _perfMetrics.update { it.copy(faceDuration = elapsed.milliseconds) }
                _faceDetectionBoxes.value = if (result != null && !result.boundingBox.isEmpty) {
                    listOf(result.boundingBox)
                } else {
                    emptyList()
                }

                if (result != null && result.userId != null) {
                    onFaceRecognized(result.userId, result.userName ?: "未知用户", result.confidence)
                } else {
                    _uiState.update { it.copy(message = "请正视摄像头") }
                }
            } catch (e: CancellationException) {
                // Normal control flow
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Face recognition error")
            } finally {
                safeBitmap.safeRecycle()
                frame.safeRecycle()
            }
        }
    }
    
    /**
     * 人脸识别成功回调
     */
    private fun onFaceRecognized(userId: Long, userName: String, confidence: Float) {
        if (_uiState.value.state != CheckState.IDLE) return
        Timber.tag("MainViewModel").d("Face recognized: $userName (confidence: $confidence)")

        viewModelScope.launch {
            val result = morningCheckUseCase.onFaceRecognized(userId, userName, confidence)
            _uiState.update {
                it.copy(
                    state = CheckState.FACE_PASS,
                    currentUserId = result.userId,
                    currentUserName = result.userName,
                    healthCertEndDate = result.healthCertEndDate,
                    healthCertDaysRemaining = result.healthCertDaysRemaining,
                    faceConfidence = result.faceConfidence,
                    message = result.message
                )
            }
        }

        faceSaveJob?.cancel()
        faceSaveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = currentFaceBitmap ?: return@launch
                val result = imageStorageUseCase.saveFaceImage(bitmap)
                result.onSuccess { name ->
                    currentFacePath = name
                    _uiState.update { it.copy(faceImagePath = name) }
                }.onFailure {
                    _uiState.update { it.copy(message = "照片保存失败") }
                }
                bitmap.recycle()
                currentFaceBitmap = null
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Failed to save face snapshot")
            }
        }
        
        hardwareRepository.beep("success")
        
        viewModelScope.launch {
            delay(1000)
            startTemperatureMeasure()
        }
    }
    
    /**
     * 开始测温
     */
    private fun startTemperatureMeasure() {
        Timber.tag("MainViewModel").d("Starting temperature measurement")
        
        _uiState.update {
            it.copy(
                state = CheckState.TEMP_MEASURING,
                message = "正在测温，请稍候..."
            )
        }
        
        voiceService.speak("正在测温")
        
        tempMeasureJob?.cancel()
        tempMeasureJob = viewModelScope.launch {
            val result = morningCheckUseCase.measureTemperatureFlow(3)
            result.onSuccess { tempResult ->
                _uiState.update { it.copy(currentTemp = tempResult.temperature) }
                if (tempResult.isNormal) {
                    onTemperatureNormal(tempResult.temperature)
                } else {
                    onTemperatureAbnormal(tempResult.temperature)
                }
            }.onFailure {
                _uiState.update { it.copy(message = "测温失败") }
                scheduleReset(3000)
            }
        }
    }
    
    /**
     * 体温正常
     */
    private fun onTemperatureNormal(temp: Float) {
        Timber.tag("MainViewModel").d("Temperature normal: $temp")

        _uiState.update {
            it.copy(
                state = CheckState.TEMP_MEASURING,
                message = "体温正常，准备手部检测"
            )
        }
        hardwareRepository.beep("success")
        morningCheckUseCase.speakTemperatureNormal()
        startHandPalmCheck()
    }
    
    /**
     * 体温异常
     */
    private fun onTemperatureAbnormal(temp: Float) {
        Timber.tag("MainViewModel").w("Temperature abnormal: $temp")
        
        _uiState.update {
            it.copy(
                state = CheckState.TEMP_FAIL,
                message = "体温异常：${String.format("%.1f", temp)}°C，请复测"
            )
        }
        
        hardwareRepository.beep("error")
        morningCheckUseCase.speakTemperatureAbnormal(temp)
        
        saveCheckRecord(isPassed = false, isTempNormal = false, isHandNormal = true)
        
        scheduleReset(5000)
    }
    
    /**
     * 执行手部检测
     */
    private fun performHandCheck() {
        Timber.tag("MainViewModel").d("Performing hand check")
        startHandPalmCheck()
    }
    
    /**
     * 手检通过
     */
    private fun onHandCheckPass() {
        Timber.tag("MainViewModel").d("Hand check passed")

        _handDetectionState.value = emptyList()
        _uiState.update {
            it.copy(
                state = CheckState.SYMPTOM_CHECKING,
                message = "请回答健康询问",
                autoSubmitRemainingSec = null
            )
        }
        morningCheckUseCase.speakHandCheckPass()
    }
    
    /**
     * 手检失败
     */
    private fun onHandCheckFail(issues: List<String>) {
        Timber.tag("MainViewModel").w("Hand check failed: $issues")
        
        _uiState.update {
            it.copy(
                state = CheckState.HAND_FAIL,
                message = "手部检测不合格：${issues.joinToString(", ")}",
                handDetectionResults = issues
            )
        }
        
        hardwareRepository.beep("error")
        morningCheckUseCase.speakHandCheckFail()
        _uiState.update { it.copy(symptomFlags = issues.joinToString(", ")) }
    }

    fun submitSymptoms(symptoms: List<String>) {
        if (_uiState.value.state != CheckState.SYMPTOM_CHECKING) return

        val result = morningCheckUseCase.processSymptomSubmission(symptoms)
        if (result.isAllPass) {
            onAllPass(remark = "无")
        } else {
            onSymptomFail(symptoms)
        }
    }

    private fun onAllPass(remark: String) {
        _uiState.update {
            it.copy(
                state = CheckState.ALL_PASS,
                message = "晨检通过！",
                symptomFlags = remark,
                autoSubmitRemainingSec = null
            )
        }

        hardwareRepository.beep("success")
        morningCheckUseCase.speakAllPass()
        hardwareRepository.openDoor()
    }

    fun confirmHandFront(issues: List<String>) = Unit

    fun confirmHandBack(issues: List<String>) = Unit

    private fun onSymptomFail(symptoms: List<String>) {
        val summary = symptoms.joinToString(", ")
        _uiState.update {
            it.copy(
                state = CheckState.SYMPTOM_FAIL,
                message = "有不适症状：$summary",
                symptomFlags = summary
            )
        }

        hardwareRepository.beep("error")
        morningCheckUseCase.speakSymptomFail()
    }

    fun finalizeCheckRecord() {
        val state = _uiState.value
        if (state.isSubmitting || state.isRecordFinalized) return
        if (state.currentUserId == null) return
        if (state.state == CheckState.SYMPTOM_CHECKING) return
        if (state.handHasIssue) return

        val isPassed = state.state == CheckState.ALL_PASS
        val isTempNormal = state.state != CheckState.TEMP_FAIL
        val isHandNormal = state.state != CheckState.HAND_FAIL

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceSaveJob?.join()
                palmSaveJob?.join()
                backSaveJob?.join()

                val userResult = userRepository.getUserById(state.currentUserId)
                val user = userResult.getOrNull()

                val healthCertStatus = when {
                    state.healthCertDaysRemaining == null -> HealthCertStatus.VALID
                    state.healthCertDaysRemaining < 0 -> HealthCertStatus.EXPIRED
                    state.healthCertDaysRemaining < 7 -> HealthCertStatus.EXPIRING_SOON
                    else -> HealthCertStatus.VALID
                }

                val handCheckResult = HandCheckResult(
                    palmNormal = isHandNormal,
                    backNormal = isHandNormal,
                    palmImagePath = currentPalmPath,
                    backImagePath = currentBackPath
                )

                val result = morningCheckUseCase.saveRecord(
                    userId = state.currentUserId,
                    userName = state.currentUserName,
                    employeeId = user?.employeeId ?: "",
                    temperature = state.currentTemp,
                    isTempNormal = isTempNormal,
                    handCheckResult = handCheckResult,
                    symptoms = emptyList(),
                    healthCertStatus = healthCertStatus,
                    faceImagePath = currentFacePath,
                    palmImagePath = currentPalmPath,
                    backImagePath = currentBackPath
                )

                result.onSuccess { savedRecord ->
                    Timber.tag("MainViewModel").d("Record saved: $savedRecord")
                    runCatching {
                        recordUploadReporter.upload(savedRecord.toEntity())
                    }.onFailure { e ->
                        Timber.tag("MainViewModel").e(e, "Failed to upload record")
                    }
                }.onFailure { e ->
                    Timber.tag("MainViewModel").e(e, "Failed to save record")
                }

                _uiState.update { it.copy(isRecordFinalized = true) }
                scheduleReset(if (isPassed) 3000 else 5000)
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Failed to finalize record")
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun retakeFace() {
        reset()
    }

    fun retakeHandPalm() {
        val state = _uiState.value
        if (state.currentUserId == null) return
        if (state.isSubmitting) return
        startHandPalmCheck()
    }

    fun retakeHandBack() {
        val state = _uiState.value
        if (state.currentUserId == null) return
        if (state.isSubmitting) return
        val palmIssues = state.handPalmInfos.filter { it.hasForeignObject }.map { it.label }
        _uiState.update {
            it.copy(
                handBackPath = null,
                handBackInfos = emptyList(),
                handBackFrameWidth = null,
                handBackFrameHeight = null,
                handHasIssue = palmIssues.isNotEmpty(),
                handDetectionResults = palmIssues
            )
        }
        startHandBackCheck()
    }

    private fun startHandPalmCheck() {
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        _handDetectionState.value = emptyList()
        currentPalmPath = null
        currentBackPath = null
        handCooldownJob?.cancel()
        _faceDetectionBoxes.value = emptyList()

        _uiState.update {
            it.copy(
                state = CheckState.HAND_PALM_CHECKING,
                message = "请将手心对准摄像头",
                handPalmPath = null,
                handBackPath = null,
                handPalmInfos = emptyList(),
                handBackInfos = emptyList()
            )
        }

        voiceService.speak("请将手心对准摄像头")
    }

    private fun startHandBackCheck() {
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        _handDetectionState.value = emptyList()
        handCooldownJob?.cancel()

        _uiState.update {
            it.copy(
                state = CheckState.HAND_BACK_CHECKING,
                message = "请将手背对准摄像头"
            )
        }

        voiceService.speak("请翻转手背对准摄像头")
    }

    private fun processHandStepFrame(frame: Bitmap) {
        val state = _uiState.value.state
        if (state != CheckState.HAND_PALM_CHECKING && state != CheckState.HAND_BACK_CHECKING) return
        if (handCooldownJob?.isActive == true) return
        if (!isHandStepProcessing.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (!isRockchip) {
                    if (now - handStepStartAt >= 800L) {
                        if (state == CheckState.HAND_PALM_CHECKING) {
                            captureHandPalmAndCooldown(frame, emptyList())
                        } else {
                            captureHandBackAndFinish(frame, emptyList())
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
                val hasIssueSoFar = hasForeignObject || _uiState.value.handHasIssue
                if (hasIssueSoFar) {
                    val issues = if (hasForeignObject) {
                        results.filter { it.hasForeignObject }.map { it.label }.ifEmpty { results.map { it.label } }
                    } else {
                        _uiState.value.handDetectionResults
                    }
                    _uiState.update {
                        if (state == CheckState.HAND_PALM_CHECKING) {
                            it.copy(
                                handPalmInfos = results,
                                handPalmFrameWidth = frame.width,
                                handPalmFrameHeight = frame.height,
                                handHasIssue = true,
                                handDetectionResults = issues
                            )
                        } else {
                            it.copy(
                                handBackInfos = results,
                                handBackFrameWidth = frame.width,
                                handBackFrameHeight = frame.height,
                                handHasIssue = true,
                                handDetectionResults = issues
                            )
                        }
                    }
                    if (state == CheckState.HAND_PALM_CHECKING) {
                        captureHandPalmAndCooldown(frame, results, isIssue = true)
                    } else {
                        captureHandBackAndFinish(frame, results, issues, isIssue = true)
                    }
                    return@launch
                }

                handOkFrames++
                if (handOkFrames >= 3) {
                    if (state == CheckState.HAND_PALM_CHECKING) {
                        captureHandPalmAndCooldown(frame, results)
                    } else {
                        val issues = _uiState.value.handDetectionResults
                        val hasPriorIssue = _uiState.value.handHasIssue
                        captureHandBackAndFinish(frame, results, issues, isIssue = hasPriorIssue)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Hand step check error")
                onHandCheckFail(listOf("检测异常"))
            } finally {
                isHandStepProcessing.set(false)
                frame.safeRecycle()
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

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceSaveJob?.join()
                palmSaveJob?.join()
                backSaveJob?.join()
                val userResult = userRepository.getUserById(state.currentUserId)
                val user = userResult.getOrNull()

                val healthCertStatus = when {
                    state.healthCertDaysRemaining == null -> HealthCertStatus.VALID
                    state.healthCertDaysRemaining < 0 -> HealthCertStatus.EXPIRED
                    state.healthCertDaysRemaining < 7 -> HealthCertStatus.EXPIRING_SOON
                    else -> HealthCertStatus.VALID
                }

                val record = Record(
                    userId = state.currentUserId,
                    userName = state.currentUserName,
                    employeeId = user?.employeeId ?: "",
                    temperature = state.currentTemp,
                    isTempNormal = isTempNormal,
                    isHandNormal = isHandNormal,
                    isPassed = isPassed,
                    handStatus = if (isHandNormal) HandStatus.NORMAL else HandStatus.ABNORMAL,
                    healthCertStatus = healthCertStatus,
                    symptomFlags = emptyList(),
                    faceImagePath = currentFacePath,
                    handPalmPath = currentPalmPath,
                    handBackPath = currentBackPath,
                    remark = remark
                )
                val saveResult = recordRepository.saveRecord(record)
                val recordId = saveResult.getOrNull() ?: 0L
                val savedRecord = record.copy(id = recordId)
                Timber.tag("MainViewModel").d("Record saved: $savedRecord")

                try {
                    withContext(Dispatchers.IO) {
                        recordUploadReporter.upload(savedRecord.toEntity())
                    }
                } catch (e: Exception) {
                    Timber.tag("MainViewModel").e(e, "Failed to upload record")
                }
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Failed to save record")
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
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
        Timber.tag("MainViewModel").d("Resetting state machine")

        tempMeasureJob?.cancel()
        resetJob?.cancel()
        handCooldownJob?.cancel()
        autoSubmitJob?.cancel()
        
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
        lastFaceFrameAt = 0L
        currentFacePath = null
        currentPalmPath = null
        currentBackPath = null
        currentFaceBitmap.safeRecycle()
        currentPalmBitmap.safeRecycle()
        currentBackBitmap.safeRecycle()
        currentFaceBitmap = null
        currentPalmBitmap = null
        currentBackBitmap = null
        faceSaveJob?.cancel()
        palmSaveJob?.cancel()
        backSaveJob?.cancel()
        faceSaveJob = null
        palmSaveJob = null
        backSaveJob = null
    }

    private fun captureHandPalmAndCooldown(
        frame: Bitmap,
        infos: List<com.smartcheck.sdk.HandInfo>,
        isIssue: Boolean = false
    ) {
        if (isIssue) {
            hardwareRepository.beep("error")
        } else {
            hardwareRepository.beep("success")
        }
        if (currentPalmBitmap == null) {
            val snapshot = frame.copy(Bitmap.Config.ARGB_8888, false)
            currentPalmBitmap = snapshot
            _uiState.update {
                it.copy(
                    handPalmInfos = infos,
                    handPalmFrameWidth = frame.width,
                    handPalmFrameHeight = frame.height,
                    handCapturePulse = true
                )
            }
            viewModelScope.launch {
                delay(180)
                _uiState.update { it.copy(handCapturePulse = false) }
            }
            palmSaveJob?.cancel()
            palmSaveJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = currentPalmBitmap ?: return@launch
                    val result = imageStorageUseCase.savePalmImage(bitmap)
                    result.onSuccess { name ->
                        currentPalmPath = name
                        _uiState.update { it.copy(handPalmPath = name) }
                    }.onFailure {
                        _uiState.update { it.copy(message = "照片保存失败") }
                    }
                    bitmap.recycle()
                    currentPalmBitmap = null
                } catch (e: Exception) {
                    Timber.tag("MainViewModel").e(e, "Failed to save hand palm snapshot")
                }
            }
        }

        handCooldownJob?.cancel()
        handCooldownJob = viewModelScope.launch {
            delay(2500)
            startHandBackCheck()
        }
    }

    private fun captureHandBackAndFinish(
        frame: Bitmap,
        infos: List<com.smartcheck.sdk.HandInfo>,
        issues: List<String> = emptyList(),
        isIssue: Boolean = false
    ) {
        if (!isIssue) {
            hardwareRepository.beep("success")
        }
        if (currentBackBitmap == null) {
            val snapshot = frame.copy(Bitmap.Config.ARGB_8888, false)
            currentBackBitmap = snapshot
            _uiState.update {
                it.copy(
                    handBackInfos = infos,
                    handBackFrameWidth = frame.width,
                    handBackFrameHeight = frame.height,
                    handCapturePulse = true
                )
            }
            viewModelScope.launch {
                delay(180)
                _uiState.update { it.copy(handCapturePulse = false) }
            }
            backSaveJob?.cancel()
            backSaveJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = currentBackBitmap ?: return@launch
                    val result = imageStorageUseCase.saveBackImage(bitmap)
                    result.onSuccess { name ->
                        currentBackPath = name
                        _uiState.update { it.copy(handBackPath = name) }
                    }.onFailure {
                        _uiState.update { it.copy(message = "照片保存失败") }
                    }
                    bitmap.recycle()
                    currentBackBitmap = null
                } catch (e: Exception) {
                    Timber.tag("MainViewModel").e(e, "Failed to save hand back snapshot")
                }
            }
        }
        if (isIssue) {
            _uiState.update {
                it.copy(
                    state = CheckState.SYMPTOM_CHECKING,
                    message = "手部检测异常，请人工复核",
                    autoSubmitRemainingSec = null
                )
            }
        } else {
            startAutoSubmitCountdown()
        }
    }

    private fun startAutoSubmitCountdown() {
        autoSubmitJob?.cancel()
        val totalSec = 3
        _uiState.update {
            it.copy(
                state = CheckState.AUTO_SUBMITTING,
                message = "即将自动提交",
                autoSubmitRemainingSec = totalSec,
                autoSubmitTotalSec = totalSec
            )
        }
        autoSubmitJob = viewModelScope.launch {
            for (sec in totalSec downTo 1) {
                _uiState.update { it.copy(autoSubmitRemainingSec = sec) }
                delay(1000)
            }
            autoSubmitJob = null
            onAllPass(remark = "无")
            finalizeCheckRecord()
        }
    }

    private fun calcRemainingDays(endAt: Long): Int {
        val now = System.currentTimeMillis()
        val diffMs = endAt - now
        return ceil(diffMs / (24f * 60f * 60f * 1000f)).toInt()
    }
    
    override fun onCleared() {
        super.onCleared()
        tempMeasureJob?.cancel()
        resetJob?.cancel()
        handCooldownJob?.cancel()
        autoSubmitJob?.cancel()
        voiceService.shutdown()
        Timber.tag("MainViewModel").d("MainViewModel cleared")
    }

    private fun Bitmap?.safeRecycle() {
        try {
            if (this != null && !isRecycled) {
                recycle()
            }
        } catch (e: Exception) {
            Timber.tag("MainViewModel").w(e, "Bitmap recycle failed")
        }
    }
}
