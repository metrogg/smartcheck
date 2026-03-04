package com.smartcheck.app.domain.usecase

import android.graphics.Bitmap
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.SymptomType
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.ITemperatureService
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.domain.repository.IVoiceService
import com.smartcheck.sdk.HandInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import timber.log.Timber
import javax.inject.Inject

class MorningCheckUseCase @Inject constructor(
    private val userRepository: IUserRepository,
    private val recordRepository: IRecordRepository,
    private val temperatureService: ITemperatureService,
    private val voiceService: IVoiceService
) {
    suspend fun getUserWithHealthCheck(userId: Long): Result<UserCheckResult> {
        return userRepository.getUserById(userId).map { user ->
            val healthStatus = user.getHealthCertStatus()
            val daysRemaining = user.getHealthCertDaysRemaining()
            UserCheckResult(
                user = user,
                healthCertStatus = healthStatus,
                healthCertDaysRemaining = daysRemaining,
                isHealthCertExpiringSoon = daysRemaining != null && daysRemaining < 7
            )
        }
    }

    suspend fun checkHealthCert(user: User): Result<HealthCertStatus> {
        return Result.success(user.getHealthCertStatus())
    }

    suspend fun measureTemperatureFlow(readings: Int = 3): Result<TemperatureMeasurementResult> {
        return try {
            var lastTemp = 0f
            temperatureService.observeTemperature()
                .take(readings)
                .collect { temp ->
                    lastTemp = temp
                }
            
            val isNormal = lastTemp < TEMP_THRESHOLD
            val message = if (isNormal) "体温正常" else "体温异常：${String.format("%.1f", lastTemp)}°C"
            
            Result.success(TemperatureMeasurementResult(
                temperature = lastTemp,
                isNormal = isNormal,
                message = message
            ))
        } catch (e: Exception) {
            Timber.tag("MorningCheck").e(e, "Temperature measurement failed")
            Result.failure(AppError.HardwareError("temperature", e.message ?: "unknown"))
        }
    }

    suspend fun measureTemperature(): Result<TemperatureResult> {
        return try {
            val temperature = temperatureService.observeTemperature().first()
            val isNormal = temperature <= TEMP_THRESHOLD
            voiceService.speak(if (isNormal) "体温正常" else "体温异常")
            Result.success(TemperatureResult(temperature, isNormal))
        } catch (e: Exception) {
            Timber.tag("MorningCheck").e(e, "Temperature measurement failed")
            Result.failure(AppError.HardwareError("temperature", e.message ?: "unknown"))
        }
    }

    suspend fun observeTemperature(): Result<Unit> {
        return try {
            temperatureService.initialize()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("MorningCheck").e(e, "Temperature service init failed")
            Result.failure(AppError.HardwareNotReady)
        }
    }

    suspend fun checkHand(
        palmBitmap: Bitmap,
        backBitmap: Bitmap,
        detect: (Bitmap) -> List<HandInfo>
    ): Result<HandCheckResult> {
        return try {
            val palmResults = detect(palmBitmap)
            val backResults = detect(backBitmap)

            val palmNormal = palmResults.isEmpty()
            val backNormal = backResults.isEmpty()

            val abnormalType = when {
                !palmNormal -> "palm_abnormal"
                !backNormal -> "back_abnormal"
                else -> null
            }

            voiceService.speak(
                when {
                    !palmNormal || !backNormal -> "手部异常"
                    else -> "手部正常"
                }
            )

            Result.success(HandCheckResult(
                palmNormal = palmNormal,
                backNormal = backNormal,
                palmImagePath = null,
                backImagePath = null,
                abnormalType = abnormalType
            ))
        } catch (e: Exception) {
            Timber.tag("MorningCheck").e(e, "Hand detection failed")
            Result.failure(AppError.DetectionError("hand", e.message ?: "unknown"))
        }
    }

    fun analyzeHandDetectionResults(infos: List<HandInfo>): HandDetectionAnalysis {
        val hasForeignObject = infos.any { it.hasForeignObject }
        val issues = if (hasForeignObject) {
            infos.filter { it.hasForeignObject }.map { it.label }.ifEmpty { infos.map { it.label } }
        } else {
            emptyList()
        }
        return HandDetectionAnalysis(
            hasIssue = hasForeignObject,
            issues = issues,
            isPassing = !hasForeignObject
        )
    }

    fun processSymptomSubmission(symptoms: List<String>): SymptomSubmissionResult {
        val trimmed = symptoms.map { it.trim() }.filter { it.isNotEmpty() }
        
        if (trimmed.isEmpty()) {
            speak("无不适症状")
            return SymptomSubmissionResult(
                isAllPass = true,
                hasFever = false,
                message = "晨检通过！"
            )
        }
        
        val hasFever = trimmed.any { it.contains("发烧") || it.contains("发热") || it.contains("体温") }
        
        if (hasFever) {
            speak("有发烧症状，禁止上岗")
            return SymptomSubmissionResult(
                isAllPass = false,
                hasFever = true,
                message = "有不适症状：${trimmed.joinToString(", ")}"
            )
        }
        
        speak("症状已记录")
        return SymptomSubmissionResult(
            isAllPass = false,
            hasFever = false,
            message = "有不适症状：${trimmed.joinToString(", ")}"
        )
    }

    fun calculateHealthCertStatus(remainingDays: Int?): HealthCertStatus {
        return when {
            remainingDays == null -> HealthCertStatus.VALID
            remainingDays < 0 -> HealthCertStatus.EXPIRED
            remainingDays < 7 -> HealthCertStatus.EXPIRING_SOON
            else -> HealthCertStatus.VALID
        }
    }

    suspend fun submitSymptoms(symptoms: List<SymptomType>): SymptomSubmitResult {
        val hasFever = symptoms.contains(SymptomType.FEVER)
        val hasOtherSymptoms = symptoms.isNotEmpty()
        
        voiceService.speak(
            when {
                hasFever -> "有发烧症状，禁止上岗"
                hasOtherSymptoms -> "症状已记录"
                else -> "无不适症状"
            }
        )
        
        return SymptomSubmitResult(
            hasFever = hasFever,
            hasOtherSymptoms = hasOtherSymptoms,
            shouldBlockWork = hasFever
        )
    }

    suspend fun saveRecord(
        userId: Long,
        userName: String,
        employeeId: String,
        temperature: Float,
        isTempNormal: Boolean,
        handCheckResult: HandCheckResult,
        symptoms: List<SymptomType>,
        healthCertStatus: HealthCertStatus,
        faceImagePath: String? = null,
        palmImagePath: String? = null,
        backImagePath: String? = null
    ): Result<Record> {
        val isPassed = calculateIsPassed(
            isTempNormal = isTempNormal,
            handCheckResult = handCheckResult,
            healthCertStatus = healthCertStatus,
            symptoms = symptoms
        )

        val record = Record(
            userId = userId,
            userName = userName,
            employeeId = employeeId,
            temperature = temperature,
            isTempNormal = isTempNormal,
            isHandNormal = handCheckResult.palmNormal && handCheckResult.backNormal,
            isPassed = isPassed,
            handStatus = if (handCheckResult.palmNormal && handCheckResult.backNormal)
                HandStatus.NORMAL else HandStatus.ABNORMAL,
            healthCertStatus = healthCertStatus,
            symptomFlags = symptoms,
            faceImagePath = faceImagePath,
            handPalmPath = palmImagePath,
            handBackPath = backImagePath
        )

        return recordRepository.saveRecord(record).map { id ->
            record.copy(id = id)
        }
    }

    fun calculateIsPassed(
        isTempNormal: Boolean,
        handCheckResult: HandCheckResult,
        healthCertStatus: HealthCertStatus,
        symptoms: List<SymptomType>
    ): Boolean {
        return isTempNormal &&
                handCheckResult.palmNormal &&
                handCheckResult.backNormal &&
                healthCertStatus != HealthCertStatus.EXPIRED &&
                !symptoms.contains(SymptomType.FEVER)
    }

    suspend fun checkTodayRecord(userId: Long): Result<Record?> {
        return recordRepository.getTodayRecordByUser(userId)
    }

    suspend fun onFaceRecognized(userId: Long, userName: String, confidence: Float): FaceRecognizedResult {
        val userResult = userRepository.getUserById(userId)
        val user = userResult.getOrNull()
        val healthCertEndDate = user?.healthCertEndDate
        val remainingDays = healthCertEndDate?.let { user.getHealthCertDaysRemaining() }?.toInt()

        val healthCertStatus = when {
            remainingDays == null -> HealthCertStatus.VALID
            remainingDays < 0 -> HealthCertStatus.EXPIRED
            remainingDays < 7 -> HealthCertStatus.EXPIRING_SOON
            else -> HealthCertStatus.VALID
        }

        val isAllowedToContinue = healthCertStatus != HealthCertStatus.EXPIRED

        if (!isAllowedToContinue) {
            speakHealthCertExpired()
        } else if (remainingDays != null && remainingDays < 7) {
            speakHealthCertWarning()
        }

        if (isAllowedToContinue) {
            speakSuccess(userName)
        }

        return FaceRecognizedResult(
            userId = userId,
            userName = userName,
            healthCertEndDate = healthCertEndDate,
            healthCertDaysRemaining = remainingDays,
            healthCertStatus = healthCertStatus,
            isAllowedToContinue = isAllowedToContinue,
            faceConfidence = confidence,
            message = when (healthCertStatus) {
                HealthCertStatus.EXPIRED -> "健康证已过期，禁止上岗"
                HealthCertStatus.EXPIRING_SOON -> "欢迎，$userName，健康证即将到期"
                else -> "欢迎，$userName"
            }
        )
    }

    fun speakHealthCertExpired() {
        voiceService.speak("健康证已过期，禁止上岗")
    }

    fun speak(text: String) {
        voiceService.speak(text)
    }

    fun speakSuccess(userName: String) {
        voiceService.speak("欢迎，$userName")
    }

    fun speakHealthCertWarning() {
        voiceService.speak("健康证即将到期")
    }

    fun speakTemperatureNormal() {
        voiceService.speak("体温正常，请准备手部检测")
    }

    fun speakTemperatureAbnormal(temp: Float) {
        voiceService.speak("体温异常，请复测")
    }

    fun speakHandCheckPass() {
        voiceService.speak("请回答健康询问")
    }

    fun speakHandCheckFail() {
        voiceService.speak("手部检测不合格")
    }

    fun speakAllPass() {
        voiceService.speak("晨检成功，祝您工作愉快")
    }

    fun speakSymptomFail() {
        voiceService.speak("请人工复核")
    }

    companion object {
        const val TEMP_THRESHOLD = 37.3f
    }
}

data class HandDetectionAnalysis(
    val hasIssue: Boolean,
    val issues: List<String>,
    val isPassing: Boolean
)

data class SymptomSubmissionResult(
    val isAllPass: Boolean,
    val hasFever: Boolean,
    val message: String
)

data class UserCheckResult(
    val user: User,
    val healthCertStatus: HealthCertStatus,
    val healthCertDaysRemaining: Long?,
    val isHealthCertExpiringSoon: Boolean
)

data class TemperatureResult(
    val temperature: Float,
    val isNormal: Boolean
)

data class TemperatureMeasurementResult(
    val temperature: Float,
    val isNormal: Boolean,
    val message: String
)

data class HandCheckResult(
    val palmNormal: Boolean,
    val backNormal: Boolean,
    val palmImagePath: String?,
    val backImagePath: String?,
    val abnormalType: String? = null
)

data class SymptomSubmitResult(
    val hasFever: Boolean,
    val hasOtherSymptoms: Boolean,
    val shouldBlockWork: Boolean
)

data class FaceRecognizedResult(
    val userId: Long,
    val userName: String,
    val healthCertEndDate: Long?,
    val healthCertDaysRemaining: Int?,
    val healthCertStatus: HealthCertStatus,
    val isAllowedToContinue: Boolean,
    val faceConfidence: Float,
    val message: String
)
