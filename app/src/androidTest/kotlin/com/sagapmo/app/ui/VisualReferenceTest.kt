package com.sagapmo.app.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.sagapmo.app.*
import com.sagapmo.app.capture.*
import com.sagapmo.app.reading.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.OffsetDateTime
import org.maplibre.compose.style.BaseStyle

class VisualReferenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(effectContext = Dispatchers.Main)
    @Test fun captureReferenceStates() {
        val set = InstrumentationRegistry.getArguments().getString("visualSet")
        Assume.assumeNotNull(set)
        val dao = VisualDao(referenceRows())
        var gate: CompletableDeferred<Unit>? = null
        val signal = object : SignalReader {
            override fun currentConnection() = ConnectionResult.Available(ConnectionSnapshot(1, ConnectionType.WIFI, null))
            override suspend fun read() = SignalResult.Available(SignalSample(ConnectionType.WIFI, -62, SignalUnit.DBM, Rating.GOOD, null,
                OffsetDateTime.parse("2026-11-17T10:42:00+08:00").toInstant().toEpochMilli(), 100_000))
        }
        lateinit var model: SagapMoViewModel
        compose.runOnUiThread {
            model = SagapMoViewModel(signal, object : LocationReader {
                override fun currentAccess() = LocationAccess(true, true, true)
                override suspend fun locate(): LocationResult { gate?.await(); return LocationResult.Missing(NoLocationReason.APPROXIMATE_ONLY) }
            }, dao)
            model.setDestination(Destination.READINGS)
        }
        var style by mutableStateOf<BaseStyle>(BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"))
        compose.runOnUiThread { compose.activity.setContent { SagapMoTheme { SagapMoApp(model, { model.record(false) }, { model.onPermissionResult() }, {}, style) } } }
        compose.onNodeWithText("Your readings").assertIsDisplayed()
        capture("$set-readings")
        compose.onNodeWithTag("reading-sample-1").performClick()
        compose.onNodeWithText("Reading details").assertIsDisplayed()
        capture("$set-detail")
        compose.onNodeWithContentDescription("Close reading details").performClick()
        compose.onNodeWithContentDescription("Map").performClick()
        compose.waitUntil(25_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        capture("$set-map")
        compose.runOnUiThread { model.record(true) }
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.AWAITING_PERMISSION }
        capture("$set-permission")
        compose.onNodeWithText("Save without a pin").performScrollTo().performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        capture("$set-saved-no-pin")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.runOnUiThread { gate = CompletableDeferred(); model.record(false) }
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.LOCATING }
        capture("$set-locating")
        compose.onNodeWithText("Cancel recording").performScrollTo().performClick()
        compose.runOnUiThread { dao.rows.value = emptyList() }
        compose.onNodeWithText("No readings yet").assertExists()
        capture("$set-empty")
        compose.runOnUiThread { style = BaseStyle.Uri("https://127.0.0.1:1/unavailable.json") }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty() }
        val filterBottom = compose.onNodeWithTag("filter-MOBILE").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(compose.onNodeWithContentDescription("Return to campus overview").fetchSemanticsNode().boundsInRoot.top > filterBottom)
        capture("$set-map-error")
        compose.onNodeWithText("View readings").performScrollTo().performClick()
        compose.onNodeWithText("Your readings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Record reading").assertIsEnabled()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); SystemClock.sleep(600)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "visual").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

private fun referenceRows(): List<Reading> {
    fun time(value: String) = OffsetDateTime.parse(value).toInstant().toEpochMilli()
    return listOf(
        sampleReading("sample-1").copy(capturedAtEpochMillis = time("2026-11-17T10:42:00+08:00")),
        mobileReading("sample-2").copy(capturedAtEpochMillis = time("2026-11-17T10:35:00+08:00"), latitude = 13.78390, longitude = 121.07410, accuracyMetres = 14f, locationAgeMillisAtCapture = 9000, noLocationReason = null),
        sampleReading("sample-3").copy(capturedAtEpochMillis = time("2026-11-17T10:28:00+08:00"), rawSignalValue = -79, rating = Rating.POOR,
            latitude = null, longitude = null, accuracyMetres = null, locationAgeMillisAtCapture = null, noLocationReason = NoLocationReason.APPROXIMATE_ONLY),
        mobileReading("sample-4").copy(capturedAtEpochMillis = time("2026-11-17T10:15:00+08:00"), rawSignalValue = 4, rating = Rating.GOOD,
            latitude = 13.78455, longitude = 121.07460, accuracyMetres = 10f, locationAgeMillisAtCapture = 4000, noLocationReason = null),
        sampleReading("sample-5").copy(capturedAtEpochMillis = time("2026-11-16T15:20:00+08:00"), rawSignalValue = -71, rating = Rating.FAIR),
        mobileReading("sample-6").copy(capturedAtEpochMillis = time("2026-11-16T14:04:00+08:00"), rawSignalValue = 1, rating = Rating.POOR,
            latitude = 13.78410, longitude = 121.07440, accuracyMetres = 12f, locationAgeMillisAtCapture = 3000, noLocationReason = null),
    )
}
private class VisualDao(initial: List<Reading>) : ReadingDao() {
    val rows = MutableStateFlow(initial)
    override fun observeReadings(type: ConnectionType?) = rows.asStateFlow()
    override suspend fun deleteById(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun setManualLocation(id: String, latitude: Double, longitude: Double): Int {
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(latitude = latitude, longitude = longitude,
            accuracyMetres = null, locationAgeMillisAtCapture = null, noLocationReason = null, locationManuallySet = true) else it }
        return 1
    }
    override suspend fun setLocationDescription(id: String, description: String): Int {
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(locationDescription = description) else it }
        return 1
    }
    override suspend fun getById(id: String) = rows.value.find { it.id == id }
    override suspend fun insertIfAbsent(reading: Reading): Long {
        if (getById(reading.id) != null) return -1
        rows.value = (rows.value + reading).sortedByDescending { it.capturedAtEpochMillis }; return 1
    }
}
