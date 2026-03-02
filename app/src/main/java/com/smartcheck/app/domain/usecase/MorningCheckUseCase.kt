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
import timber.log.Timber
import javax.inject.Inject

class MorningCheckUseCase @Inject constructor(
    private val userRepository: IUserRepository,
    private val recordRepository: IRecordRepository,
    private val temperatureService: ITemperatureService,
    private val voiceService: IVoiceService
) {
    suspend fun recognizeFace(embedding: ByteArray): Result<User> {
        return userRepository.getUserByFaceFeature(embedding)
    }

    suspend fun checkHealthCert(user: User): Result<HealthCertStatus> {
        return Result.success(user.getHealthCertStatus())
    }

    suspend fun measureTemperature(): Result<TemperatureResult> {
        return try {
            val temperature = temperatureService.observeTemperature().first()
            val isNormal = temperature <= TEMP_THRESHOLD
            voiceService.speak(if (isNormal) "体温正常" else "体温异常")
            Result.success(TemperatureResult(temperature, isNormal))
        } catch (e: Exception) {
            Timber.e(e, "Temperature measurement failed")
            Result.failure(AppError.HardwareError("temperature", e.message ?: "unknown"))
        }
    }

    suspend fun observeTemperature(): Result<Unit> {
        return try {
            temperatureService.initialize()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Temperature service init failed")
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
            Timber.e(e, "Hand detection failed")
            Result.failure(AppError.DetectionError("hand", e.message ?: "unknown"))
        }
    }

    suspend fun submitSymptoms(userId: Long, symptoms: List<SymptomType>): Result<Unit> {
        val hasFever = symptoms.contains(SymptomType.FEVER)
        voiceService.speak(
            if (hasFever) "有发烧症状，禁止上岗" else "症状已记录"
        )
        return Result.success(Unit)
    }

    suspend fun saveRecord(
        userId: Long,
        userName: String,
        employeeId: String,
        temperature: Float,
        isTempNormal: Boolean,
        handCheckResult: HandCheckResult,
        symptoms: List<SymptomType>,
        healthCertStatus: HealthCertStatus
    ): Result<Record> {
        val record = Record(
            userId = userId,
            userName = userName,
            employeeId = employeeId,
            temperature = temperature,
            isTempNormal = isTempNormal,
            isHandNormal = handCheckResult.palmNormal && handCheckResult.backNormal,
            isPassed = isTempNormal &&
                    handCheckResult.palmNormal &&
                    handCheckResult.backNormal &&
                    healthCertStatus != HealthCertStatus.EXPIRED &&
                    !symptoms.contains(SymptomType.FEVER),
            handStatus = if (handCheckResult.palmNormal && handCheckResult.backNormal)
                HandStatus.NORMAL else HandStatus.ABNORMAL,
            healthCertStatus = healthCertStatus,
            symptomFlags = symptoms,
            handPalmPath = handCheckResult.palmImagePath,
            handBackPath = handCheckResult.backImagePath
        )

        return recordRepository.saveRecord(record).map { id ->
            record.copy(id = id)
        }
    }

    suspend fun checkTodayRecord(userId: Long): Result<Record?> {
        return recordRepository.getTodayRecordByUser(userId)
    }

    fun speak(text: String) {
        voiceService.speak(text)
    }

    companion object {
        const val TEMP_THRESHOLD = 37.5f
    }
}

data class TemperatureResult(
    val temperature: Float,
    val isNormal: Boolean
)

data class HandCheckResult(
    val palmNormal: Boolean,
    val backNormal: Boolean,
    val palmImagePath: String?,
    val backImagePath: String?,
    val abnormalType: String? = null
)
