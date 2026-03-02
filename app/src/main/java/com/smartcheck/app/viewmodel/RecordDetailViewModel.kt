package com.smartcheck.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.repository.IRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val recordRepository: IRecordRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordId = savedStateHandle.get<String>("id")?.toLongOrNull()

    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()

    init {
        if (recordId != null) {
            viewModelScope.launch {
                recordRepository.getRecordById(recordId).fold(
                    onSuccess = { _record.value = it },
                    onFailure = { _record.value = null }
                )
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
            handStatus = com.smartcheck.app.domain.model.HandStatus.valueOf(handStatus),
            healthCertStatus = com.smartcheck.app.domain.model.HealthCertStatus.valueOf(healthCertStatus),
            symptomFlags = symptomFlags.split(",").mapNotNull {
                try { com.smartcheck.app.domain.model.SymptomType.valueOf(it) } catch (e: Exception) { null }
            },
            remark = remark
        )
        viewModelScope.launch {
            recordRepository.updateRecord(updated)
            _record.value = updated
        }
    }
}
