package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordEntity

interface RecordUploadReporter {
    suspend fun upload(record: RecordEntity)
}
