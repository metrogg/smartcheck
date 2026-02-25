package com.smartcheck.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    
    @Query("SELECT * FROM check_records ORDER BY checkTime DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 100): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM check_records WHERE userId = :userId ORDER BY checkTime DESC")
    fun getRecordsByUser(userId: Long): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM check_records WHERE checkTime >= :startTime AND checkTime <= :endTime ORDER BY checkTime DESC")
    fun getRecordsByTimeRange(startTime: Long, endTime: Long): Flow<List<RecordEntity>>
    
    @Insert
    suspend fun insertRecord(record: RecordEntity): Long

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Query("SELECT * FROM check_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Long): RecordEntity?
    
    @Query("DELETE FROM check_records WHERE checkTime < :beforeTime")
    suspend fun deleteOldRecords(beforeTime: Long)

    @Query("DELETE FROM check_records")
    suspend fun deleteAllRecords()
}
