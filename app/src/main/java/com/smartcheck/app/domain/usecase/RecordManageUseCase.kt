package com.smartcheck.app.domain.usecase

import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.repository.IRecordRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class RecordManageUseCase @Inject constructor(
    private val recordRepository: IRecordRepository
) {
    fun observeRecentRecords(limit: Int = 100): Flow<List<Record>> {
        return recordRepository.observeRecentRecords(limit)
    }

    fun observeRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<Record>> {
        return recordRepository.observeRecordsByDateRange(startTime, endTime)
    }

    fun observeRecordsByUser(userId: Long): Flow<List<Record>> {
        return recordRepository.observeRecordsByUser(userId)
    }

    suspend fun getRecordById(id: Long): Result<Record> {
        return recordRepository.getRecordById(id)
    }

    suspend fun getTodayRecordByUser(userId: Long): Result<Record?> {
        return recordRepository.getTodayRecordByUser(userId)
    }

    suspend fun deleteOldRecords(beforeTime: Long): Result<Unit> {
        Timber.d("Deleting records before: $beforeTime")
        return recordRepository.deleteOldRecords(beforeTime)
    }

    suspend fun deleteAllRecords(): Result<Unit> {
        Timber.w("Deleting all records")
        return recordRepository.deleteAllRecords()
    }

    suspend fun getRecordsCount(): Long {
        return observeRecentRecords(Int.MAX_VALUE).let { flow ->
            var count = 0L
            flow.collect { records ->
                count = records.size.toLong()
            }
            count
        }
    }
}
