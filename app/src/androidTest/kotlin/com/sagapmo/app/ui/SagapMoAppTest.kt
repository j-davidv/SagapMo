package com.sagapmo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sagapmo.app.*
import com.sagapmo.app.capture.*
import com.sagapmo.app.reading.*
import com.sagapmo.app.speedtest.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.maplibre.compose.style.BaseStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

class SagapMoAppTest {
    @Test fun capturedMobileGenerationAppearsInResultListAndDetails() {
        sample = sample.copy(mobileGeneration = MobileGeneration.FIVE_G)
        start()
        compose.runOnIdle { model.setIncludeSpeedTest(false) }
        compose.onNodeWithContentDescription("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        compose.onNodeWithText("Mobile · 5G · Good").assertIsDisplayed()
        compose.onNodeWithText("Show saved reading").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("reading-details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Mobile · 5G") and hasAnyAncestor(hasTestTag("reading-details"))).assertIsDisplayed()
        compose.onNodeWithText("Android network label at capture").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close reading details").performClick()
        compose.onNodeWithTag("reading-${dao.rows.value.single().id}")
            .assert(hasText("Mobile · 5G"))
        compose.runOnIdle { assertEquals(MobileGeneration.FIVE_G, dao.rows.value.single().mobileGeneration) }
    }

    @Test fun legacyMobileReadingDoesNotInventNetworkContext() {
        start(listOf(mobileReading()))
        compose.onNodeWithTag("reading-test-mobile").performClick()
        compose.onNodeWithText("Network generation unavailable").assertIsDisplayed()
        compose.onNodeWithText("Mobile · 4G").assertDoesNotExist()
        compose.onNodeWithText("Mobile · 5G").assertDoesNotExist()
    }

    @Test fun locationProgressAndOutcomeStayClearThroughSpeedTest() {
        val locationGate = CompletableDeferred<Unit>()
        val speedGate = CompletableDeferred<Unit>()
        val engine = object : SpeedTestEngine {
            override fun runSpeedTest(connectionAgeMillis: Long?, connectionType: ConnectionType) = flow {
                emit(SpeedTestProgress(phase = SpeedTestPhase.UPLOAD))
                speedGate.await()
                emit(SpeedTestProgress(phase = SpeedTestPhase.COMPLETED, downloadMbps = 24.0, uploadMbps = 12.5))
            }
        }
        start(speedTest = engine, locationGate = locationGate)
        compose.onNodeWithContentDescription("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.LOCATING }
        compose.onNodeWithText("Getting location").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        compose.onNodeWithText("Keep browsing").performClick()
        compose.onNodeWithText("Recording…").assertIsDisplayed()
        compose.onNodeWithText("View progress").assertDoesNotExist()
        compose.onNodeWithContentDescription("Map").performClick()
        compose.onNodeWithText("Recording…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Readings").performClick()
        locationGate.complete(Unit)
        compose.waitUntil(5000) { model.uiState.value.speedTestProgress?.phase == SpeedTestPhase.UPLOAD }
        compose.onNodeWithText("Measuring internet speed").assertDoesNotExist()
        compose.onNodeWithText("Recording…").performClick()
        compose.onNodeWithText("No map pin for this reading").assertIsDisplayed()
        compose.onNodeWithText("Testing upload speed…").assertIsDisplayed()
        compose.onNodeWithText("Keep browsing").performScrollTo().performClick()
        speedGate.complete(Unit)
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        compose.onNodeWithText("Speed test completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recording…").assertDoesNotExist()
        assertEquals(1, dao.rows.value.size)
    }

    @Test fun partialSpeedTestShowsUnavailableDownloadAndMeasuredUpload() {
        val engine = object : SpeedTestEngine {
            override fun runSpeedTest(connectionAgeMillis: Long?, connectionType: ConnectionType) =
                flowOf(SpeedTestProgress(phase = SpeedTestPhase.FAILED, uploadMbps = 12.5, overallProgress = 1f, downloadRateLimited = true))
        }
        start(speedTest = engine)
        compose.runOnIdle { model.setIncludeSpeedTest(true) }
        compose.onNodeWithContentDescription("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        compose.onNodeWithText("Speed test partially completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download: Unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Upload: 12.5 Mbps").assertIsDisplayed()
        compose.onNodeWithText("The test server limited download requests. Try again later for a full download test.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show saved reading").performScrollTo().performClick()
        compose.onNodeWithText("Download speed unavailable").performScrollTo().assertIsDisplayed()
    }

    @Test fun descriptionCanBeAddedAfterCaptureEditedAndClearedInDetails() {
        start()
        compose.onNodeWithContentDescription("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Outside CICS")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000) { dao.rows.value.single().locationDescription == "Outside CICS" }
        compose.onNodeWithText("Show saved reading").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("x".repeat(201))
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000) { dao.rows.value.single().locationDescription.length == 200 }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextClearance()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000) { dao.rows.value.single().locationDescription.isEmpty() }
    }
    @Test fun failedDescriptionSaveKeepsDraftForRetry() {
        start(listOf(sampleReading()))
        compose.onNodeWithTag("reading-test-wifi").performClick()
        compose.runOnIdle { dao.failDescription = true }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Near the stairs")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Couldn't save description. Your text is still here.").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals("", dao.rows.value.single().locationDescription); dao.failDescription = false }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.waitUntil(5000) { dao.rows.value.single().locationDescription == "Near the stairs" }
        compose.onNodeWithContentDescription("Close reading details").performClick()
        compose.onNodeWithText("Near the stairs").assertIsDisplayed()
    }
    @Test fun holdingNavigationShowsLabelWithoutSwitchingScreens() {
        start()
        compose.onNodeWithContentDescription("Map").performTouchInput { longClick() }
        compose.onNodeWithText("Map").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Destination.READINGS, model.uiState.value.destination) }
    }
    @Test fun impreciseCaptureSavesCoordinatesAndExplainsApproximation() {
        location = LocationResult.Candidate(LocationFix(13.78421, 121.07428, 200f, 100_000))
        start()
        compose.onNodeWithContentDescription("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVED }
        compose.onNodeWithText("Saved with a map pin").assertIsDisplayed()
        compose.onNodeWithText("Show saved reading").performClick()
        compose.onNodeWithText("Approximate location").assertExists()
        compose.onNodeWithText("Show on map").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(13.78421, dao.rows.value.single().latitude!!, 0.0)
            assertEquals(NoLocationReason.LOCATION_INACCURATE, dao.rows.value.single().noLocationReason)
        }
    }
    @Test fun manualLocationCanBeCancelledOrSavedWithoutChangingSignal() {
        val original = mobileReading()
        start(listOf(original), baseStyle = com.sagapmo.app.map.CampusMapScreenTest.localStyle)
        compose.onNodeWithTag("reading-${original.id}").performClick()
        compose.onNodeWithText("Set location").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(original, dao.rows.value.single()) }
        compose.onNodeWithText("Set location").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Save location") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save location").performClick()
        compose.waitUntil(5000) { dao.rows.value.single().locationManuallySet }
        compose.onNodeWithTag("toggle-advanced-info").performScrollTo().performClick()
        compose.onNodeWithText("Set manually").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val saved = dao.rows.value.single()
            assertEquals(original.rawSignalValue, saved.rawSignalValue)
            assertEquals(original.capturedAtEpochMillis, saved.capturedAtEpochMillis)
            assertEquals(original.id, saved.id)
            assertNotNull(saved.latitude)
            assertNull(saved.accuracyMetres)
        }
    }
    @Test fun shortSlowSwipeAndFastFlickDoNotOfferDeletion() {
        start(listOf(sampleReading()))
        for (duration in listOf(500L, 80L)) {
            compose.onNodeWithTag("reading-test-wifi").performTouchInput {
                swipe(centerRight, centerRight - androidx.compose.ui.geometry.Offset(width * 0.25f, 0f), durationMillis = duration)
            }
            compose.onNodeWithText("Delete reading?").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, dao.rows.value.size) }
        }
    }
    @Test fun swipeLeftDeletesOneReadingAndUndoRestoresIt() {
        val original = mobileReading()
        start(listOf(sampleReading(), original))
        compose.onNodeWithTag("reading-${original.id}").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(2, dao.rows.value.size) }
        compose.onNodeWithTag("reading-${original.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete reading?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, dao.rows.value.size) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(2, dao.rows.value.size) }
        compose.onNodeWithTag("reading-${original.id}").performTouchInput { swipeLeft() }
        compose.onNode(hasText("Delete", substring = false) and hasClickAction()).performClick()
        compose.waitUntil(5000) { dao.rows.value.size == 1 }
        compose.onNodeWithTag("reading-${original.id}").assertDoesNotExist()
        compose.onNodeWithTag("reading-test-wifi").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { dao.rows.value.size == 2 }
        compose.runOnIdle { assertEquals(original, dao.rows.value.single { it.id == original.id }) }
    }
    @Test fun failedDeletionKeepsReadingAndExplainsFailure() {
        dao.failDelete = true
        start(listOf(sampleReading()))
        compose.onNodeWithTag("reading-test-wifi").performTouchInput { swipeLeft() }
        compose.onNode(hasText("Delete", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithText("Couldn't delete reading. Try again.").assertIsDisplayed()
        compose.onNodeWithTag("reading-test-wifi").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, dao.rows.value.size) }
    }
    @Test fun readingsFiltersFitNarrowWidthsAndEnlargedText() {
        val config = mutableStateOf(320 to 1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, config.value.second)) {
                SagapMoTheme {
                    Box(Modifier.width(config.value.first.dp).testTag("filter-container")) { Filters(AppUiState(), {}) }
                }
            }
        }
        for (width in listOf(280, 320)) for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { config.value = width to scale }
            val container = compose.onNodeWithTag("filter-container").fetchSemanticsNode().boundsInRoot
            for (type in listOf("ALL", "WIFI", "MOBILE")) {
                val bounds = compose.onNodeWithTag("filter-$type").fetchSemanticsNode().boundsInRoot
                assertTrue("Filter fits $width dp at font scale $scale", bounds.left >= container.left && bounds.right <= container.right)
                compose.onNode(hasText("0") and hasAnyAncestor(hasTestTag("filter-$type")), useUnmergedTree = true).assertIsDisplayed()
            }
            compose.onNodeWithText("Mobile").assertIsDisplayed()
        }
    }
    @Test fun attributionStartsClosedAndCanBeToggled() {
        compose.setContent { SagapMoTheme { com.sagapmo.app.map.MapAttribution(listOf("Map credit")) } }
        compose.onNodeWithText("Map credit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Map attribution").performClick()
        compose.onNodeWithText("Map credit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Map attribution").performClick()
        compose.onNodeWithText("Map credit").assertDoesNotExist()
    }
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)
    private val dao = UiDao()
    private var sample = SignalSample(ConnectionType.MOBILE, 3, SignalUnit.ANDROID_LEVEL, Rating.GOOD, 1000, 1_794_883_320_000, 100_000)
    private var location: LocationResult = LocationResult.Missing(NoLocationReason.APPROXIMATE_ONLY)
    private val signal = object : SignalReader {
        override fun currentConnection() = ConnectionResult.Available(ConnectionSnapshot(1, sample.connectionType, 1))
        override suspend fun read() = SignalResult.Available(sample)
    }
    private lateinit var model: SagapMoViewModel
    private var settingsOpened = false
    @Test fun campusLandmarkNamesRemainAccessible() {
        start(listOf(sampleReading()), baseStyle = com.sagapmo.app.map.CampusMapScreenTest.localStyle)
        compose.runOnIdle { model.setDestination(Destination.MAP) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("GYMNASIUM", substring = true).fetchSemanticsNodes().isNotEmpty() }
        for (landmark in com.sagapmo.app.map.campusLandmarks)
            compose.onNodeWithContentDescription(landmark.name.replace('\n', ' '), substring = true).assertExists()
        compose.runOnIdle { model.showOnMap(sampleReading()) }
        compose.onNodeWithContentDescription("GYMNASIUM", substring = true).assertExists()
    }
    @Test fun circularMapFiltersSitAboveFocusAndShareSelectionWithReadings() {
        start(listOf(sampleReading(), mobileReading()))
        compose.runOnIdle { model.setDestination(Destination.MAP) }
        val focus = compose.onNodeWithContentDescription("Return to campus overview").fetchSemanticsNode().boundsInRoot
        var previousBottom = 0f
        for (type in listOf("ALL", "WIFI", "MOBILE")) {
            val filter = compose.onNodeWithTag("filter-$type")
            val bounds = filter.fetchSemanticsNode().boundsInRoot
            assertTrue("Filter stack stays above focus", bounds.bottom < focus.top)
            assertTrue("Filters do not overlap", bounds.top >= previousBottom)
            assertEquals(bounds.width, bounds.height, 0.1f)
            previousBottom = bounds.bottom
            filter.performClick().assertIsSelected()
        }
        compose.runOnIdle { assertEquals(ConnectionType.MOBILE, model.uiState.value.filter) }
        compose.onNodeWithContentDescription("Readings").performClick()
        compose.onNodeWithTag("filter-MOBILE").assertIsSelected()
        compose.onNodeWithContentDescription("Map").performClick()
        compose.onNodeWithContentDescription("All connections").performClick().assertIsSelected()
        compose.runOnIdle { assertNull(model.uiState.value.filter) }
    }
    @Test fun mapExtendsBehindFloatingNavigation() {
        start()
        compose.runOnIdle { model.setDestination(Destination.MAP) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("map-surface").fetchSemanticsNodes().isNotEmpty() }
        val map = compose.onNodeWithTag("map-surface").fetchSemanticsNode().boundsInRoot
        val record = compose.onNodeWithContentDescription("Record reading").fetchSemanticsNode().boundsInRoot
        val attribution = compose.onNodeWithContentDescription("Map attribution").fetchSemanticsNode().boundsInRoot
        val focus = compose.onNodeWithContentDescription("Return to campus overview").fetchSemanticsNode().boundsInRoot
        assertTrue("Attribution is above the bottom-right focus control", attribution.bottom < focus.top)
        assertTrue("Focus stays above navigation", focus.bottom < record.top)
        assertTrue("Map continues below the floating action", map.bottom > record.bottom)
        assertTrue("Attribution stays above navigation", attribution.bottom < record.top)
    }
    private fun start(
        rows: List<Reading> = emptyList(),
        baseStyle: BaseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        speedTest: SpeedTestEngine = HttpSpeedTestEngine(),
        locationGate: CompletableDeferred<Unit>? = null,
    ) {
        dao.rows.value = rows
        compose.runOnUiThread {
            model = SagapMoViewModel(signal, object : LocationReader {
                override fun currentAccess() = LocationAccess(true, true, true)
                override suspend fun locate(): LocationResult {
                    locationGate?.await()
                    return location
                }
            }, dao, speedTest)
            model.setDestination(Destination.READINGS)
        }
        compose.setContent { SagapMoTheme { SagapMoApp(model, onRecord = { model.record(false) }, onOpenSettings = { settingsOpened = true }, baseStyle = baseStyle) } }
    }
    @Test fun floatingControlsExposeSelectionSettingsAndBusyState() {
        dao.gate = CompletableDeferred(); start()
        compose.runOnIdle { model.setIncludeSpeedTest(false) }
        compose.onNodeWithText("On this phone").assertDoesNotExist()
        compose.onNodeWithContentDescription("Readings").assertIsSelected()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-dialog").assertIsDisplayed()
        compose.runOnIdle { assertFalse(settingsOpened) }
        compose.onNodeWithText("Open Android App Settings").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("settings-dialog").assertDoesNotExist()
        compose.onNodeWithContentDescription("Record reading").performClick()
        compose.waitUntil(5000) { model.uiState.value.capturePhase == CapturePhase.SAVING }
        compose.onNodeWithContentDescription("Recording…").assertIsNotEnabled()
        compose.runOnIdle { dao.gate!!.complete(Unit) }
    }
    @Test fun emptyDatabaseExplainsFirstReading() {
        start(); compose.onNodeWithText("No readings yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Record reading").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Record reading").performClick()
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
        compose.onNodeWithTag("toggle-advanced-info").performScrollTo().performClick()
        compose.onNodeWithText("13.78422, 121.07431").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Signal age at capture").assertDoesNotExist()
        compose.onNodeWithText("Wi-Fi measurement age unavailable. Android does not provide a timestamp for this RSSI value.").assertDoesNotExist()
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
        compose.onNodeWithContentDescription("Record reading").performClick()
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
    var failDescription = false
    var failDelete = false
    val rows = MutableStateFlow<List<Reading>>(emptyList())
    var failRead = false
    var failSave = false
    var gate: CompletableDeferred<Unit>? = null
    override fun observeReadings(type: ConnectionType?): Flow<List<Reading>> = flow {
        if (failRead) error("read error")
        emitAll(rows.map { it.sortedWith(compareByDescending<Reading> { r -> r.capturedAtEpochMillis }.thenByDescending { r -> r.id }) })
    }
    override suspend fun deleteById(id: String) { if (failDelete) error("disk error"); rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun setManualLocation(id: String, latitude: Double, longitude: Double): Int {
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(latitude = latitude, longitude = longitude,
            accuracyMetres = null, locationAgeMillisAtCapture = null, noLocationReason = null, locationManuallySet = true) else it }
        return 1
    }
    override suspend fun setLocationDescription(id: String, description: String): Int {
        if (failDescription) error("disk error")
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(locationDescription = description) else it }
        return 1
    }
    override suspend fun getById(id: String) = rows.value.find { it.id == id }
    override suspend fun insertIfAbsent(reading: Reading): Long {
        gate?.await(); if (failSave) error("disk full")
        if (getById(reading.id) != null) return -1
        rows.value += reading; return 1
    }
}
