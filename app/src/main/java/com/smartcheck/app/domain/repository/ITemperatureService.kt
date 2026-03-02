package com.smartcheck.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface ITemperatureService {

    suspend fun initialize(): Result<Unit>

    suspend fun release(): Result<Unit>

    fun isInitialized(): Boolean

    fun observeTemperature(): Flow<Float>
}
