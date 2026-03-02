package com.smartcheck.app.domain.model

sealed class AppError : Throwable() {
    object NotFound : AppError()
    data class ValidationError(val field: String, val errorMsg: String) : AppError()
    data class DuplicateError(val field: String, val value: String) : AppError()
    object Unauthorized : AppError()
    object Forbidden : AppError()

    object HardwareNotReady : AppError()
    data class HardwareError(val device: String, val detail: String) : AppError()

    object ModelNotLoaded : AppError()
    data class DetectionError(val type: String, val errorMsg: String) : AppError()
    object NoTargetDetected : AppError()

    data class UnknownError(val errorMsg: String) : AppError()

    override fun toString(): String = message ?: javaClass.simpleName
}
