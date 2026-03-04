package com.smartcheck.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 系统账号实体（用于 API 登录和账号同步）
 */
@Entity(tableName = "system_users")
data class SystemUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val username: String,
    val passwordHash: String,
    val passwordType: String = "plain",
    val employeeId: String? = null,
    val role: String = "employee",
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
