package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 管理界面 ViewModel
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val recordRepository: RecordRepository
) : ViewModel() {
    
    val recentRecords: Flow<List<RecordEntity>> = recordRepository.getRecentRecords(50)
}
