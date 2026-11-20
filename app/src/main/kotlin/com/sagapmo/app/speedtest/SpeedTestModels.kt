package com.sagapmo.app.speedtest

import kotlinx.coroutines.flow.Flow

enum class SpeedTestStatus {
    COMPLETED,
    FAILED,
    SKIPPED
}

enum class SpeedTestPhase {
    IDLE,
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
)

interface SpeedTestEngine {
    fun runSpeedTest(connectionAgeMillis: Long? = null): Flow<SpeedTestProgress>
}
