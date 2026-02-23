package com.smartcheck.app.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import com.smartcheck.app.data.db.AppDatabase
import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.upload.NoOpRecordUploadReporter
import com.smartcheck.app.data.upload.RecordUploadReporter
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.app.ml.MockFaceEngine
import com.smartcheck.app.ml.SeetaFaceEngine
import com.smartcheck.sdk.HandDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 */
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
        ).build()
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
     * 提供人脸识别引擎
     * 当前使用 MockFaceEngine，将来替换为 SeetaFaceEngine
     */
    @Provides
    @Singleton
    fun provideFaceEngine(
        @ApplicationContext context: Context,
        seetaFaceEngine: SeetaFaceEngine
    ): FaceEngine {
        Timber.d("Initializing FaceEngine (Seeta)")
        seetaFaceEngine.init(context)
        return seetaFaceEngine
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
