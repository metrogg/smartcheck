package com.smartcheck.app.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UserManageUseCaseTest {

    private lateinit var userRepository: IUserRepository
    private lateinit var useCase: UserManageUseCase

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        useCase = UserManageUseCase(userRepository)
    }

    @Test
    fun `observeAllUsers returns flow from repository`() = runTest {
        // Given
        val users = listOf(
            User(id = 1, name = "张三", employeeId = "E001"),
            User(id = 2, name = "李四", employeeId = "E002")
        )
        coEvery { userRepository.observeAllUsers() } returns flowOf(users)

        // When
        val result = useCase.observeAllUsers().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("张三", result[0].name)
    }

    @Test
    fun `createUser fails when name is blank`() = runTest {
        // Given
        val user = User(name = "", employeeId = "E001")

        // When
        val result = useCase.createUser(user)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `createUser fails when employeeId is blank`() = runTest {
        // Given
        val user = User(name = "张三", employeeId = "")

        // When
        val result = useCase.createUser(user)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `createUser fails when employeeId already exists`() = runTest {
        // Given
        val existingUser = User(id = 1, name = "王五", employeeId = "E001")
        coEvery { userRepository.getUserByEmployeeId("E001") } returns Result.success(existingUser)
        
        val newUser = User(name = "张三", employeeId = "E001")

        // When
        val result = useCase.createUser(newUser)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `createUser succeeds when valid`() = runTest {
        // Given
        coEvery { userRepository.getUserByEmployeeId("E001") } returns Result.failure(AppError.NotFound)
        coEvery { userRepository.createUser(any()) } returns Result.success(1L)
        
        val newUser = User(name = "张三", employeeId = "E001")

        // When
        val result = useCase.createUser(newUser)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.createUser(newUser) }
    }

    @Test
    fun `deleteUser calls repository`() = runTest {
        // Given
        coEvery { userRepository.deleteUser(1L) } returns Result.success(Unit)

        // When
        val result = useCase.deleteUser(1L)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.deleteUser(1L) }
    }
}
