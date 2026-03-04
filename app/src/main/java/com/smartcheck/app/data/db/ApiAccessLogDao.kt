package com.smartcheck.app.data.db

import androidx.room.*

@Dao
interface ApiAccessLogDao {

    @Insert
    suspend fun insertLog(log: ApiAccessLogEntity)

    @Query("SELECT * FROM api_access_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<ApiAccessLogEntity>

    @Query("SELECT * FROM api_access_logs WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLogsByUser(userId: Long, limit: Int = 100): List<ApiAccessLogEntity>

    @Query("SELECT * FROM api_access_logs WHERE endpoint = :endpoint ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLogsByEndpoint(endpoint: String, limit: Int = 100): List<ApiAccessLogEntity>

    @Query("DELETE FROM api_access_logs WHERE createdAt < :beforeTime")
    suspend fun deleteOldLogs(beforeTime: Long)

    @Query("SELECT COUNT(*) FROM api_access_logs WHERE ipAddress = :ip AND createdAt > :since")
    suspend fun getRequestCountByIp(ip: String, since: Long): Int
}
