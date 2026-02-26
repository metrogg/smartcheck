package com.smartcheck.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.sdk.face.FaceInfo
import com.smartcheck.sdk.face.FaceSdk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeetaFaceEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepository: UserRepository
) : FaceEngine {

    private var isInitialized = false
    private val isProcessing = AtomicBoolean(false)
    private val initInProgress = AtomicBoolean(false)
    private val initLock = Any()
    @Volatile private var lastPerfLogAt = 0L

    override fun init(context: Context) {
        ensureInit(context, "sync")
    }

    fun initAsync(context: Context) {
        startAsyncInit(context)
    }

    private fun startAsyncInit(context: Context) {
        if (isInitialized) return
        if (!initInProgress.compareAndSet(false, true)) return
        Thread {
            try {
                ensureInit(context, "async")
            } finally {
                initInProgress.set(false)
            }
        }.start()
    }

    private fun ensureInit(context: Context, source: String) {
        if (isInitialized) return
        synchronized(initLock) {
            if (isInitialized) return
            val start = SystemClock.elapsedRealtime()
            val ret = FaceSdk.init(context)
            val elapsed = SystemClock.elapsedRealtime() - start
            isInitialized = ret == 0
            if (!isInitialized) {
                Timber.e(
                    "SeetaFaceEngine init failed (%s): ret=%d err=%s time=%dms",
                    source,
                    ret,
                    FaceSdk.getLastInitError(),
                    elapsed
                )
            } else {
                Timber.i("SeetaFaceEngine init ok (%s) time=%dms", source, elapsed)
            }
        }
    }

    override suspend fun detectAndRecognize(frame: Bitmap): FaceResult? {
        if (!isInitialized) {
            startAsyncInit(appContext)
            return null
        }
        if (!isProcessing.compareAndSet(false, true)) return null

        return try {
            withContext(Dispatchers.Default) {
                val totalStart = SystemClock.elapsedRealtime()
                val detectStart = SystemClock.elapsedRealtime()
                val faces = FaceSdk.detect(frame)
                val detectMs = SystemClock.elapsedRealtime() - detectStart
                val bestFace: FaceInfo? = faces.maxByOrNull { it.score }
                val bbox = bestFace?.box?.let {
                    Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
                } ?: Rect()

                val featureStart = SystemClock.elapsedRealtime()
                val feature = FaceSdk.extractFeature(frame)
                val featureMs = SystemClock.elapsedRealtime() - featureStart
                if (feature == null) {
                    maybeLogPerf(
                        totalMs = SystemClock.elapsedRealtime() - totalStart,
                        detectMs = detectMs,
                        featureMs = featureMs,
                        compareMs = 0L,
                        users = 0,
                        faces = faces.size,
                        bestSim = 0f
                    )
                    return@withContext if (!bbox.isEmpty) {
                        FaceResult(
                            userId = null,
                            userName = null,
                            confidence = 0f,
                            boundingBox = bbox,
                            isLive = true
                        )
                    } else {
                        null
                    }
                }

                val users = userRepository.getAllActiveUsers().first()

                var bestUser: UserEntity? = null
                var bestSim = 0.0f

                val compareStart = SystemClock.elapsedRealtime()
                for (user in users) {
                    val bytes = user.faceEmbedding ?: continue
                    val stored = byteArrayToFloatArray(bytes) ?: continue
                    if (stored.size != feature.size) continue

                    val sim = FaceSdk.calculateSimilarity(stored, feature)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestUser = user
                    }
                }
                val compareMs = SystemClock.elapsedRealtime() - compareStart
                maybeLogPerf(
                    totalMs = SystemClock.elapsedRealtime() - totalStart,
                    detectMs = detectMs,
                    featureMs = featureMs,
                    compareMs = compareMs,
                    users = users.size,
                    faces = faces.size,
                    bestSim = bestSim
                )

                val threshold = 0.70f
                if (bestUser != null && bestSim >= threshold) {
                    FaceResult(
                        userId = bestUser!!.id,
                        userName = bestUser!!.name,
                        confidence = bestSim,
                        boundingBox = bbox,
                        isLive = true
                    )
                } else {
                    if (!bbox.isEmpty) {
                        FaceResult(
                            userId = null,
                            userName = null,
                            confidence = bestSim,
                            boundingBox = bbox,
                            isLive = true
                        )
                    } else {
                        null
                    }
                }
            }
        } finally {
            isProcessing.set(false)
        }
    }

    override suspend fun registerUser(userId: Long, frames: List<Bitmap>): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Default) {
            val user = userRepository.getUserById(userId) ?: return@withContext false
            if (frames.isEmpty()) return@withContext false

            var sum: FloatArray? = null
            var count = 0

            for (frame in frames) {
                val feature = FaceSdk.extractFeature(frame) ?: continue
                if (sum == null) {
                    sum = FloatArray(feature.size)
                }
                if (sum!!.size != feature.size) continue

                for (i in feature.indices) {
                    sum!![i] += feature[i]
                }
                count++
            }

            if (sum == null || count == 0) return@withContext false

            for (i in sum!!.indices) {
                sum!![i] /= count.toFloat()
            }

            val embeddingBytes = floatArrayToByteArray(sum!!)
            val updated = user.copy(faceEmbedding = embeddingBytes)
            userRepository.updateUser(updated)
            true
        }
    }

    override fun release() {
        if (!isInitialized) return
        FaceSdk.release()
        isInitialized = false
    }

    private fun maybeLogPerf(
        totalMs: Long,
        detectMs: Long,
        featureMs: Long,
        compareMs: Long,
        users: Int,
        faces: Int,
        bestSim: Float
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPerfLogAt < 2000L) return
        lastPerfLogAt = now
        Timber.d(
            "Face perf total=%dms detect=%dms feature=%dms compare=%dms users=%d faces=%d bestSim=%.3f",
            totalMs,
            detectMs,
            featureMs,
            compareMs,
            users,
            faces,
            bestSim
        )
    }

    private fun floatArrayToByteArray(feature: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(feature.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(feature)
        return buffer.array()
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty() || bytes.size % 4 != 0) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(out)
        return out
    }
}
