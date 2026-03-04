package com.smartcheck.app.data.db

import androidx.room.*

@Dao
interface ApiTokenDao {

    @Insert
    suspend fun insertToken(token: ApiTokenEntity)

    @Query("SELECT * FROM api_tokens WHERE token = :token AND isRevoked = 0 AND expiresAt > :currentTime")
    suspend fun getValidToken(token: String, currentTime: Long = System.currentTimeMillis()): ApiTokenEntity?

    @Query("UPDATE api_tokens SET lastUsedAt = :currentTime WHERE token = :token")
    suspend fun updateLastUsed(token: String, currentTime: Long = System.currentTimeMillis())

    @Query("UPDATE api_tokens SET isRevoked = 1 WHERE token = :token")
    suspend fun revokeToken(token: String)

    @Query("UPDATE api_tokens SET isRevoked = 1 WHERE expiresAt < :currentTime")
    suspend fun revokeExpiredTokens(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM api_tokens WHERE isRevoked = 1 OR expiresAt < :currentTime")
    suspend fun deleteInvalidTokens(currentTime: Long = System.currentTimeMillis())

    @Query("SELECT * FROM api_tokens WHERE userId = :userId AND isRevoked = 0 ORDER BY createdAt DESC")
    suspend fun getActiveTokensByUser(userId: Long): List<ApiTokenEntity>
}
