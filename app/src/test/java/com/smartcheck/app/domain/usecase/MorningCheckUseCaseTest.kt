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
    fun `recognizeFace returns user when found`() = runTest {
        // Given
        val embedding = ByteArray(512) { it.toByte() }
        val expectedUser = User(
            id = 1L,
            name = "张三",
            employeeId = "EMP001"
        )
        coEvery { userRepository.getUserByFaceFeature(embedding) } returns Result.success(expectedUser)

        // When
        val result = useCase.recognizeFace(embedding)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedUser, result.getOrNull())
    }

    @Test
    fun `recognizeFace returns failure when not found`() = runTest {
        // Given
        val embedding = ByteArray(512) { it.toByte() }
        coEvery { userRepository.getUserByFaceFeature(embedding) } returns Result.failure(
            mockk(relaxed = true)
        )

        // When
        val result = useCase.recognizeFace(embedding)

        // Then
        assertTrue(result.isFailure)
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
}
