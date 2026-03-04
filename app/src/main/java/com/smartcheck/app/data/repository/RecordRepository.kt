package com.smartcheck.app.data.repository

import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.toDomain
import com.smartcheck.app.domain.model.toEntity
import com.smartcheck.app.domain.repository.IRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val recordDao: RecordDao
) : IRecordRepository {

    fun getRecentRecords(limit: Int = 100): Flow<List<RecordEntity>> = 
        recordDao.getRecentRecords(limit).map { entities -> entities }

    fun getRecordsByTimeRange(startTime: Long, endTime: Long): Flow<List<RecordEntity>> = 
        recordDao.getRecordsByTimeRange(startTime, endTime).map { entities -> entities }

    fun getRecordsByUser(userId: Long): Flow<List<RecordEntity>> = 
        recordDao.getRecordsByUser(userId).map { entities -> entities }

    suspend fun insertRecord(record: RecordEntity): Long = recordDao.insertRecord(record)

    suspend fun updateRecordEntity(record: RecordEntity) = recordDao.updateRecord(record)

    override fun observeRecentRecords(limit: Int): Flow<List<Record>> {
        return recordDao.getRecentRecords(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeRecordsByUser(userId: Long): Flow<List<Record>> {
        return recordDao.getRecordsByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<Record>> {
        return recordDao.getRecordsByTimeRange(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecordById(id: Long): Result<Record> {
        return try {
            val entity = recordDao.getRecordById(id)
            if (entity != null) {
                Result.success(entity.toDomain())
            } else {
                Result.failure(AppError.NotFound)
            }
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "getRecordById failed"))
        }
    }

    override suspend fun getTodayRecordByUser(userId: Long): Result<Record?> {
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis

            val records = recordDao.getRecordsByUser(userId)
            var todayRecord: RecordEntity? = null
            records.collect { entities ->
                todayRecord = entities.firstOrNull { it.checkTime >= todayStart }
            }

            Result.success(todayRecord?.toDomain())
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "getTodayRecordByUser failed"))
        }
    }

    override suspend fun saveRecord(record: Record): Result<Long> {
        return try {
            val id = recordDao.insertRecord(record.toEntity())
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "saveRecord failed"))
        }
    }

    override suspend fun updateRecord(record: Record): Result<Unit> {
        return try {
            recordDao.updateRecord(record.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "updateRecord failed"))
        }
    }

    override suspend fun deleteOldRecords(beforeTime: Long): Result<Unit> {
        return try {
            recordDao.deleteOldRecords(beforeTime)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "deleteOldRecords failed"))
        }
    }

    override suspend fun deleteAllRecords(): Result<Unit> {
        return try {
            recordDao.deleteAllRecords()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.UnknownError(e.message ?: "deleteAllRecords failed"))
        }
    }

    // 同步方法，用于 API 接口
    suspend fun getRecordsByTimeRangeSync(startTime: Long, endTime: Long): List<RecordEntity> {
        return recordDao.getRecordsByTimeRangeSync(startTime, endTime)
    }

    suspend fun getRecordByIdSync(id: Long): RecordEntity? {
        return recordDao.getRecordById(id)
    }

    suspend fun getRecordsAfterId(lastId: Long, limit: Int): List<RecordEntity> {
        return recordDao.getRecordsAfterId(lastId, limit)
    }
}
