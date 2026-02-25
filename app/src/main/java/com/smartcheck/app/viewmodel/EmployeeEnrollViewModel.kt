package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.ml.FaceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class EmployeeEnrollViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val faceEngine: FaceEngine
) : ViewModel() {

    val users: Flow<List<UserEntity>> = userRepository.getAllActiveUsers()

    suspend fun enrollWithFrame(
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

        val existing = userRepository.getUserByEmployeeId(trimmedEmployeeId)

        val userId = if (existing == null) {
            userRepository.insertUser(
                UserEntity(
                    name = trimmedName,
                    employeeId = trimmedEmployeeId,
                    idCardNumber = trimmedIdCard,
                    healthCertImagePath = trimmedCertPath,
                    healthCertStartDate = healthCertStartDate,
                    healthCertEndDate = healthCertEndDate,
                    department = trimmedDepartment
                )
            )
        } else {
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
        }

        val ok = faceEngine.registerUser(userId, listOf(frame))
        if (!ok) return null

        return userId
    }
}
