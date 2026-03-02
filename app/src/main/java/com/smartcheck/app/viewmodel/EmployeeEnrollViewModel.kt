package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.ml.FaceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmployeeEnrollViewModel @Inject constructor(
    private val userRepository: IUserRepository,
    private val faceEngine: FaceEngine
) : ViewModel() {

    val users: Flow<List<User>> = userRepository.observeAllUsers()

    fun enrollWithFrame(
        name: String,
        employeeId: String,
        department: String,
        idCardNumber: String,
        healthCertImagePath: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?,
        frame: Bitmap,
        onResult: (Long?) -> Unit
    ) {
        viewModelScope.launch {
            val result = enrollWithFrameSuspend(
                name, employeeId, department, idCardNumber,
                healthCertImagePath, healthCertStartDate, healthCertEndDate, frame
            )
            onResult(result)
        }
    }

    private suspend fun enrollWithFrameSuspend(
        name: String,
        employeeId: String,
        department: String,
        idCardNumber: String,
        healthCertImagePath: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?,
        frame: Bitmap
    ): Long? {
        val trimmedEmployeeId = employeeId.trim()
        val trimmedName = name.trim()
        val trimmedDepartment = department.trim()
        val trimmedIdCard = idCardNumber.trim()
        val trimmedCertPath = healthCertImagePath.trim()

        if (trimmedEmployeeId.isEmpty() || trimmedName.isEmpty()) return null
        if (healthCertStartDate != null && healthCertEndDate != null && healthCertEndDate < healthCertStartDate) {
            return null
        }

        val existingResult = userRepository.getUserByEmployeeId(trimmedEmployeeId)

        val userId = existingResult.fold(
            onSuccess = { existing ->
                val updated = existing.copy(
                    name = trimmedName,
                    department = trimmedDepartment,
                    idCardNumber = trimmedIdCard,
                    healthCertImagePath = trimmedCertPath,
                    healthCertStartDate = healthCertStartDate,
                    healthCertEndDate = healthCertEndDate
                )
                userRepository.updateUser(updated)
                existing.id
            },
            onFailure = {
                val newUser = User(
                    name = trimmedName,
                    employeeId = trimmedEmployeeId,
                    idCardNumber = trimmedIdCard,
                    healthCertImagePath = trimmedCertPath,
                    healthCertStartDate = healthCertStartDate,
                    healthCertEndDate = healthCertEndDate,
                    department = trimmedDepartment
                )
                userRepository.createUser(newUser).getOrNull()
            }
        )

        if (userId == null) return null

        val ok = faceEngine.registerUser(userId, listOf(frame))
        if (!ok) return null

        return userId
    }
}
