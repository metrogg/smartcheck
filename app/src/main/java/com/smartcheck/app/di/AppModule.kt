package com.smartcheck.app.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import com.smartcheck.app.data.db.AppDatabase
import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.repository.AdminAuthRepository
import com.smartcheck.app.data.repository.RecordRepository
import com.smartcheck.app.data.repository.TemperatureServiceImpl
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.data.upload.NoOpRecordUploadReporter
import com.smartcheck.app.data.upload.RecordUploadReporter
import com.smartcheck.app.domain.repository.IAdminAuthService
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.ITemperatureService
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.domain.repository.IVoiceService
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.app.ml.MockFaceEngine
import com.smartcheck.app.ml.SeetaFaceEngine
import com.smartcheck.app.voice.VoicePrompter
import com.smartcheck.sdk.HandDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModuleBinds {

    @Binds
    @Singleton
    abstract fun bindUserRepository(repo: UserRepository): IUserRepository

    @Binds
    @Singleton
    abstract fun bindRecordRepository(repo: RecordRepository): IRecordRepository

    @Binds
    @Singleton
    abstract fun bindAdminAuthService(repo: AdminAuthRepository): IAdminAuthService

    @Binds
    @Singleton
    abstract fun bindVoiceService(service: VoicePrompter): IVoiceService

    @Binds
    @Singleton
    abstract fun bindTemperatureService(service: TemperatureServiceImpl): ITemperatureService
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * 提供 Room 数据库
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartcheck_db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()
    }
    
    /**
     * 提供 UserDao
     */
    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }
    
    /**
     * 提供 RecordDao
     */
    @Provides
    @Singleton
    fun provideRecordDao(database: AppDatabase): RecordDao {
        return database.recordDao()
    }

    @Provides
    @Singleton
    fun provideRecordUploadReporter(): RecordUploadReporter {
        return NoOpRecordUploadReporter()
    }
    
    /**
     * 提供应用协程作用域
     */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideFaceEngine(
        @ApplicationContext context: Context,
        seetaFaceEngine: SeetaFaceEngine,
        mockFaceEngine: MockFaceEngine,
        appScope: CoroutineScope,
    ): FaceEngine {
        Timber.d("Initializing FaceEngine (Seeta)")
        return try {
            appScope.launch { seetaFaceEngine.initAsync(context) }
            seetaFaceEngine
        } catch (t: Throwable) {
            // Keep app usable even if native init crashes.
            Timber.e(t, "SeetaFaceEngine init crashed; falling back to MockFaceEngine")
            mockFaceEngine.init(context)
            mockFaceEngine
        }
    }
    
    /**
     * 初始化 HandDetector
     */
    @Provides
    @Singleton
    fun provideHandDetector(@ApplicationContext context: Context): HandDetector {
        Timber.d("Initializing HandDetector (Mock)")
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val product = Build.PRODUCT.lowercase()
        val isRockchip = hardware.contains("rk") || board.contains("rk") || product.contains("rk")
        if (!isRockchip) {
            Timber.w("Skipping HandDetector init on non-RK device. hardware=$hardware board=$board product=$product")
            return HandDetector
        }

        HandDetector.init(context, "mock_license_key")
        return HandDetector
    }
}
