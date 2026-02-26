package com.smartcheck.app.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object FileUtil {
    private const val RECORDS_DIR = "records"

    fun getRecordsDir(context: Context): File {
        val dir = File(context.filesDir, RECORDS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveBitmapToInternal(context: Context, bitmap: Bitmap, filename: String): String {
        val file = File(getRecordsDir(context), filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.name
    }

    fun saveBitmapToInternalResult(context: Context, bitmap: Bitmap, filename: String): Result<String> {
        return runCatching {
            val file = File(getRecordsDir(context), filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.name
        }
    }

    fun getRecordImageFile(context: Context, pathOrName: String?): File? {
        if (pathOrName.isNullOrBlank()) return null
        return if (pathOrName.startsWith("/")) {
            File(pathOrName)
        } else {
            File(getRecordsDir(context), pathOrName)
        }
    }

    fun clearOldRecords(context: Context, days: Int) {
        if (days <= 0) return
        val dir = File(context.filesDir, RECORDS_DIR)
        if (!dir.exists()) return

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
