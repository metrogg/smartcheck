package com.smartcheck.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.sdk.face.FaceSdk
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@HiltViewModel
class EmployeeDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId = savedStateHandle.get<String>("id")?.toLongOrNull()

    private val _user = MutableStateFlow<UserEntity?>(null)
    val user: StateFlow<UserEntity?> = _user.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val _faceBitmap = MutableStateFlow<Bitmap?>(null)
    val faceBitmap: StateFlow<Bitmap?> = _faceBitmap.asStateFlow()

    private val _certBitmap = MutableStateFlow<Bitmap?>(null)
    val certBitmap: StateFlow<Bitmap?> = _certBitmap.asStateFlow()

    init {
        if (userId != null) {
            viewModelScope.launch {
                _user.value = userRepository.getUserById(userId)
            }
        }
    }

    fun updateUser(
        name: String,
        employeeId: String,
        idCardNumber: String,
        healthCertImagePath: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?
    ) {
        val current = _user.value ?: return
        if (healthCertStartDate == null || healthCertEndDate == null) {
            emitError("请选择健康证起止日期")
            return
        }
        if (healthCertStartDate != null && healthCertEndDate != null && healthCertEndDate < healthCertStartDate) {
            emitError("健康证日期范围不合法")
            return
        }
        val updated = current.copy(
            name = name.trim(),
            employeeId = employeeId.trim(),
            idCardNumber = idCardNumber.trim(),
            healthCertImagePath = healthCertImagePath.trim(),
            healthCertStartDate = healthCertStartDate,
            healthCertEndDate = healthCertEndDate
        )
        viewModelScope.launch {
            userRepository.updateUser(updated)
            _user.value = updated
            _events.tryEmit(UiEvent.Saved)
        }
    }

    fun deleteUser() {
        val current = _user.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.deleteUser(current)
            _events.tryEmit(UiEvent.Saved)
        }
    }

    fun updateFaceBitmap(bitmap: Bitmap?) {
        _faceBitmap.value = bitmap
    }

    fun updateCertBitmap(bitmap: Bitmap?) {
        _certBitmap.value = bitmap
    }

    fun saveEmployee(
        name: String,
        employeeId: String,
        idCardNumber: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedName = name.trim()
                val trimmedId = employeeId.trim()
                if (trimmedName.isEmpty() || trimmedId.isEmpty()) {
                    emitError("请填写姓名和编号")
                    return@launch
                }
                val faceBitmap = _faceBitmap.value
                if (faceBitmap == null || faceBitmap.isRecycled) {
                    emitError("请拍摄人脸")
                    return@launch
                }
                val certBitmap = _certBitmap.value
                if (certBitmap == null || certBitmap.isRecycled) {
                    emitError("请拍摄健康证")
                    return@launch
                }
                if (healthCertStartDate == null) {
                    emitError("请选择健康证起始日期")
                    return@launch
                }
                if (healthCertEndDate == null) {
                    emitError("请选择健康证日期")
                    return@launch
                }
                if (healthCertEndDate < healthCertStartDate) {
                    emitError("健康证日期范围不合法")
                    return@launch
                }

                val prepared = prepareFaceBitmap(faceBitmap)
                try {
                    if (!FaceSdk.isInitialized()) {
                        val initRet = FaceSdk.init(appContext)
                        if (initRet != 0) {
                            val detail = FaceSdk.getLastInitError() ?: "code=$initRet"
                            emitError("人脸引擎初始化失败: $detail")
                            return@launch
                        }
                    }

                    val faces = synchronized(FaceSdk) { FaceSdk.detect(prepared) }
                    if (faces.isEmpty()) {
                        emitError("未检测到人脸，请重新拍摄")
                        return@launch
                    }
                    val feature = synchronized(FaceSdk) { FaceSdk.extractFeature(prepared) }
                        ?: throw IllegalStateException("人脸不清晰，请重新拍摄")
                    val embedding = floatArrayToByteArray(feature)

                    val faceName = "face_emp_${System.currentTimeMillis()}.jpg"
                    val certName = "cert_emp_${System.currentTimeMillis()}.jpg"
                    val faceImageName = FileUtil.saveBitmapToInternal(appContext, faceBitmap, faceName)
                    val certImageName = FileUtil.saveBitmapToInternal(appContext, certBitmap, certName)

                    val current = _user.value
                    val entity = (current ?: UserEntity(
                        name = trimmedName,
                        employeeId = trimmedId
                    )).copy(
                        name = trimmedName,
                        employeeId = trimmedId,
                        idCardNumber = idCardNumber.trim(),
                        healthCertStartDate = healthCertStartDate,
                        healthCertEndDate = healthCertEndDate,
                        faceEmbedding = embedding,
                        faceImagePath = faceImageName,
                        healthCertImagePath = certImageName
                    )

                    if (current == null) {
                        val newId = userRepository.insertUser(entity)
                        _user.value = entity.copy(id = newId)
                    } else {
                        userRepository.updateUser(entity)
                        _user.value = entity
                    }
                    launch(Dispatchers.Main) {
                        onSuccess()
                        _faceBitmap.value = null
                        _certBitmap.value = null
                    }
                } finally {
                    if (prepared !== faceBitmap) {
                        prepared.recycle()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save employee")
                emitError(e.message ?: "保存失败")
            }
        }
    }

    private fun emitError(message: String) {
        _events.tryEmit(UiEvent.Error(message))
    }

    private fun floatArrayToByteArray(feature: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(feature.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(feature)
        return buffer.array()
    }

    private fun prepareFaceBitmap(source: Bitmap): Bitmap {
        val argb = if (source.config == Bitmap.Config.ARGB_8888) source else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val maxDim = 1024
        val w = argb.width
        val h = argb.height
        val maxSide = maxOf(w, h)
        return if (maxSide <= maxDim) {
            argb
        } else {
            val scale = maxDim.toFloat() / maxSide
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(argb, targetW, targetH, true)
        }
    }

    sealed interface UiEvent {
        data object Saved : UiEvent
        data class Error(val message: String) : UiEvent
    }
}
