package com.sagapmo.app.speedtest

import kotlinx.coroutines.flow.Flow
import com.sagapmo.app.reading.ConnectionType

enum class SpeedTestStatus {
    COMPLETED,
    FAILED,
    SKIPPED
}

enum class SpeedTestPhase {
    IDLE,
    STABILIZING,
    LATENCY,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    FAILED
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val latencyMs: Double? = null,
    val jitterMs: Double? = null,
    val currentRateMbps: Double? = null,
    val overallProgress: Float = 0f,
    val connectionAgeMillisAtStart: Long? = null,
    val downloadRateLimited: Boolean = false,
) {
    internal val phaseProgress: Float
        get() = when (phase) {
            SpeedTestPhase.LATENCY -> overallProgress / 0.2f
            SpeedTestPhase.DOWNLOAD -> (overallProgress - 0.2f) / (0.6f - 0.2f)
            SpeedTestPhase.UPLOAD -> (overallProgress - 0.6f) / (1f - 0.6f)
            else -> overallProgress
        }.coerceIn(0f, 1f)
}

interface SpeedTestEngine {
    fun runSpeedTest(connectionAgeMillis: Long? = null, connectionType: ConnectionType = ConnectionType.WIFI): Flow<SpeedTestProgress>
}

internal val ConnectionType.speedTestPhaseDurationMillis: Long
    get() = when (this) {
        ConnectionType.WIFI -> 10_000L
        ConnectionType.MOBILE -> 5_000L
    }
