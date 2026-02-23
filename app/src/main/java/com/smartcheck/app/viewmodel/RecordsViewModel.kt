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

    val records: StateFlow<List<RecordEntity>> =
        combine(timeFilter, query) { filter, q -> filter to q }
            .flatMapLatest { (filter, q) ->
                val now = System.currentTimeMillis()
                val base = when (filter) {
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
                    val trimmed = q.trim()
                    if (trimmed.isEmpty()) {
                        list
                    } else {
                        val lower = trimmed.lowercase()
                        list.filter {
                            it.userName.lowercase().contains(lower) ||
                                it.employeeId.lowercase().contains(lower)
                        }
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
}
