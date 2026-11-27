package com.sagapmo.app.speedtest

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HttpSpeedTestEngineTest {

    private lateinit var server: MockWebServer
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun runSpeedTestSuccessfulFlow() = runTest(testDispatcher) {
        // Mock 3 ping responses
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        // Mock download response with 64KB data
        val downloadData = "A".repeat(65536)
        server.enqueue(MockResponse().setResponseCode(200).setBody(downloadData))

        // Mock upload response
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val localUrl = server.url("/").newBuilder()
                    .encodedPath(request.url.encodedPath)
                    .encodedQuery(request.url.encodedQuery)
                    .build()
                chain.proceed(request.newBuilder().url(localUrl).build())
            }
            .build()

        val engine = HttpSpeedTestEngine(
            client = client,
            ioDispatcher = testDispatcher,
            maxBytesPerPhase = 65536L,
            maxDurationMsPerPhase = 1000L
        )

        val phaseStarts = mutableMapOf<SpeedTestPhase, Long>()
        val progressList = engine.runSpeedTest(connectionAgeMillis = 5000L)
            .onEach { phaseStarts.putIfAbsent(it.phase, testScheduler.currentTime) }
            .toList()

        assertTrue("Should have emitted progress events", progressList.isNotEmpty())
        assertEquals(SpeedTestPhase.STABILIZING, progressList.first().phase)
        assertEquals(2_500L, phaseStarts.getValue(SpeedTestPhase.LATENCY))
        assertTrue(phaseStarts.getValue(SpeedTestPhase.DOWNLOAD) - phaseStarts.getValue(SpeedTestPhase.LATENCY) in 1_000L..1_500L)
        assertTrue(progressList.any {
            it.phase == SpeedTestPhase.UPLOAD && it.overallProgress in 0.61f..0.99f
        })
        assertTrue(progressList.filter { it.phase == SpeedTestPhase.UPLOAD }.all {
            it.currentRateMbps == null && it.uploadMbps == null
        })
        assertEquals(1f, progressList.last { it.phase == SpeedTestPhase.DOWNLOAD }.phaseProgress, 0.001f)
        assertEquals(0f, progressList.first { it.phase == SpeedTestPhase.UPLOAD }.phaseProgress, 0.001f)
        assertTrue(progressList.zipWithNext().all { (previous, next) -> previous.overallProgress <= next.overallProgress })
        val finalProgress = progressList.last()

        assertEquals(SpeedTestPhase.COMPLETED, finalProgress.phase)
        assertEquals(1.0f, finalProgress.overallProgress, 0.01f)
        assertEquals(5000L, finalProgress.connectionAgeMillisAtStart)
        assertNotNull(finalProgress.latencyMs)
        assertNotNull(finalProgress.jitterMs)
        assertTrue(finalProgress.downloadMbps!! > 0.0)
        assertTrue(finalProgress.uploadMbps!! > 0.0)
        repeat(3) { assertEquals("HEAD", server.takeRequest(1, TimeUnit.SECONDS)?.method) }
        val download = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("/__down?bytes=10000000", download?.path)
        assertEquals("identity", download?.getHeader("Accept-Encoding"))
        val upload = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("chunked", upload?.getHeader("Transfer-Encoding"))
        assertEquals(65536L, upload?.bodySize)
    }

    @Test
    fun runSpeedTestReportsRateLimitedDownloadAndPreservesUpload() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse())

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.FAILED, result.phase)
        assertNull(result.downloadMbps)
        assertTrue(result.downloadRateLimited)
        assertTrue(result.uploadMbps!! > 0.0)
    }

    @Test
    fun sameEngineCanRunAgainAfterInternetFailure() = runTest(testDispatcher) {
        var offline = true
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (offline) throw java.io.IOException("offline")
            chain.proceed(chain.request())
        }.build()
        val engine = localEngine(client)
        val failed = engine.runSpeedTest().toList().last()
        assertEquals(SpeedTestPhase.FAILED, failed.phase)
        assertNull(failed.downloadMbps)
        assertNull(failed.uploadMbps)

        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("A".repeat(65536)))
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "65536"))
        offline = false
        val recovered = engine.runSpeedTest().toList().last()
        assertEquals(SpeedTestPhase.COMPLETED, recovered.phase)
        assertTrue(recovered.downloadMbps!! > 0.0)
        assertTrue(recovered.uploadMbps!! > 0.0)
    }

    @Test
    fun runSpeedTestPreservesDownloadAfterUploadIsRejected() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("A".repeat(65536)))
        server.enqueue(MockResponse().setResponseCode(413))

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.FAILED, result.phase)
        assertTrue(result.downloadMbps!! > 0.0)
        assertNull(result.uploadMbps)
    }

    @Test
    fun mobileUploadPreservesSuccessfulBatchesAfterALaterRequestFails() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("download"))
        server.enqueue(MockResponse())
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "65536"))
        server.enqueue(MockResponse().setResponseCode(503))
        val engine = HttpSpeedTestEngine(
            pingUrl = server.url("/ping").toString(),
            downloadUrl = server.url("/down").toString(),
            uploadUrl = server.url("/up").toString(),
            ioDispatcher = testDispatcher,
            maxBytesPerPhase = 131072L
        )

        val result = engine.runSpeedTest(connectionType = com.sagapmo.app.reading.ConnectionType.MOBILE).toList().last()

        assertTrue("Keep the server-confirmed upload measurement", (result.uploadMbps ?: 0.0) > 0.0)
        assertTrue((result.downloadMbps ?: 0.0) > 0.0)
    }

    @Test
    fun mobileUploadDeclaresItsSizeBeforeSending() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("A".repeat(65536)))
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "65536"))

        val result = localEngine().runSpeedTest(connectionType = com.sagapmo.app.reading.ConnectionType.MOBILE).toList().last()

        assertNotNull(result.uploadMbps)
        repeat(4) { server.takeRequest(1, TimeUnit.SECONDS) }
        val upload = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("65536", upload?.getHeader("Content-Length"))
        assertNull(upload?.getHeader("Transfer-Encoding"))
    }

    @Test
    fun runSpeedTestRejectsUploadWithMismatchedByteCount() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("A".repeat(65536)))
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "60000"))

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.FAILED, result.phase)
        assertNull(result.uploadMbps)
    }

    @Test
    fun runSpeedTestUsesPayloadBytesAndElapsedTimeForMbps() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse()
            .setBody("A".repeat(65536))
            .throttleBody(16384, 150, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse()
            .setHeader("cf-meta-upload-bytes", "65536")
            .setHeadersDelay(500, TimeUnit.MILLISECONDS))

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.COMPLETED, result.phase)
        assertTrue("Expected about 1.2 Mbps down, got ${result.downloadMbps}", result.downloadMbps!! in 0.2..1.3)
        assertTrue("Expected about 1.0 Mbps up, got ${result.uploadMbps}", result.uploadMbps!! in 0.2..1.1)
    }

    @Test
    fun downloadKeepsMeasuredBytesWhenALaterRequestFails() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("A".repeat(32768)))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "65536"))

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.COMPLETED, result.phase)
        assertTrue((result.downloadMbps ?: 0.0) > 0.0)
    }

    @Test
    fun downloadContinuesAfterTheFirstResponseEnds() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        repeat(2) { server.enqueue(MockResponse().setBody("A".repeat(32768))) }
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "65536"))

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.COMPLETED, result.phase)
        repeat(3) { assertEquals("HEAD", server.takeRequest(1, TimeUnit.SECONDS)?.method) }
        repeat(2) { assertEquals("/down", server.takeRequest(1, TimeUnit.SECONDS)?.path) }
        assertEquals("/up", server.takeRequest(1, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun sustainedUploadShowsAnEstimateBeforeTheFinalResult() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setBody("A".repeat(65536)))
        server.enqueue(MockResponse().setHeader("cf-meta-upload-bytes", "65536"))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body ?: return@addInterceptor chain.proceed(request)
            val slowBody = object : RequestBody() {
                override fun contentType() = body.contentType()

                override fun writeTo(sink: BufferedSink) {
                    val slowSink = object : ForwardingSink(sink) {
                        override fun write(source: Buffer, byteCount: Long) {
                            Thread.sleep(180)
                            super.write(source, byteCount)
                        }
                    }.buffer()
                    body.writeTo(slowSink)
                    slowSink.emit()
                }
            }
            chain.proceed(request.newBuilder().post(slowBody).build())
        }.build()

        val progress = localEngine(client).runSpeedTest().toList()
        val upload = progress.filter { it.phase == SpeedTestPhase.UPLOAD }
        assertNull(upload.first().currentRateMbps)
        assertTrue(upload.any { (it.currentRateMbps ?: 0.0) in 0.1..1.0 })
        assertTrue(upload.all { it.uploadMbps == null })
        assertNotNull(progress.last().uploadMbps)
    }

    @Test
    fun runSpeedTestHandlesFailingEndpointsGracefully() = runTest(testDispatcher) {
        val client = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        val engine = HttpSpeedTestEngine(
            client = client,
            pingUrl = "http://localhost:1", // Invalid port
            downloadUrl = "http://localhost:1",
            uploadUrl = "http://localhost:1",
            ioDispatcher = testDispatcher,
            maxDurationMsPerPhase = 500L
        )

        val progressList = engine.runSpeedTest().toList()
        assertTrue(progressList.isNotEmpty())
        val finalProgress = progressList.last()
        assertEquals(SpeedTestPhase.FAILED, finalProgress.phase)
        assertNull(finalProgress.downloadMbps)
        assertNull(finalProgress.uploadMbps)
    }

    private fun localEngine(client: OkHttpClient = OkHttpClient()) = HttpSpeedTestEngine(
        client = client,
        pingUrl = server.url("/ping").toString(),
        downloadUrl = server.url("/down").toString(),
        uploadUrl = server.url("/up").toString(),
        ioDispatcher = testDispatcher,
        maxBytesPerPhase = 65536L
    )
}
