package com.sagapmo.app

import com.sagapmo.app.capture.*
import com.sagapmo.app.reading.*
import com.sagapmo.app.speedtest.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SagapMoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val signal = FakeSignal()
    private val location = FakeLocation()
    private val dao = FakeDao()
    private val speedTest = FakeSpeedTestEngine()
    private lateinit var model: SagapMoViewModel
    @Before fun setUp() { Dispatchers.setMain(dispatcher); model = SagapMoViewModel(signal, location, dao, speedTest, dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun descriptionAutoSaveKeepsLatestTextAfterDetailsClose() = runTest(dispatcher) {
        model.record(false); advanceUntilIdle()
        val id = dao.rows.value.single().id
        model.updateLocationDescription(id, "First draft")
        advanceTimeBy(200)
        model.updateLocationDescription(id, "Latest text")
        model.closeDetails()
        advanceUntilIdle()
        assertEquals("Latest text", dao.rows.value.single().locationDescription)
        assertNull(model.uiState.value.descriptionSavingId)
        assertNull(model.uiState.value.descriptionErrorId)
    }
    @Test fun descriptionsAreTrimmedBoundedAndCannotCreateMissingReadings() = runTest(dispatcher) {
        model.record(false); advanceUntilIdle()
        val original = dao.rows.value.single()
        assertTrue(model.setLocationDescription(original.id, "  Outside CICS  "))
        assertEquals(original.copy(locationDescription = "Outside CICS"), dao.rows.value.single())
        assertFalse(model.setLocationDescription(original.id, "x".repeat(201)))
        assertFalse(model.setLocationDescription("missing", "Nowhere"))
        assertEquals(1, dao.rows.value.size)
        assertTrue(model.setLocationDescription(original.id, ""))
        assertEquals(original, dao.rows.value.single())
    }
    @Test fun doubleTapCreatesOneOperation() = runTest(dispatcher) {
        model.record(false); model.record(false); advanceUntilIdle()
        assertEquals(1, dao.rows.value.size); assertEquals(CapturePhase.SAVED, model.uiState.value.capturePhase)
    }
    @Test fun permissionContinuationRunsOnce() = runTest(dispatcher) {
        model.record(true); advanceUntilIdle()
        assertEquals(CapturePhase.AWAITING_PERMISSION, model.uiState.value.capturePhase)
        assertTrue(model.claimPermissionLaunch()); assertFalse(model.claimPermissionLaunch())
        model.onPermissionResult(); model.onPermissionResult(); advanceUntilIdle()
        assertEquals(1, dao.rows.value.size)
    }
    @Test fun backgroundDuringPermissionDoesNotCancelDialog() = runTest(dispatcher) {
        model.record(true); advanceUntilIdle(); model.onAppBackgrounded()
        assertEquals(CapturePhase.AWAITING_PERMISSION, model.uiState.value.capturePhase)
        model.onPermissionResult(); advanceUntilIdle(); assertEquals(1, dao.rows.value.size)
    }
    @Test fun backgroundDuringLocationCancelsAndRemovesRequest() = runTest(dispatcher) {
        location.gate = CompletableDeferred()
        model.record(false); runCurrent(); assertEquals(CapturePhase.LOCATING, model.uiState.value.capturePhase)
        model.onAppBackgrounded(); advanceUntilIdle()
        assertTrue(location.cancelled); assertTrue(dao.rows.value.isEmpty())
        assertEquals(CapturePhase.INTERRUPTED, model.uiState.value.capturePhase)
    }
    @Test fun backgroundDuringSaveDoesNotStartAnotherInsert() = runTest(dispatcher) {
        dao.gate = CompletableDeferred()
        model.record(false); runCurrent(); assertEquals(CapturePhase.SAVING, model.uiState.value.capturePhase)
        model.onAppBackgrounded(); model.record(false); model.setDestination(Destination.READINGS)
        dao.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, dao.rows.value.size); assertEquals(1, dao.saves)
    }
    @Test fun storageFailureRetainsIdAndCandidate() = runTest(dispatcher) {
        dao.failSave = true; model.record(false); advanceUntilIdle()
        val candidate = requireNotNull(model.uiState.value.pendingSave)
        model.record(false); advanceUntilIdle()
        assertEquals(candidate, model.uiState.value.pendingSave); assertEquals(1, signal.readCalls)
        assertEquals(CapturePhase.SAVE_FAILED, model.uiState.value.capturePhase)
    }
    @Test fun retrySaveDoesNotRecapture() = runTest(dispatcher) {
        dao.failSave = true; model.record(false); advanceUntilIdle()
        val candidate = requireNotNull(model.uiState.value.pendingSave)
        dao.failSave = false; model.retrySave(); model.retrySave(); advanceUntilIdle()
        assertEquals(candidate, dao.rows.value.single()); assertEquals(1, signal.readCalls)
        assertEquals(CapturePhase.SAVED, model.uiState.value.capturePhase)
    }
    @Test fun signalFailureDoesNotInsert() = runTest(dispatcher) {
        signal.result = SignalResult.Failed(CaptureError.CONNECTION_CHANGED)
        model.record(false); advanceUntilIdle()
        assertTrue(dao.rows.value.isEmpty()); assertEquals(CaptureError.CONNECTION_CHANGED, model.uiState.value.captureError)
    }
    @Test fun eachMissingLocationReasonStillSavesValidSignal() = runTest(dispatcher) {
        for (reason in NoLocationReason.entries) {
            location.result = LocationResult.Missing(reason); model.record(false); advanceUntilIdle()
            assertEquals(reason, model.uiState.value.savedReading!!.noLocationReason)
        }
        assertEquals(7, dao.rows.value.size)
    }
    @Test fun readFailureCanBeRetried() = runTest(dispatcher) {
        dao.failRead = true; model.retryReadings(); advanceUntilIdle()
        assertEquals(ReadingsState.Failed, model.uiState.value.readingsState)
        dao.failRead = false; model.retryReadings(); advanceUntilIdle()
        assertTrue(model.uiState.value.readingsState is ReadingsState.Loaded)
    }
    @Test fun hiddenSavedReadingAndMapFocusAdjustFilter() = runTest(dispatcher) {
        model.setFilter(ConnectionType.WIFI)
        signal.result = SignalResult.Available(SignalSample(ConnectionType.MOBILE, 3, SignalUnit.ANDROID_LEVEL, Rating.GOOD, 1000, 5000, 100_000))
        model.record(false); advanceUntilIdle()
        val reading = requireNotNull(model.uiState.value.savedReading)
        assertEquals(ConnectionType.WIFI, model.uiState.value.filter)
        model.openReading(reading); advanceUntilIdle()
        assertNull(model.uiState.value.filter); assertEquals(reading, model.uiState.value.selectedReading)
        val located = reading.copy(latitude = 13.78421, longitude = 121.07428, accuracyMetres = 8f, locationAgeMillisAtCapture = 1000, noLocationReason = null)
        model.showOnMap(located)
        assertEquals(Destination.MAP, model.uiState.value.destination); assertEquals(reading.id, model.uiState.value.focusedReadingId)
        assertNull(model.uiState.value.selectedReading)
    }
    @Test fun recordWithSpeedTestCapturesAndSavesMetrics() = runTest(dispatcher) {
        model.setIncludeSpeedTest(true)
        model.record(false); advanceUntilIdle()
        val saved = requireNotNull(model.uiState.value.savedReading)
        assertEquals(50.0, saved.downloadMbps!!, 0.01)
        assertEquals(20.0, saved.uploadMbps!!, 0.01)
        assertEquals(15.0, saved.latencyMs!!, 0.01)
        assertEquals(2.0, saved.jitterMs!!, 0.01)
        assertEquals(SpeedTestStatus.COMPLETED, saved.speedTestStatus)
    }

    @Test fun recordWithSpeedTestDisabledSkipsSpeedTest() = runTest(dispatcher) {
        model.setIncludeSpeedTest(false)
        model.record(false); advanceUntilIdle()
        val saved = requireNotNull(model.uiState.value.savedReading)
        assertNull(saved.downloadMbps)
        assertNull(saved.uploadMbps)
        assertNull(saved.latencyMs)
        assertNull(saved.jitterMs)
        assertEquals(SpeedTestStatus.SKIPPED, saved.speedTestStatus)
    }
}

private class FakeSpeedTestEngine : SpeedTestEngine {
    var resultProgress = SpeedTestProgress(
        phase = SpeedTestPhase.COMPLETED,
        downloadMbps = 50.0,
        uploadMbps = 20.0,
        latencyMs = 15.0,
        jitterMs = 2.0,
        overallProgress = 1.0f
    )
    override fun runSpeedTest(connectionAgeMillis: Long?): Flow<SpeedTestProgress> = flow {
        emit(SpeedTestProgress(phase = SpeedTestPhase.LATENCY, latencyMs = 15.0, jitterMs = 2.0, connectionAgeMillisAtStart = connectionAgeMillis, overallProgress = 0.2f))
        emit(resultProgress.copy(connectionAgeMillisAtStart = connectionAgeMillis))
    }
}

private class FakeSignal : SignalReader {
    var connection: ConnectionResult = ConnectionResult.Available(ConnectionSnapshot(1, ConnectionType.WIFI, null))
    var result: SignalResult = SignalResult.Available(SignalSample(ConnectionType.WIFI, -62, SignalUnit.DBM, Rating.GOOD, null, 5000, 100_000))
    var readCalls = 0
    override fun currentConnection() = connection
    override suspend fun read(): SignalResult { readCalls++; return result }
}
private class FakeLocation : LocationReader {
    var gate: CompletableDeferred<Unit>? = null
    var cancelled = false
    var result: LocationResult = LocationResult.Missing(NoLocationReason.PERMISSION_DENIED)
    override fun currentAccess() = LocationAccess(true, true, true)
    override suspend fun locate(): LocationResult = try { gate?.await(); result }
        catch (error: CancellationException) { cancelled = true; throw error }
}
private class FakeDao : ReadingDao() {
    val rows = MutableStateFlow<List<Reading>>(emptyList())
    var failSave = false
    var failRead = false
    var saves = 0
    var gate: CompletableDeferred<Unit>? = null
    override fun observeReadings(type: ConnectionType?): Flow<List<Reading>> = flow {
        if (failRead) error("disk read failed")
        emitAll(rows.map { rows -> rows.filter { type == null || it.connectionType == type } })
    }
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
        rows.value += reading; return rows.value.size.toLong()
    }
    override suspend fun save(reading: Reading): Reading {
        saves++; gate?.await(); if (failSave) error("disk full")
        return super.save(reading)
    }
}
