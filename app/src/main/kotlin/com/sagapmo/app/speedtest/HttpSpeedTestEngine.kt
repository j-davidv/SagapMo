package com.sagapmo.app.speedtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class HttpSpeedTestEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val pingUrl: String = "https://speed.cloudflare.com/__down?bytes=0",
    // Cloudflare rejected the larger download for native clients without browser headers.
    private val downloadUrl: String = "https://speed.cloudflare.com/__down?bytes=10000000",
    private val uploadUrl: String = "https://speed.cloudflare.com/__up",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytesPerPhase: Long = 15L * 1024L * 1024L,
    private val maxDurationMsPerPhase: Long = 5000L,
) : SpeedTestEngine {

    override fun runSpeedTest(connectionAgeMillis: Long?): Flow<SpeedTestProgress> = flow {
        var latencyMs: Double? = null
        var jitterMs: Double? = null
        var downloadMbps: Double? = null
        var uploadMbps: Double? = null

        // 1. Latency & Jitter phase
        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.LATENCY,
                overallProgress = 0.05f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        val pingSamples = mutableListOf<Long>()
        val pingCount = 3
        for (i in 0 until pingCount) {
            if (!currentCoroutineContext().isActive) break
            val rtt = measurePing(pingUrl)
            if (rtt != null) pingSamples.add(rtt)
        }

        if (pingSamples.isNotEmpty()) {
            latencyMs = pingSamples.average().coerceAtLeast(0.0)
            jitterMs = if (pingSamples.size > 1) {
                var diffSum = 0.0
                for (i in 0 until pingSamples.size - 1) {
                    diffSum += abs(pingSamples[i + 1] - pingSamples[i])
                }
                (diffSum / (pingSamples.size - 1)).coerceAtLeast(0.0)
            } else 0.0
        }

        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.LATENCY,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                overallProgress = 0.2f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        // 2. Download phase
        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.DOWNLOAD,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                overallProgress = 0.25f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        downloadMbps = measureDownload(downloadUrl) { currentRate, fraction ->
            emit(
                SpeedTestProgress(
                    phase = SpeedTestPhase.DOWNLOAD,
                    latencyMs = latencyMs,
                    jitterMs = jitterMs,
                    currentRateMbps = currentRate,
                    downloadMbps = currentRate,
                    overallProgress = 0.2f + 0.4f * fraction,
                    connectionAgeMillisAtStart = connectionAgeMillis
                )
            )
        }

        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.DOWNLOAD,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                currentRateMbps = downloadMbps,
                overallProgress = 0.6f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        // 3. Upload phase
        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.UPLOAD,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                overallProgress = 0.65f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        uploadMbps = measureUpload(uploadUrl) { currentRate, fraction ->
            emit(
                SpeedTestProgress(
                    phase = SpeedTestPhase.UPLOAD,
                    latencyMs = latencyMs,
                    jitterMs = jitterMs,
                    downloadMbps = downloadMbps,
                    uploadMbps = currentRate,
                    currentRateMbps = currentRate,
                    overallProgress = 0.6f + 0.4f * fraction,
                    connectionAgeMillisAtStart = connectionAgeMillis
                )
            )
        }

        emit(
            SpeedTestProgress(
                phase = if (downloadMbps != null && uploadMbps != null) SpeedTestPhase.COMPLETED else SpeedTestPhase.FAILED,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                overallProgress = 1.0f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.FAILED,
                overallProgress = 1.0f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )
    }.flowOn(ioDispatcher)

    private fun measurePing(url: String): Long? {
        val request = Request.Builder().url(url).head().build()
        val start = System.nanoTime()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    (System.nanoTime() - start) / 1_000_000L
                } else null
            }
        } catch (_: Exception) {
            try {
                val getRequest = Request.Builder().url(url).build()
                val getStart = System.nanoTime()
                client.newCall(getRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        (System.nanoTime() - getStart) / 1_000_000L
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun measureDownload(
        url: String,
        onProgress: suspend (currentRateMbps: Double, fraction: Float) -> Unit
    ): Double? {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val stream = body.byteStream()
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                val startTime = System.nanoTime()
                val maxDurationNanos = TimeUnit.MILLISECONDS.toNanos(maxDurationMsPerPhase)
                var elapsedNanos = 0L
                var lastProgressNanos = 0L

                while (totalBytesRead < maxBytesPerPhase && elapsedNanos < maxDurationNanos &&
                    currentCoroutineContext().isActive
                ) {
                    val readLimit = minOf(buffer.size.toLong(), maxBytesPerPhase - totalBytesRead).toInt()
                    val read = stream.read(buffer, 0, readLimit)
                    if (read == -1) break
                    totalBytesRead += read
                    elapsedNanos = System.nanoTime() - startTime

                    if (elapsedNanos - lastProgressNanos >= TimeUnit.MILLISECONDS.toNanos(150)) {
                        val byteFraction = totalBytesRead.toFloat() / maxBytesPerPhase
                        val timeFraction = elapsedNanos.toFloat() / maxDurationNanos
                        val rate = megabitsPerSecond(totalBytesRead, elapsedNanos)
                        onProgress(rate, maxOf(byteFraction, timeFraction).coerceAtMost(1f))
                        lastProgressNanos = elapsedNanos
                    }
                }

                if (elapsedNanos > 0 && totalBytesRead > 0) megabitsPerSecond(totalBytesRead, elapsedNanos) else null
            }
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun measureUpload(
        url: String,
        onProgress: suspend (currentRateMbps: Double, fraction: Float) -> Unit
    ): Double? {
        val dummyChunk = ByteArray(16384)
        val maxDurationNanos = TimeUnit.MILLISECONDS.toNanos(maxDurationMsPerPhase)
        val context = currentCoroutineContext()
        var startTime = 0L
        var totalBytesSent = 0L

        val requestBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun isOneShot() = true

            override fun writeTo(sink: BufferedSink) {
                startTime = System.nanoTime()
                while (totalBytesSent < maxBytesPerPhase && System.nanoTime() - startTime < maxDurationNanos &&
                    context.isActive
                ) {
                    val toWrite = minOf(dummyChunk.size.toLong(), maxBytesPerPhase - totalBytesSent).toInt()
                    sink.write(dummyChunk, 0, toWrite)
                    totalBytesSent += toWrite
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val elapsedNanos = System.nanoTime() - startTime
                if (!response.isSuccessful || totalBytesSent == 0L || elapsedNanos <= 0) return null
                response.header("cf-meta-upload-bytes")?.let { confirmedBytes ->
                    if (confirmedBytes.toLongOrNull() != totalBytesSent) return null
                }
                val rate = megabitsPerSecond(totalBytesSent, elapsedNanos)
                onProgress(rate, 1.0f)
                rate
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun megabitsPerSecond(bytes: Long, elapsedNanos: Long): Double =
        bytes * 8_000.0 / elapsedNanos
}
