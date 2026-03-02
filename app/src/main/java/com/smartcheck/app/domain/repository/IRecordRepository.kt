package com.smartcheck.app.domain.repository

import com.smartcheck.app.domain.model.Record
import kotlinx.coroutines.flow.Flow

interface IRecordRepository {

    fun observeRecentRecords(limit: Int = 100): Flow<List<Record>>

    fun observeRecordsByUser(userId: Long): Flow<List<Record>>

    fun observeRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<Record>>

    suspend fun getRecordById(id: Long): Result<Record>

    suspend fun getTodayRecordByUser(userId: Long): Result<Record?>

    suspend fun saveRecord(record: Record): Result<Long>

    suspend fun updateRecord(record: Record): Result<Unit>

    suspend fun deleteOldRecords(beforeTime: Long): Result<Unit>

    suspend fun deleteAllRecords(): Result<Unit>
}
