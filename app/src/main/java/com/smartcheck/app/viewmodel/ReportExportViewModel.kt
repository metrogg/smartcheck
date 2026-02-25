package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ReportExportViewModel @Inject constructor(
    recordRepository: RecordRepository
) : ViewModel() {

    val records: StateFlow<List<RecordEntity>> = recordRepository.getRecentRecords(1000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
