package com.smartcheck.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * API Token 实体
 * 用于存储第三方系统调用的认证令牌
 */
@Entity(tableName = "api_tokens")
data class ApiTokenEntity(
    @PrimaryKey
    val token: String,
    val userId: Long,
    val username: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val isRevoked: Boolean = false,
    val lastUsedAt: Long? = null
)
