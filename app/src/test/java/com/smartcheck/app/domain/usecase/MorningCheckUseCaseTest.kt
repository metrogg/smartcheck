package com.smartcheck.app.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.ITemperatureService
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.domain.repository.IVoiceService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MorningCheckUseCaseTest {

    private lateinit var userRepository: IUserRepository
    private lateinit var recordRepository: IRecordRepository
    private lateinit var temperatureService: ITemperatureService
    private lateinit var voiceService: IVoiceService
    private lateinit var useCase: MorningCheckUseCase

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        recordRepository = mockk(relaxed = true)
        temperatureService = mockk(relaxed = true)
        voiceService = mockk(relaxed = true)
        
        useCase = MorningCheckUseCase(
            userRepository = userRepository,
            recordRepository = recordRepository,
            temperatureService = temperatureService,
            voiceService = voiceService
        )
    }

    @Test
    fun `onFaceRecognized returns result with user info when found`() = runTest {
        // Given
        val userId = 1L
        val userName = "张三"
        val confidence = 0.95f
        val expectedUser = User(
            id = userId,
            name = userName,
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )
        coEvery { userRepository.getUserById(userId) } returns Result.success(expectedUser)

        // When
        val result = useCase.onFaceRecognized(userId, userName, confidence)

        // Then
        assertEquals(userId, result.userId)
        assertEquals(userName, result.userName)
        assertEquals(confidence, result.faceConfidence)
        coVerify { voiceService.speak("欢迎，$userName") }
    }

    @Test
    fun `onFaceRecognized speaks health cert warning when expiring soon`() = runTest {
        // Given
        val userId = 1L
        val userName = "张三"
        val confidence = 0.95f
        val expectedUser = User(
            id = userId,
            name = userName,
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        )
        coEvery { userRepository.getUserById(userId) } returns Result.success(expectedUser)

        // When
        val result = useCase.onFaceRecognized(userId, userName, confidence)

        // Then
        assertTrue(result.healthCertDaysRemaining!! < 7)
        coVerify { voiceService.speak("健康证即将到期") }
    }

    @Test
    fun `checkHealthCert returns valid status`() = runTest {
        // Given
        val user = User(
            id = 1L,
            name = "张三",
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )

        // When
        val result = useCase.checkHealthCert(user)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `speak calls voiceService`() {
        // Given
        val message = "测试消息"

        // When
        useCase.speak(message)

        // Then
        coVerify { voiceService.speak(message) }
    }

    @Test
    fun `speakSuccess calls voiceService with welcome message`() {
        // Given
        val userName = "张三"

        // When
        useCase.speakSuccess(userName)

        // Then
        coVerify { voiceService.speak("欢迎，$userName") }
    }

    @Test
    fun `speakHealthCertWarning calls voiceService`() {
        // When
        useCase.speakHealthCertWarning()

        // Then
        coVerify { voiceService.speak("健康证即将到期") }
    }
}
