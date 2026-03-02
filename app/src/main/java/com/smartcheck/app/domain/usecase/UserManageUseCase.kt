package com.smartcheck.app.domain.usecase

import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class UserManageUseCase @Inject constructor(
    private val userRepository: IUserRepository
) {
    fun observeAllUsers(): Flow<List<User>> {
        return userRepository.observeAllUsers()
    }

    suspend fun getUserById(id: Long): Result<User> {
        return userRepository.getUserById(id)
    }

    suspend fun getUserByEmployeeId(employeeId: String): Result<User> {
        return userRepository.getUserByEmployeeId(employeeId)
    }

    suspend fun getUserByFaceFeature(embedding: ByteArray): Result<User> {
        return userRepository.getUserByFaceFeature(embedding)
    }

    suspend fun createUser(user: User): Result<Long> {
        if (user.name.isBlank()) {
            return Result.failure(AppError.ValidationError("name", "姓名不能为空"))
        }
        if (user.employeeId.isBlank()) {
            return Result.failure(AppError.ValidationError("employeeId", "工号不能为空"))
        }

        val existing = userRepository.getUserByEmployeeId(user.employeeId)
        if (existing.isSuccess) {
            return Result.failure(AppError.DuplicateError("employeeId", user.employeeId))
        }

        Timber.d("Creating user: ${user.name}, employeeId: ${user.employeeId}")
        return userRepository.createUser(user)
    }

    suspend fun updateUser(user: User): Result<Unit> {
        if (user.name.isBlank()) {
            return Result.failure(AppError.ValidationError("name", "姓名不能为空"))
        }

        Timber.d("Updating user: ${user.id}, name: ${user.name}")
        return userRepository.updateUser(user)
    }

    suspend fun deleteUser(userId: Long): Result<Unit> {
        Timber.d("Deleting user: $userId")
        return userRepository.deleteUser(userId)
    }

    suspend fun deactivateUser(userId: Long): Result<Unit> {
        return userRepository.getUserById(userId).fold(
            onSuccess = { user ->
                userRepository.updateUser(user.copy(isActive = false))
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun activateUser(userId: Long): Result<Unit> {
        return userRepository.getUserById(userId).fold(
            onSuccess = { user ->
                userRepository.updateUser(user.copy(isActive = true))
            },
            onFailure = { Result.failure(it) }
        )
    }
}
