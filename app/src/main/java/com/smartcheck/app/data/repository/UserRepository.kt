package com.smartcheck.app.data.repository

import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.db.UserEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun getAllActiveUsers(): Flow<List<UserEntity>> = userDao.getAllActiveUsers()
    
    suspend fun getUserById(userId: Long): UserEntity? = userDao.getUserById(userId)
    
    suspend fun getUserByEmployeeId(employeeId: String): UserEntity? = 
        userDao.getUserByEmployeeId(employeeId)
    
    suspend fun insertUser(user: UserEntity): Long = userDao.insertUser(user)
    
    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)
    
    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)
}
