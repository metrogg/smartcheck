package com.smartcheck.app.domain.model

typealias AppResult<T> = Result<T>

inline fun <T, R> Result<T>.mapFold(
    onSuccess: (T) -> R,
    onFailure: (Throwable) -> R
): R = fold(onSuccess, onFailure)

fun <T> Result<T>.getOrNull(): T? = getOrNull()

fun <T> Result<T>.exceptionOrNull(): Throwable? = exceptionOrNull()

fun <T> Result<T>.isSuccessResult(): Boolean = isSuccess

fun <T> Result<T>.isFailureResult(): Boolean = isFailure
