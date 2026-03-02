package com.smartcheck.app.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class ImageStorageUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val minFreeBytes = 200L * 1024L * 1024L

    fun saveFaceImage(bitmap: Bitmap): Result<String> {
        return saveImage(bitmap, "face_${System.currentTimeMillis()}.jpg")
    }

    fun savePalmImage(bitmap: Bitmap): Result<String> {
        return saveImage(bitmap, "palm_${System.currentTimeMillis()}.jpg")
    }

    fun saveBackImage(bitmap: Bitmap): Result<String> {
        return saveImage(bitmap, "back_${System.currentTimeMillis()}.jpg")
    }

    private fun saveImage(bitmap: Bitmap, filename: String): Result<String> {
        val available = appContext.filesDir.usableSpace
        if (available < minFreeBytes) {
            Timber.w("Insufficient storage space: $available bytes")
            return Result.failure(AppError.StorageError("存储空间不足"))
        }

        val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return FileUtil.saveBitmapToInternalResult(appContext, safeBitmap, filename)
            .also { safeBitmap.recycle() }
    }

    fun hasEnoughStorage(): Boolean {
        return appContext.filesDir.usableSpace >= minFreeBytes
    }
}
