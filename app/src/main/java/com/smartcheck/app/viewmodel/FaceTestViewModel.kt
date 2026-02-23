package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.ml.FaceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FaceTestViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val faceEngine: FaceEngine
) : ViewModel() {

    suspend fun registerUserWithFrame(
        name: String,
        employeeId: String,
        department: String,
        frame: Bitmap
    ): Long? {
        val trimmedEmployeeId = employeeId.trim()
        val trimmedName = name.trim()
        val trimmedDepartment = department.trim()

        if (trimmedEmployeeId.isEmpty() || trimmedName.isEmpty()) return null

        val existing = userRepository.getUserByEmployeeId(trimmedEmployeeId)

        val userId = if (existing == null) {
            userRepository.insertUser(
                UserEntity(
                    name = trimmedName,
                    employeeId = trimmedEmployeeId,
                    department = trimmedDepartment
                )
            )
        } else {
            val updated = existing.copy(
                name = trimmedName,
                department = trimmedDepartment
            )
            userRepository.updateUser(updated)
            existing.id
        }

        val ok = faceEngine.registerUser(userId, listOf(frame))
        if (!ok) return null

        return userId
    }
}
