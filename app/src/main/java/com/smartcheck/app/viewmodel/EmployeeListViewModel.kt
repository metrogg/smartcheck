package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneId

@HiltViewModel
class EmployeeListViewModel @Inject constructor(
    userRepository: UserRepository
) : ViewModel() {

    data class EmployeeListItem(
        val id: String,
        val name: String,
        val daysRemaining: Int,
        val faceImagePath: String?
    )

    private val query = MutableStateFlow("")
    private val page = MutableStateFlow(0)
    private val pageSize = 10

    data class UiState(
        val items: List<EmployeeListItem>,
        val pageIndex: Int,
        val totalPages: Int,
        val totalCount: Int,
        val query: String
    )

    val uiState: StateFlow<UiState> = combine(
        userRepository.getAllActiveUsers(),
        query,
        page
    ) { list, q, p ->
        val filtered = list.filter { user ->
            val key = q.trim().lowercase()
            if (key.isEmpty()) {
                true
            } else {
                user.name.lowercase().contains(key) || user.employeeId.lowercase().contains(key)
            }
        }.map { user ->
            EmployeeListItem(
                id = user.id.toString(),
                name = user.name,
                daysRemaining = calcRemainingDays(user.healthCertEndDate),
                faceImagePath = user.faceImagePath
            )
        }
        val totalPages = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
        val safePage = p.coerceIn(0, totalPages - 1)
        val startIndex = safePage * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(filtered.size)
        val pageItems = if (filtered.isEmpty()) emptyList() else filtered.subList(startIndex, endIndex)

        UiState(
            items = pageItems,
            pageIndex = safePage,
            totalPages = totalPages,
            totalCount = filtered.size,
            query = q
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState(emptyList(), 0, 1, 0, "")
    )

    private fun calcRemainingDays(endAt: Long?): Int {
        if (endAt == null) return 0
        val endDate = java.time.Instant.ofEpochMilli(endAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt()
    }

    fun setQuery(value: String) {
        query.value = value
        page.value = 0
    }

    fun nextPage() {
        page.value = page.value + 1
    }

    fun prevPage() {
        page.value = (page.value - 1).coerceAtLeast(0)
    }
}
