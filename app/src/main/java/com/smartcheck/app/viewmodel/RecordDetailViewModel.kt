package com.smartcheck.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.SymptomType
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
        
        val isTempNormal = temperature < 37.3f
        val isHandNormal = handStatus.equals("NORMAL", ignoreCase = true)
        val isPassed = isTempNormal && isHandNormal
        
        val parsedHandStatus = try { HandStatus.valueOf(handStatus.uppercase()) } catch (e: Exception) { HandStatus.NOT_CHECKED }
        val parsedHealthCertStatus = try { HealthCertStatus.valueOf(healthCertStatus.uppercase()) } catch (e: Exception) { HealthCertStatus.VALID }
        val parsedSymptomFlags = symptomFlags.split(",").mapNotNull {
            try { SymptomType.valueOf(it.trim().uppercase()) } catch (e: Exception) { null }
        }
        
        val updated = current.copy(
            temperature = temperature,
            isTempNormal = isTempNormal,
            isHandNormal = isHandNormal,
            isPassed = isPassed,
            handStatus = parsedHandStatus,
            healthCertStatus = parsedHealthCertStatus,
            symptomFlags = parsedSymptomFlags,
            remark = remark
        )
        viewModelScope.launch {
            recordRepository.updateRecord(updated)
            _record.value = updated
        }
    }
}
