package com.smartcheck.sdk.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object FaceSdk {

    private const val TAG = "FaceSdk"

    private const val FD_MODEL_NAME = "fd_2_00.dat"
    private const val LM_MODEL_NAME = "pd_2_00_pts5.dat"
    private const val FR_MODEL_NAME = "fr_2_10.dat"

    private const val FD_MODEL_MD5 = "E88669E5F1301CA56162DE8AEF1FD5D5"
    private const val LM_MODEL_MD5 = "877A44AA6F07CB3064AD2828F50F261A"
    private const val FR_MODEL_MD5 = "2D637AAD8B1B7AE62154A877EC291C99"

    private var isInitialized = false
    private var lastInitError: String? = null

    init {
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("SeetaNet")
            System.loadLibrary("SeetaFaceDetector")
            System.loadLibrary("SeetaFaceLandmarker")
            System.loadLibrary("SeetaFaceRecognizer")
            System.loadLibrary("SeetaFaceTracker")
            System.loadLibrary("SeetaQualityAssessor")
            System.loadLibrary("face_sdk")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun extractFeature(bitmap: Bitmap): FloatArray? {
        if (!isInitialized) {
            Log.w(TAG, "FaceSdk not initialized")
            return null
        }

        return try {
            nativeExtractFeature(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during feature extraction", e)
            null
        }
    }

    fun calculateSimilarity(feature1: FloatArray, feature2: FloatArray): Float {
        if (!isInitialized) {
            Log.w(TAG, "FaceSdk not initialized")
            return 0f
        }

        return try {
            nativeCalculateSimilarity(feature1, feature2)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during similarity calculation", e)
            0f
        }
    }

    private fun ensureAssetCopied(context: Context, assetName: String): String {
        val outFile = File(context.filesDir, assetName)
        val metaFile = File(context.filesDir, "$assetName.meta")
        if (outFile.exists() && metaFile.exists()) {
            val expected = metaFile.readText().trim().toLongOrNull()
            if (expected != null && expected > 0 && outFile.length() == expected) {
                return outFile.absolutePath
            }
        }

        val tmpFile = File(context.filesDir, "$assetName.tmp")
        if (tmpFile.exists()) {
            tmpFile.delete()
        }

        val copied = context.assets.open(assetName).use { input ->
            FileOutputStream(tmpFile).use { output ->
                val bytes = input.copyTo(output)
                output.fd.sync()
                bytes
            }
        }

        if (outFile.exists()) {
            outFile.delete()
        }

        val renamed = tmpFile.renameTo(outFile)
        if (!renamed) {
            tmpFile.copyTo(outFile, overwrite = true)
            tmpFile.delete()
        }

        metaFile.writeText(copied.toString())
        return outFile.absolutePath
    }

    private fun md5OfFile(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02X".format(it) }
    }

    @Synchronized
    fun init(context: Context): Int {
        if (isInitialized) {
            Log.w(TAG, "Already initialized")
            return 0
        }
        lastInitError = null

        try {
            context.assets.open(FD_MODEL_NAME).close()
            context.assets.open(LM_MODEL_NAME).close()
            context.assets.open(FR_MODEL_NAME).close()
        } catch (e: Exception) {
            Log.e(TAG, "Model files not found in assets/")
            Log.e(TAG, "Required files: $FD_MODEL_NAME, $LM_MODEL_NAME, $FR_MODEL_NAME")
            return -1
        }

        val fdPath = ensureAssetCopied(context, FD_MODEL_NAME)
        val lmPath = ensureAssetCopied(context, LM_MODEL_NAME)
        val frPath = ensureAssetCopied(context, FR_MODEL_NAME)

        try {
            val fdMd5 = md5OfFile(File(fdPath))
            val lmMd5 = md5OfFile(File(lmPath))
            val frMd5 = md5OfFile(File(frPath))
            Log.i(TAG, "Model MD5 fd=$fdMd5 lm=$lmMd5 fr=$frMd5")
            if (!fdMd5.equals(FD_MODEL_MD5, ignoreCase = true) ||
                !lmMd5.equals(LM_MODEL_MD5, ignoreCase = true) ||
                !frMd5.equals(FR_MODEL_MD5, ignoreCase = true)
            ) {
                Log.e(TAG, "Model MD5 mismatch. Expected fd=$FD_MODEL_MD5 lm=$LM_MODEL_MD5 fr=$FR_MODEL_MD5")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute model MD5", e)
        }

        val ret = try {
            nativeInit(fdPath, lmPath, frPath)
        } catch (t: Throwable) {
            lastInitError = "nativeInit threw ${t::class.java.simpleName}: ${t.message}"
            Log.e(TAG, "nativeInit threw", t)
            -99
        }
        if (ret == 0) {
            isInitialized = true
            Log.i(TAG, "FaceSdk initialized successfully")
        } else {
            Log.e(TAG, "Failed to initialize FaceSdk: $ret")
        }
        return ret
    }

    fun getLastInitError(): String? = lastInitError

    fun detect(bitmap: Bitmap): List<FaceInfo> {
        if (!isInitialized) {
            Log.w(TAG, "FaceSdk not initialized")
            return emptyList()
        }

        return try {
            val results = nativeDetect(bitmap)
            results?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during detection", e)
            emptyList()
        }
    }

    fun release() {
        if (!isInitialized) return
        try {
            nativeRelease()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during release", e)
        } finally {
            isInitialized = false
        }
    }

    private external fun nativeInit(fdModelPath: String, lmModelPath: String, frModelPath: String): Int

    private external fun nativeDetect(bitmap: Bitmap): Array<FaceInfo>?

    private external fun nativeExtractFeature(bitmap: Bitmap): FloatArray?

    private external fun nativeCalculateSimilarity(feature1: FloatArray, feature2: FloatArray): Float

    private external fun nativeRelease()
}
