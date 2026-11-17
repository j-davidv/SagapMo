package com.sagapmo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sagapmo.app.*
import com.sagapmo.app.capture.*
import com.sagapmo.app.reading.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class SagapMoAppTest {
    @get:Rule val compose = createComposeRule()
    private val dao = UiDao()
    private var sample = SignalSample(ConnectionType.MOBILE, 3, SignalUnit.ANDROID_LEVEL, Rating.GOOD, 1000, 1_794_883_320_000, 100_000)
    private var location: LocationResult = LocationResult.Missing(NoLocationReason.APPROXIMATE_ONLY)
    private val signal = object : SignalReader {
        override fun currentConnection() = ConnectionResult.Available(ConnectionSnapshot(1, sample.connectionType, 1))
        override suspend fun read() = SignalResult.Available(sample)
    }
    private lateinit var model: SagapMoViewModel
    private fun start(rows: List<Reading> = emptyList()) {
        dao.rows.value = rows
        compose.runOnUiThread {
            model = SagapMoViewModel(signal, object : LocationReader {
                override fun currentAccess() = LocationAccess(true, true, true)
                override suspend fun locate(): LocationResult = location
            }, dao)
            model.setDestination(Destination.READINGS)
        }
        compose.setContent { SagapMoTheme { SagapMoApp(model, onRecord = { model.record(false) }) } }
    }
    @Test fun emptyDatabaseExplainsFirstReading() {
        start(); compose.onNodeWithText("No readings yet").assertIsDisplayed()
        compose.onNodeWithText("Record reading").assertIsDisplayed()
    }
    @Test fun noMatchesNamesEachFilterAndCanShowAll() {
        start(listOf(sampleReading()))
        compose.onNodeWithTag("filter-MOBILE").performClick()
        compose.onNodeWithText("No Mobile readings").assertIsDisplayed()
        compose.onNodeWithText("Show all readings").performClick()
        compose.onNodeWithTag("reading-test-wifi").assertIsDisplayed()
        compose.runOnIdle { dao.rows.value = listOf(mobileReading()) }
        compose.onNodeWithTag("filter-WIFI").performClick()
        compose.onNodeWithText("No Wi-Fi readings").assertIsDisplayed()
    }
    @Test fun recordsMobileWhileWifiFilterIsSelected() {
        start(); compose.onNodeWithTag("filter-WIFI").performClick()
        compose.onNodeWithText("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        compose.onNodeWithText("Reading saved").assertIsDisplayed()
        compose.onNodeWithText("Show saved reading").performClick()
        compose.onNode(hasText("Level 3/4") and hasAnyAncestor(hasTestTag("reading-details"))).assertIsDisplayed()
        assertEquals(ConnectionType.MOBILE, dao.rows.value.single().connectionType)
        assertNull(model.uiState.value.filter)
    }
    @Test fun exactCoordinateDuplicatesOpenTheirOwnStoredDetails() {
        start(listOf(sampleReading("new"), sampleReading("old").copy(capturedAtEpochMillis = 1000, rawSignalValue = -71, rating = Rating.FAIR)))
        compose.onAllNodes(hasTestTag("reading-new") or hasTestTag("reading-old"))[0].assert(hasTestTag("reading-new"))
        compose.onNodeWithTag("reading-old").performClick()
        compose.onNode(hasText("-71 dBm") and hasAnyAncestor(hasTestTag("reading-details"))).assertIsDisplayed()
        compose.onNodeWithText("13.78422, 121.07431").assertIsDisplayed()
        compose.onNodeWithText("Wi-Fi measurement age unavailable. Android does not provide a timestamp for this RSSI value.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show on map").performScrollTo().assertIsDisplayed()
    }
    @Test fun everyMissingLocationReasonIsExplainedInDetails() {
        start(listOf(mobileReading()))
        for (reason in NoLocationReason.entries) {
            compose.runOnIdle { model.openReading(mobileReading().copy(noLocationReason = reason)) }
            compose.onNodeWithText(reason.explanation()).assertExists()
            compose.onNodeWithText("Show on map").assertDoesNotExist()
            compose.onNodeWithContentDescription("Close reading details").performClick()
        }
    }
    @Test fun saveProgressAndRetryUseRetainedCandidate() {
        dao.gate = CompletableDeferred(); dao.failSave = true; start()
        compose.onNodeWithText("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVING }
        compose.onNodeWithText("Saving reading").assertIsDisplayed()
        compose.runOnIdle { dao.gate!!.complete(Unit) }
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVE_FAILED }
        val id = model.uiState.value.pendingSave!!.id
        compose.runOnIdle { dao.failSave = false }
        compose.onNodeWithText("Retry save").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        assertEquals(id, dao.rows.value.single().id)
    }
    @Test fun readFailureCanRetryWithoutResettingCollection() {
        dao.failRead = true; start()
        compose.onNodeWithText("Readings could not load").assertIsDisplayed()
        compose.runOnIdle { dao.failRead = false }
        compose.onNodeWithText("Retry readings").performClick()
        compose.onNodeWithText("No readings yet").assertIsDisplayed()
    }
}

private class UiDao : ReadingDao() {
    val rows = MutableStateFlow<List<Reading>>(emptyList())
    var failRead = false
    var failSave = false
    var gate: CompletableDeferred<Unit>? = null
    override fun observeReadings(type: ConnectionType?): Flow<List<Reading>> = flow {
        if (failRead) error("read error")
        emitAll(rows.map { it.sortedWith(compareByDescending<Reading> { r -> r.capturedAtEpochMillis }.thenByDescending { r -> r.id }) })
    }
    override suspend fun getById(id: String) = rows.value.find { it.id == id }
    override suspend fun insertIfAbsent(reading: Reading): Long {
        gate?.await(); if (failSave) error("disk full")
        if (getById(reading.id) != null) return -1
        rows.value += reading; return 1
    }
}
