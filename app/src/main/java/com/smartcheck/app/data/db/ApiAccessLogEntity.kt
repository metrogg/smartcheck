package com.smartcheck.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * API 访问日志实体
 * 记录所有接口调用情况
 */
@Entity(tableName = "api_access_logs")
data class ApiAccessLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val endpoint: String,
    val method: String,
    val requestParams: String? = null,
    val responseCode: Int,
    val responseMessage: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val ipAddress: String? = null,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
