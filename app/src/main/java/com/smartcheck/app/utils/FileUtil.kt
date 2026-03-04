package com.smartcheck.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import timber.log.Timber
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

        if (pathOrName.startsWith("content://")) {
            Timber.tag("FileUtil").d("Handling content URI: $pathOrName")
            try {
                val uri = Uri.parse(pathOrName)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    inputStream.close()
                    return null
                }
            } catch (e: Exception) {
                Timber.tag("FileUtil").w("Cannot access content URI: $pathOrName, error: ${e.message}")
            }
            return null
        }

        return if (pathOrName.startsWith("/")) {
            val file = File(pathOrName)
            if (file.exists()) file else null
        } else {
            File(getRecordsDir(context), pathOrName)
        }
    }

    fun loadBitmapFromInternal(context: Context, pathOrName: String?): Bitmap? {
        val file = getRecordImageFile(context, pathOrName) ?: return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Timber.tag("FileUtil").e(e, "Failed to load bitmap: ${file.absolutePath}")
            null
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
