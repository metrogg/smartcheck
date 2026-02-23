package com.smartcheck.app.data.repository

import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.RecordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val recordDao: RecordDao
) {
    fun getRecentRecords(limit: Int = 100): Flow<List<RecordEntity>> = 
        recordDao.getRecentRecords(limit)
    
    fun getRecordsByUser(userId: Long): Flow<List<RecordEntity>> = 
        recordDao.getRecordsByUser(userId)
    
    fun getRecordsByTimeRange(startTime: Long, endTime: Long): Flow<List<RecordEntity>> = 
        recordDao.getRecordsByTimeRange(startTime, endTime)
    
    suspend fun insertRecord(record: RecordEntity): Long = recordDao.insertRecord(record)
    
    suspend fun deleteOldRecords(beforeTime: Long) = recordDao.deleteOldRecords(beforeTime)
}
