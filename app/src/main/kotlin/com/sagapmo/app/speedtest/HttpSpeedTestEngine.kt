package com.sagapmo.app.speedtest

import com.sagapmo.app.reading.ConnectionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
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
    private val maxBytesPerPhase: Long = 150_000_000L,
    private val maxDurationMsPerPhase: Long? = null,
) : SpeedTestEngine {

    override fun runSpeedTest(connectionAgeMillis: Long?, connectionType: ConnectionType): Flow<SpeedTestProgress> = channelFlow {
        val phaseDurationNanos = TimeUnit.MILLISECONDS.toNanos(maxDurationMsPerPhase ?: connectionType.speedTestPhaseDurationMillis)
        var latencyMs: Double? = null
        var jitterMs: Double? = null

        send(SpeedTestProgress(phase = SpeedTestPhase.STABILIZING, connectionAgeMillisAtStart = connectionAgeMillis))
        delay(2_500)

        // 1. Latency & Jitter phase
        send(
            SpeedTestProgress(
                phase = SpeedTestPhase.LATENCY,
                overallProgress = 0.05f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        val pingSamples = mutableListOf<Long>()
        repeat(3) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
            val rtt = measurePing(pingUrl, deadline)
            if (rtt != null) pingSamples.add(rtt)
            delay(TimeUnit.NANOSECONDS.toMillis((deadline - System.nanoTime()).coerceAtLeast(0L)))
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

        send(
            SpeedTestProgress(
                phase = SpeedTestPhase.LATENCY,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                overallProgress = 0.2f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        // 2. Download phase
        send(
            SpeedTestProgress(
                phase = SpeedTestPhase.DOWNLOAD,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                overallProgress = 0.2f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        val download = measureDownload(downloadUrl, phaseDurationNanos) { currentRate, fraction ->
            send(
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
        val downloadMbps = download.mbps

        send(
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
        send(
            SpeedTestProgress(
                phase = SpeedTestPhase.UPLOAD,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                overallProgress = 0.6f,
                connectionAgeMillisAtStart = connectionAgeMillis
            )
        )

        val uploadMbps = measureUpload(uploadUrl, phaseDurationNanos, connectionType) { fraction, rate ->
            trySend(
                SpeedTestProgress(
                    phase = SpeedTestPhase.UPLOAD,
                    latencyMs = latencyMs,
                    jitterMs = jitterMs,
                    downloadMbps = downloadMbps,
                    currentRateMbps = rate,
                    overallProgress = 0.6f + 0.4f * fraction,
                    connectionAgeMillisAtStart = connectionAgeMillis
                )
            )
        }

        send(
            SpeedTestProgress(
                phase = if (downloadMbps != null && uploadMbps != null) SpeedTestPhase.COMPLETED else SpeedTestPhase.FAILED,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                overallProgress = 1.0f,
                connectionAgeMillisAtStart = connectionAgeMillis,
                downloadRateLimited = download.rateLimited
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

    private fun measurePing(url: String, deadline: Long): Long? {
        fun probe(request: Request): Long? {
            val start = System.nanoTime()
            if (start >= deadline) return null
            val call = client.newCall(request)
            call.timeout().deadlineNanoTime(deadline)
            return call.execute().use { response ->
                if (response.isSuccessful) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) else null
            }
        }
        val request = Request.Builder().url(url)
        return try {
            probe(request.head().build())
        } catch (_: IOException) {
            try {
                probe(request.get().build())
            } catch (_: IOException) {
                null
            }
        }
    }

    private data class DownloadMeasurement(val mbps: Double?, val rateLimited: Boolean)

    private suspend fun measureDownload(
        url: String,
        durationNanos: Long,
        onProgress: suspend (currentRateMbps: Double, fraction: Float) -> Unit
    ): DownloadMeasurement {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        val buffer = ByteArray(8192)
        val startTime = System.nanoTime()
        var totalBytesRead = 0L
        var elapsedAtLastRead = 0L
        var lastProgressNanos = 0L
        var rateLimited = false
        try {
            while (totalBytesRead < maxBytesPerPhase && System.nanoTime() - startTime < durationNanos &&
                currentCoroutineContext().isActive
            ) {
                val bytesBeforeRequest = totalBytesRead
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        rateLimited = response.code == 429
                        return@use
                    }
                    val stream = response.body?.byteStream() ?: return@use
                    while (totalBytesRead < maxBytesPerPhase && System.nanoTime() - startTime < durationNanos &&
                        currentCoroutineContext().isActive
                    ) {
                        val readLimit = minOf(buffer.size.toLong(), maxBytesPerPhase - totalBytesRead).toInt()
                        val read = stream.read(buffer, 0, readLimit)
                        if (read == -1) break
                        totalBytesRead += read
                        val elapsedNanos = System.nanoTime() - startTime
                        elapsedAtLastRead = elapsedNanos
                        if (elapsedNanos - lastProgressNanos >= TimeUnit.MILLISECONDS.toNanos(150)) {
                            val rate = megabitsPerSecond(totalBytesRead, elapsedNanos)
                            onProgress(rate, (elapsedNanos.toFloat() / durationNanos).coerceAtMost(1f))
                            lastProgressNanos = elapsedNanos
                        }
                    }
                }
                if (totalBytesRead == bytesBeforeRequest) break
            }
        } catch (_: IOException) {
            // Keep the bytes measured before a later request or read failed.
        }
        val mbps = if (totalBytesRead > 0 && elapsedAtLastRead > 0) megabitsPerSecond(totalBytesRead, elapsedAtLastRead) else null
        return DownloadMeasurement(mbps, rateLimited)
    }

    private suspend fun measureUpload(
        url: String,
        maxDurationNanos: Long,
        connectionType: ConnectionType,
        onProgress: (fraction: Float, rate: Double?) -> Unit
    ): Double? {
        val dummyChunk = ByteArray(16384)
        val context = currentCoroutineContext()
        var startTime = 0L
        var totalBytesSent = 0L
        var confirmedBytes = 0L
        var confirmedElapsedNanos = 0L
        var nextProgress = 0.25f
        var sampleStartNanos = 0L
        var sampleStartBytes = 0L
        var lastEstimateNanos = 0L
        var estimate: Double? = null

        val mobileUpload = connectionType == ConnectionType.MOBILE
        fun uploadBody(batchSize: Long) = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun isOneShot() = true
            override fun contentLength() = if (mobileUpload) batchSize else -1L

            override fun writeTo(sink: BufferedSink) {
                if (startTime == 0L) startTime = System.nanoTime()
                val batchEnd = totalBytesSent + batchSize
                while (totalBytesSent < batchEnd && (mobileUpload || System.nanoTime() - startTime < maxDurationNanos) && context.isActive) {
                    val toWrite = minOf(dummyChunk.size.toLong(), batchEnd - totalBytesSent).toInt()
                    sink.write(dummyChunk, 0, toWrite)
                    totalBytesSent += toWrite
                    val elapsedNanos = System.nanoTime() - startTime
                    val fraction = maxOf(totalBytesSent.toFloat() / maxBytesPerPhase, elapsedNanos.toFloat() / maxDurationNanos)
                    // Skip the initial writes, which can mostly fill the local buffer.
                    if (sampleStartNanos == 0L && elapsedNanos >= TimeUnit.MILLISECONDS.toNanos(150)) {
                        sampleStartNanos = elapsedNanos
                        sampleStartBytes = totalBytesSent
                    }
                    val sampleElapsed = elapsedNanos - sampleStartNanos
                    val updateEstimate = sampleStartNanos > 0 && sampleElapsed >= TimeUnit.MILLISECONDS.toNanos(350) &&
                        elapsedNanos - lastEstimateNanos >= TimeUnit.MILLISECONDS.toNanos(250)
                    if (updateEstimate) {
                        estimate = megabitsPerSecond(totalBytesSent - sampleStartBytes, sampleElapsed)
                        lastEstimateNanos = elapsedNanos
                    }
                    if (fraction < 1f && (fraction >= nextProgress || updateEstimate)) {
                        onProgress(fraction, estimate)
                        nextProgress = fraction + 0.25f
                    }
                }
            }
        }

        try {
            do {
                val bytesBeforeRequest = totalBytesSent
                val batchSize = minOf(if (mobileUpload) 65_536L else 25_000_000L, maxBytesPerPhase - totalBytesSent)
                val request = Request.Builder().url(url).post(uploadBody(batchSize)).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logger.getLogger("SagapMoSpeedTest").warning("Upload response HTTP ${response.code}")
                        return@use
                    }
                    response.header("cf-meta-upload-bytes")?.let { serverBytes ->
                        if (serverBytes.toLongOrNull() != totalBytesSent - bytesBeforeRequest) {
                            Logger.getLogger("SagapMoSpeedTest").warning("Upload byte count mismatch")
                            return@use
                        }
                    }
                    confirmedBytes = totalBytesSent
                    confirmedElapsedNanos = System.nanoTime() - startTime
                }
                if (confirmedBytes == bytesBeforeRequest || confirmedBytes != totalBytesSent) break
            } while (totalBytesSent < maxBytesPerPhase && System.nanoTime() - startTime < maxDurationNanos && context.isActive)
        } catch (error: IOException) {
            Logger.getLogger("SagapMoSpeedTest").warning("Upload interrupted ${error.javaClass.simpleName}")
        }
        return if (confirmedBytes > 0 && confirmedElapsedNanos > 0) megabitsPerSecond(confirmedBytes, confirmedElapsedNanos) else null
    }

    private fun megabitsPerSecond(bytes: Long, elapsedNanos: Long): Double =
        bytes * 8_000.0 / elapsedNanos
}
