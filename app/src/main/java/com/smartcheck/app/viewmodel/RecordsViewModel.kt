package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val recordRepository: RecordRepository
) : ViewModel() {

    enum class TimeFilter {
        TODAY,
        WEEK,
        MONTH,
        ALL
    }

    private val _timeFilter = MutableStateFlow(TimeFilter.TODAY)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _handStatus = MutableStateFlow<Set<String>>(emptySet())
    val handStatus: StateFlow<Set<String>> = _handStatus.asStateFlow()

    private val _healthCertStatus = MutableStateFlow<Set<String>>(emptySet())
    val healthCertStatus: StateFlow<Set<String>> = _healthCertStatus.asStateFlow()

    private val _symptomFlags = MutableStateFlow<Set<String>>(emptySet())
    val symptomFlags: StateFlow<Set<String>> = _symptomFlags.asStateFlow()

    val records: StateFlow<List<RecordEntity>> =
        combine(timeFilter, query, handStatus, healthCertStatus, symptomFlags) { filter, q, hand, cert, symptoms ->
            FilterState(filter, q, hand, cert, symptoms)
        }
            .flatMapLatest { filterState ->
                val now = System.currentTimeMillis()
                val base = when (filterState.filter) {
                    TimeFilter.TODAY -> {
                        val start = now - TimeUnit.DAYS.toMillis(1)
                        recordRepository.getRecordsByTimeRange(start, now)
                    }

                    TimeFilter.WEEK -> {
                        val start = now - TimeUnit.DAYS.toMillis(7)
                        recordRepository.getRecordsByTimeRange(start, now)
                    }

                    TimeFilter.MONTH -> {
                        val start = now - TimeUnit.DAYS.toMillis(30)
                        recordRepository.getRecordsByTimeRange(start, now)
                    }

                    TimeFilter.ALL -> {
                        recordRepository.getRecentRecords(500)
                    }
                }

                base.map { list ->
                    val trimmed = filterState.query.trim()
                    if (trimmed.isEmpty()) {
                        list
                    } else {
                        val lower = trimmed.lowercase()
                        list.filter {
                            it.userName.lowercase().contains(lower) ||
                                it.employeeId.lowercase().contains(lower)
                        }
                    }.filter { record ->
                        val handOk = filterState.handStatus.isEmpty() || filterState.handStatus.contains(record.handStatus)
                        val certOk = filterState.healthCertStatus.isEmpty() || filterState.healthCertStatus.contains(record.healthCertStatus)
                        val symptomOk = filterState.symptomFlags.isEmpty() || filterState.symptomFlags.any { flag ->
                            record.symptomFlags.contains(flag)
                        }
                        handOk && certOk && symptomOk
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleHandStatus(value: String) {
        _handStatus.value = toggleSetValue(_handStatus.value, value)
    }

    fun toggleHealthCertStatus(value: String) {
        _healthCertStatus.value = toggleSetValue(_healthCertStatus.value, value)
    }

    fun toggleSymptomFlag(value: String) {
        _symptomFlags.value = toggleSetValue(_symptomFlags.value, value)
    }

    private data class FilterState(
        val filter: TimeFilter,
        val query: String,
        val handStatus: Set<String>,
        val healthCertStatus: Set<String>,
        val symptomFlags: Set<String>
    )

    private fun toggleSetValue(set: Set<String>, value: String): Set<String> {
        return if (set.contains(value)) set - value else set + value
    }
}
