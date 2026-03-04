package com.smartcheck.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.sdk.face.FaceInfo
import com.smartcheck.sdk.face.FaceSdk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeetaFaceEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepository: IUserRepository
) : FaceEngine {

    private var isInitialized = false
    private val isProcessing = AtomicBoolean(false)
    private val initInProgress = AtomicBoolean(false)
    private val initLock = Any()
    @Volatile private var lastPerfLogAt = 0L

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class CachedUser(
        val id: Long,
        val name: String,
        val feature: FloatArray
    )

    private val userFeatureCache = ConcurrentHashMap<Long, CachedUser>()

    fun refreshUserCache() {
        engineScope.launch {
            try {
                val users = userRepository.observeAllUsers().first()
                val newCache = ConcurrentHashMap<Long, CachedUser>()
                for (user in users) {
                    val bytes = user.faceEmbedding ?: continue
                    val feature = byteArrayToFloatArray(bytes) ?: continue
                    newCache[user.id] = CachedUser(user.id, user.name, feature)
                }
                userFeatureCache.clear()
                userFeatureCache.putAll(newCache)
                Timber.d("[FaceEngine] 用户特征缓存已刷新: ${newCache.size} 个用户")
            } catch (e: Exception) {
                Timber.e(e, "[FaceEngine] 刷新用户缓存失败")
            }
        }
    }

    fun clearUserCache() {
        userFeatureCache.clear()
        Timber.d("[FaceEngine] 用户特征缓存已清空")
    }

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
                refreshUserCache()
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

                if (faces.isEmpty()) {
                    maybeLogPerf(
                        totalMs = SystemClock.elapsedRealtime() - totalStart,
                        detectMs = detectMs,
                        featureMs = 0L,
                        compareMs = 0L,
                        users = userFeatureCache.size,
                        faces = 0,
                        bestSim = 0f
                    )
                    return@withContext null
                }

                val bestFace = faces.maxByOrNull { it.score }
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
                        users = userFeatureCache.size,
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

                if (userFeatureCache.isEmpty()) {
                    refreshUserCache()
                }

                val cachedUsers = userFeatureCache.values.toList()

                var bestUserId: Long? = null
                var bestUserName: String? = null
                var bestSim = 0.0f

                val compareStart = SystemClock.elapsedRealtime()
                for (cachedUser in cachedUsers) {
                    if (cachedUser.feature.size != feature.size) continue
                    val sim = FaceSdk.calculateSimilarity(cachedUser.feature, feature)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestUserId = cachedUser.id
                        bestUserName = cachedUser.name
                    }
                }
                val compareMs = SystemClock.elapsedRealtime() - compareStart

                maybeLogPerf(
                    totalMs = SystemClock.elapsedRealtime() - totalStart,
                    detectMs = detectMs,
                    featureMs = featureMs,
                    compareMs = compareMs,
                    users = cachedUsers.size,
                    faces = faces.size,
                    bestSim = bestSim
                )

                val threshold = 0.70f
                if (bestUserId != null && bestSim >= threshold) {
                    FaceResult(
                        userId = bestUserId,
                        userName = bestUserName,
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
        if (!isInitialized) {
            Timber.w("[FaceEngine] 注册时引擎未初始化，尝试同步初始化")
            startAsyncInit(appContext)
            Thread.sleep(500)
            if (!isInitialized) {
                Timber.e("[FaceEngine] 注册失败：引擎初始化超时")
                return false
            }
        }

        return withContext(Dispatchers.Default) {
            val userResult = userRepository.getUserById(userId)
            val user = userResult.getOrNull() ?: run {
                Timber.e("[FaceEngine] 注册失败：用户不存在 userId=$userId")
                return@withContext false
            }
            if (frames.isEmpty()) {
                Timber.e("[FaceEngine] 注册失败：没有传入图片")
                return@withContext false
            }

            var sum: FloatArray? = null
            var count = 0

            for (frame in frames) {
                val feature = FaceSdk.extractFeature(frame)
                if (feature == null) {
                    Timber.w("[FaceEngine] 特征提取失败，跳过当前帧")
                    continue
                }
                if (sum == null) {
                    sum = FloatArray(feature.size)
                }
                if (sum!!.size != feature.size) continue

                for (i in feature.indices) {
                    sum!![i] += feature[i]
                }
                count++
            }

            if (sum == null || count == 0) {
                Timber.e("[FaceEngine] 注册失败：无法从图片中提取特征")
                return@withContext false
            }

            for (i in sum!!.indices) {
                sum!![i] /= count.toFloat()
            }

            val embeddingBytes = floatArrayToByteArray(sum!!)
            val updated = user.copy(faceEmbedding = embeddingBytes)
            userRepository.updateUser(updated)

            userFeatureCache[userId] = CachedUser(userId, user.name, sum!!)
            Timber.i("[FaceEngine] 用户特征注册成功: userId=$userId, name=${user.name}, frames=$count")

            true
        }
    }

    override fun release() {
        if (!isInitialized) return
        FaceSdk.release()
        isInitialized = false
        userFeatureCache.clear()
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
