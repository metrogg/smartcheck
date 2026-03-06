package com.smartcheck.app.domain.repository

import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface IUserRepository {

    fun observeAllUsers(): Flow<List<User>>

    suspend fun getUserById(id: Long): Result<User>

    suspend fun getUserByEmployeeId(employeeId: String): Result<User>

    suspend fun getUsersAfterId(lastId: Long, limit: Int): List<User>

    suspend fun getUserByFaceFeature(embedding: ByteArray): Result<User>

    suspend fun createUser(user: User): Result<Long>

    suspend fun updateUser(user: User): Result<Unit>

    suspend fun deleteUser(userId: Long): Result<Unit>

    suspend fun deleteUserByEmployeeId(employeeId: String): Result<Unit>

    suspend fun deleteAllUsers(): Result<Unit>
}
