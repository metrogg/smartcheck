package com.smartcheck.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.ml.FaceEngine
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
    private val userRepository: IUserRepository,
    private val faceEngine: FaceEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId = savedStateHandle.get<String>("id")?.toLongOrNull()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val _faceBitmap = MutableStateFlow<Bitmap?>(null)
    val faceBitmap: StateFlow<Bitmap?> = _faceBitmap.asStateFlow()

    private val _certBitmap = MutableStateFlow<Bitmap?>(null)
    val certBitmap: StateFlow<Bitmap?> = _certBitmap.asStateFlow()

    init {
        if (userId != null) {
            viewModelScope.launch {
                userRepository.getUserById(userId).fold(
                    onSuccess = { user ->
                        _user.value = user
                        loadExistingImages(user)
                    },
                    onFailure = { _user.value = null }
                )
            }
        }
    }

    private fun loadExistingImages(user: User) {
        val facePath = user.faceImagePath
        if (!facePath.isNullOrBlank()) {
            val bitmap = FileUtil.loadBitmapFromInternal(appContext, facePath)
            _faceBitmap.value = bitmap
        }
        val certPath = user.healthCertImagePath
        if (!certPath.isNullOrBlank()) {
            val bitmap = FileUtil.loadBitmapFromInternal(appContext, certPath)
            _certBitmap.value = bitmap
        }
    }

    fun updateUser(
        name: String,
        employeeId: String,
        idCardNumber: String,
        phone: String,
        position: String,
        department: String,
        healthCertImagePath: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?
    ) {
        val current = _user.value ?: return
        if (healthCertStartDate != null && healthCertEndDate != null && healthCertEndDate < healthCertStartDate) {
            emitError("健康证日期范围不合法")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var faceEmbedding = current.faceEmbedding
                var faceImagePath = current.faceImagePath

                val newFaceBitmap = _faceBitmap.value
                if (newFaceBitmap != null) {
                    Timber.d("更新人脸照片")
                    val prepared = prepareFaceBitmap(newFaceBitmap)
                    val faces = synchronized(FaceSdk) { FaceSdk.detect(prepared) }
                    if (faces.isEmpty()) {
                        emitError("未检测到人脸，请重新拍摄")
                        return@launch
                    }
                    val feature = synchronized(FaceSdk) { FaceSdk.extractFeature(prepared) }
                        ?: throw IllegalStateException("人脸不清晰，请重新拍摄")
                    faceEmbedding = floatArrayToByteArray(feature)

                    val faceName = "face_emp_${System.currentTimeMillis()}.jpg"
                    faceImagePath = FileUtil.saveBitmapToInternal(appContext, newFaceBitmap, faceName)

                    if (prepared !== newFaceBitmap) {
                        prepared.recycle()
                    }
                }

                var certImagePath = healthCertImagePath
                val newCertBitmap = _certBitmap.value
                if (newCertBitmap != null) {
                    Timber.d("更新健康证照片")
                    val certName = "cert_emp_${System.currentTimeMillis()}.jpg"
                    certImagePath = FileUtil.saveBitmapToInternal(appContext, newCertBitmap, certName)
                }

                val updated = current.copy(
                    name = name.trim(),
                    employeeId = employeeId.trim(),
                    idCardNumber = idCardNumber.trim(),
                    phone = phone.trim(),
                    position = position.trim(),
                    department = department.trim(),
                    healthCertImagePath = certImagePath.trim(),
                    healthCertStartDate = healthCertStartDate,
                    healthCertEndDate = healthCertEndDate,
                    faceEmbedding = faceEmbedding,
                    faceImagePath = faceImagePath
                )
                userRepository.updateUser(updated)
                _user.value = updated

                if (faceEmbedding != null) {
                    Timber.d("人脸已更新，刷新缓存")
                    faceEngine.refreshUserCache()
                }

                _faceBitmap.value = null
                _certBitmap.value = null
                launch(Dispatchers.Main) {
                    _events.tryEmit(UiEvent.Saved)
                }
            } catch (e: Exception) {
                Timber.e(e, "更新员工失败")
                emitError(e.message ?: "保存失败")
            }
        }
    }

    fun deleteUser() {
        val current = _user.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.deleteUser(current.id)
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
        phone: String,
        position: String,
        department: String,
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
                    val newUser = if (current != null) {
                        current.copy(
                            name = trimmedName,
                            employeeId = trimmedId,
                            idCardNumber = idCardNumber.trim(),
                            phone = phone.trim(),
                            position = position.trim(),
                            department = department.trim(),
                            healthCertStartDate = healthCertStartDate,
                            healthCertEndDate = healthCertEndDate,
                            faceEmbedding = embedding,
                            faceImagePath = faceImageName,
                            healthCertImagePath = certImageName
                        )
                    } else {
                        User(
                            name = trimmedName,
                            employeeId = trimmedId,
                            idCardNumber = idCardNumber.trim(),
                            phone = phone.trim(),
                            position = position.trim(),
                            department = department.trim(),
                            healthCertStartDate = healthCertStartDate,
                            healthCertEndDate = healthCertEndDate,
                            faceEmbedding = embedding,
                            faceImagePath = faceImageName,
                            healthCertImagePath = certImageName
                        )
                    }

                    if (current == null) {
                        val result = userRepository.createUser(newUser)
                        result.fold(
                            onSuccess = { newId -> _user.value = newUser.copy(id = newId) },
                            onFailure = { emitError("创建用户失败") }
                        )
                    } else {
                        userRepository.updateUser(newUser)
                        _user.value = newUser
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
