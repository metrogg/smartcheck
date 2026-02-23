package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpRecordUploadReporter @Inject constructor() : RecordUploadReporter {
    override suspend fun upload(record: RecordEntity) {
        Timber.d("Record upload stub (no-op): id=${record.id} user=${record.userName} employeeId=${record.employeeId} passed=${record.isPassed}")
    }
}
