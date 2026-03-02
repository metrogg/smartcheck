package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.ml.FaceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FaceTestViewModel @Inject constructor(
    private val userRepository: IUserRepository,
    private val faceEngine: FaceEngine
) : ViewModel() {

    fun registerUserWithFrame(
        name: String,
        employeeId: String,
        department: String,
        frame: Bitmap,
        onResult: (Long?) -> Unit
    ) {
        viewModelScope.launch {
            val result = registerUserWithFrameSuspend(name, employeeId, department, frame)
            onResult(result)
        }
    }

    private suspend fun registerUserWithFrameSuspend(
        name: String,
        employeeId: String,
        department: String,
        frame: Bitmap
    ): Long? {
        val trimmedEmployeeId = employeeId.trim()
        val trimmedName = name.trim()
        val trimmedDepartment = department.trim()

        if (trimmedEmployeeId.isEmpty() || trimmedName.isEmpty()) return null

        val existingResult = userRepository.getUserByEmployeeId(trimmedEmployeeId)

        val userId = existingResult.fold(
            onSuccess = { existing ->
                val updated = existing.copy(
                    name = trimmedName,
                    department = trimmedDepartment
                )
                userRepository.updateUser(updated)
                existing.id
            },
            onFailure = {
                val newUser = User(
                    name = trimmedName,
                    employeeId = trimmedEmployeeId,
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
