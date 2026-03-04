package com.smartcheck.app.domain.usecase

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object PerformanceMetrics {

    private val metrics = ConcurrentHashMap<String, MetricRecord>()

    fun recordDuration(operation: String, durationMs: Long) {
        val record = metrics.getOrPut(operation) { MetricRecord(operation) }
        record.addSample(durationMs)

        Timber.tag("Perf")
            .d("$operation: ${durationMs}ms (avg: ${record.getStats().avg}ms)")
    }

    fun recordDurationIfSlow(operation: String, durationMs: Long, thresholdMs: Long = 1000) {
        if (durationMs > thresholdMs) {
            Timber.tag("Perf")
                .w("$operation SLOW: ${durationMs}ms (threshold: ${thresholdMs}ms)")
        }
        recordDuration(operation, durationMs)
    }

    fun getStats(operation: String): MetricStats? {
        return metrics[operation]?.getStats()
    }

    fun getAllStats(): Map<String, MetricStats> {
        return metrics.mapValues { it.value.getStats() }
    }

    fun clear() {
        metrics.clear()
    }
}

class MetricRecord(
    val operation: String,
    private val samples: MutableList<Long> = mutableListOf()
) {
    fun addSample(durationMs: Long) {
        synchronized(samples) {
            samples.add(durationMs)
            if (samples.size > MAX_SAMPLES) {
                samples.removeAt(0)
            }
        }
    }

    fun getStats(): MetricStats {
        synchronized(samples) {
            if (samples.isEmpty()) return MetricStats(0, 0, 0, 0)

            val sorted = samples.sorted()
            return MetricStats(
                count = samples.size,
                avg = samples.average().toLong(),
                min = sorted.first(),
                max = sorted.last(),
                p50 = sorted[sorted.size / 2],
                p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)],
                p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.lastIndex)]
            )
        }
    }

    companion object {
        private const val MAX_SAMPLES = 1000
    }
}

data class MetricStats(
    val count: Int,
    val avg: Long,
    val min: Long,
    val max: Long,
    val p50: Long = 0,
    val p95: Long = 0,
    val p99: Long = 0
)
