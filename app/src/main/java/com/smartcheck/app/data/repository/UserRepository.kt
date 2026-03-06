package com.smartcheck.app.data.repository

import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.model.toDomain
import com.smartcheck.app.domain.model.toEntity
import com.smartcheck.app.domain.repository.IUserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) : IUserRepository {

    override fun observeAllUsers(): Flow<List<User>> {
        return userDao.getAllActiveUsers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getUserById(id: Long): Result<User> {
        return try {
            val entity = userDao.getUserById(id)
            if (entity != null) {
                Result.success(entity.toDomain())
            } else {
                Result.failure(AppError.NotFound)
            }
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "getUserById failed"))
        }
    }

    override suspend fun getUserByEmployeeId(employeeId: String): Result<User> {
        return try {
            val entity = userDao.getUserByEmployeeId(employeeId)
            if (entity != null) {
                Result.success(entity.toDomain())
            } else {
                Result.failure(AppError.NotFound)
            }
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "getUserByEmployeeId failed"))
        }
    }

    override suspend fun getUsersAfterId(lastId: Long, limit: Int): List<User> {
        return try {
            userDao.getUsersAfterId(lastId, limit).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getUserByFaceFeature(embedding: ByteArray): Result<User> {
        return Result.failure(AppError.NoTargetDetected)
    }

    override suspend fun createUser(user: User): Result<Long> {
        return try {
            val id = userDao.insertUser(user.toEntity())
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "createUser failed"))
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            userDao.updateUser(user.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "updateUser failed"))
        }
    }

    override suspend fun deleteUser(userId: Long): Result<Unit> {
        return try {
            userDao.deleteUserById(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "deleteUser failed"))
        }
    }

    override suspend fun deleteUserByEmployeeId(employeeId: String): Result<Unit> {
        return try {
            userDao.deleteUserByEmployeeId(employeeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "deleteUserByEmployeeId failed"))
        }
    }

    override suspend fun deleteAllUsers(): Result<Unit> {
        return try {
            userDao.deleteAllUsers()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "deleteAllUsers failed"))
        }
    }
}
