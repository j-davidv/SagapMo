package com.sagapmo.app.speedtest

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

        val progressList = engine.runSpeedTest(connectionAgeMillis = 5000L).toList()

        assertTrue("Should have emitted progress events", progressList.isNotEmpty())
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
    fun runSpeedTestPreservesUploadAfterDownloadIsRejected() = runTest(testDispatcher) {
        repeat(3) { server.enqueue(MockResponse()) }
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse())

        val result = localEngine().runSpeedTest().toList().last()

        assertEquals(SpeedTestPhase.FAILED, result.phase)
        assertNull(result.downloadMbps)
        assertTrue(result.uploadMbps!! > 0.0)
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

    private fun localEngine() = HttpSpeedTestEngine(
        pingUrl = server.url("/ping").toString(),
        downloadUrl = server.url("/down").toString(),
        uploadUrl = server.url("/up").toString(),
        ioDispatcher = testDispatcher,
        maxBytesPerPhase = 65536L
    )
}
