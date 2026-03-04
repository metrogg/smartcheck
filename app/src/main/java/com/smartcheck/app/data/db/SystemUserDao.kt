package com.smartcheck.app.data.db

import androidx.room.*

@Dao
interface SystemUserDao {

    @Query("SELECT * FROM system_users WHERE username = :username AND status = 'active' LIMIT 1")
    suspend fun getActiveUserByUsername(username: String): SystemUserEntity?

    @Query("SELECT * FROM system_users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): SystemUserEntity?

    @Query("SELECT * FROM system_users ORDER BY createdAt DESC")
    suspend fun getAllUsers(): List<SystemUserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: SystemUserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<SystemUserEntity>)

    @Update
    suspend fun updateUser(user: SystemUserEntity)

    @Query("DELETE FROM system_users WHERE username = :username")
    suspend fun deleteUserByUsername(username: String)

    @Query("DELETE FROM system_users")
    suspend fun deleteAllUsers()

    @Query("SELECT COUNT(*) FROM system_users")
    suspend fun getUserCount(): Int
}
