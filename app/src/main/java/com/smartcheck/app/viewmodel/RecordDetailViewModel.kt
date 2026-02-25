package com.smartcheck.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordId = savedStateHandle.get<String>("id")?.toLongOrNull()

    private val _record = MutableStateFlow<RecordEntity?>(null)
    val record: StateFlow<RecordEntity?> = _record.asStateFlow()

    init {
        if (recordId != null) {
            viewModelScope.launch {
                _record.value = recordRepository.getRecordById(recordId)
            }
        }
    }

    fun updateRecord(
        temperature: Float,
        handStatus: String,
        healthCertStatus: String,
        symptomFlags: String,
        remark: String
    ) {
        val current = _record.value ?: return
        val updated = current.copy(
            temperature = temperature,
            handStatus = handStatus,
            healthCertStatus = healthCertStatus,
            symptomFlags = symptomFlags,
            remark = remark
        )
        viewModelScope.launch {
            recordRepository.updateRecord(updated)
            _record.value = updated
        }
    }
}
