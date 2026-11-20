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

        val baseUrl = server.url("/").toString()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()

        val engine = HttpSpeedTestEngine(
            client = client,
            pingUrl = "${baseUrl}ping",
            downloadUrl = "${baseUrl}down",
            uploadUrl = "${baseUrl}up",
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
        assertNotNull(finalProgress.downloadMbps)
        assertNotNull(finalProgress.uploadMbps)
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
        assertEquals(SpeedTestPhase.COMPLETED, finalProgress.phase)
        assertEquals(0.0, finalProgress.downloadMbps!!, 0.01)
        assertEquals(0.0, finalProgress.uploadMbps!!, 0.01)
    }
}
