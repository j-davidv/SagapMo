package com.sagapmo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagapmo.app.capture.*
import com.sagapmo.app.reading.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import com.sagapmo.app.speedtest.*

enum class CapturePhase { IDLE, AWAITING_PERMISSION, LOCATING, ACQUIRING_SIGNAL, RUNNING_SPEED_TEST, SAVING, SAVE_FAILED, SAVED, FAILED, INTERRUPTED }
enum class Destination { MAP, READINGS }
sealed interface ReadingsState {
    data object Loading : ReadingsState
    data class Loaded(val readings: List<Reading>) : ReadingsState
    data object Failed : ReadingsState
}
data class AppUiState(
    val capturePhase: CapturePhase = CapturePhase.IDLE,
    val captureError: CaptureError? = null,
    val pendingSave: Reading? = null,
    val savedReading: Reading? = null,
    val filter: ConnectionType? = null,
    val destination: Destination = Destination.MAP,
    val selectedReading: Reading? = null,
    val readingsState: ReadingsState = ReadingsState.Loading,
    val focusedReadingId: String? = null,
    val captureVisible: Boolean = false,
    val permissionLaunched: Boolean = false,
    val descriptionSavingId: String? = null,
    val descriptionErrorId: String? = null,
    val includeSpeedTest: Boolean = true,
    val speedTestProgress: SpeedTestProgress? = null,
    val use3dMap: Boolean = false,
    val settingsVisible: Boolean = false,
) {
    val allReadings get() = (readingsState as? ReadingsState.Loaded)?.readings.orEmpty()
    val visibleReadings get() = allReadings.filter { filter == null || it.connectionType == filter }
    val recording get() = capturePhase in setOf(CapturePhase.AWAITING_PERMISSION, CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL, CapturePhase.RUNNING_SPEED_TEST, CapturePhase.SAVING)
}

class SagapMoViewModel(
    private val signalReader: SignalReader,
    private val locationReader: LocationReader,
    private val dao: ReadingDao,
    private val speedTestEngine: SpeedTestEngine = HttpSpeedTestEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialUse3dMap: Boolean = false,
    initialIncludeSpeedTest: Boolean = true,
    private val onSavePreference: ((key: String, value: Boolean) -> Unit)? = null,
) : ViewModel() {
    private val state = MutableStateFlow(
        AppUiState(
            use3dMap = initialUse3dMap,
            includeSpeedTest = initialIncludeSpeedTest,
        )
    )
    val uiState = state.asStateFlow()
    private var operationId: String? = null
    private var operationJob: Job? = null
    private var readingsJob: Job? = null
    private val descriptionJobs = mutableMapOf<String, Job>()

    init { retryReadings() }

    fun record(needsPermissionPrompt: Boolean, includeSpeedTest: Boolean = state.value.includeSpeedTest) {
        if (state.value.recording || state.value.pendingSave != null) return
        operationId = UUID.randomUUID().toString()
        state.update { it.copy(capturePhase = CapturePhase.ACQUIRING_SIGNAL, captureError = null,
            savedReading = null, captureVisible = true, permissionLaunched = false, selectedReading = null,
            includeSpeedTest = includeSpeedTest, speedTestProgress = null) }
        operationJob = viewModelScope.launch {
            when (val connection = withContext(ioDispatcher) { signalReader.currentConnection() }) {
                is ConnectionResult.Failed -> fail(connection.error)
                is ConnectionResult.Available -> if (needsPermissionPrompt) {
                    state.update { it.copy(capturePhase = CapturePhase.AWAITING_PERMISSION) }
                } else acquire()
            }
        }
    }

    fun setUse3dMap(enabled: Boolean) {
        state.update { it.copy(use3dMap = enabled) }
        onSavePreference?.invoke("use_3d_map", enabled)
    }

    fun setIncludeSpeedTest(include: Boolean) {
        state.update { it.copy(includeSpeedTest = include) }
        onSavePreference?.invoke("include_speed_test", include)
    }

    fun openSettings() {
        state.update { it.copy(settingsVisible = true) }
    }

    fun dismissSettings() {
        state.update { it.copy(settingsVisible = false) }
    }

    fun claimPermissionLaunch(): Boolean {
        if (state.value.capturePhase != CapturePhase.AWAITING_PERMISSION || state.value.permissionLaunched) return false
        state.update { it.copy(permissionLaunched = true) }
        return true
    }

    fun onPermissionResult() {
        if (state.value.capturePhase != CapturePhase.AWAITING_PERMISSION) return
        state.update { it.copy(capturePhase = CapturePhase.LOCATING, permissionLaunched = false) }
        operationJob = viewModelScope.launch { acquire() }
    }

    private suspend fun acquire() {
        state.update { it.copy(capturePhase = CapturePhase.LOCATING) }
        val location = locationReader.locate()
        state.update { it.copy(capturePhase = CapturePhase.ACQUIRING_SIGNAL) }
        when (val signal = signalReader.read()) {
            is SignalResult.Failed -> fail(signal.error)
            is SignalResult.Available -> {
                val access = withContext(ioDispatcher) { locationReader.currentAccess() }
                var candidate = assembleReading(requireNotNull(operationId), signal.sample, location, access)
                    .copy(wifiConnectedAgeMillis = signal.sample.connectionAgeMillis)
                if (state.value.includeSpeedTest) {
                    state.update { it.copy(capturePhase = CapturePhase.RUNNING_SPEED_TEST) }
                    var lastProgress: SpeedTestProgress? = null
                    try {
                        speedTestEngine.runSpeedTest(signal.sample.connectionAgeMillis).collect { progress ->
                            lastProgress = progress
                            state.update { it.copy(speedTestProgress = progress) }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                    val finalProg = lastProgress
                    if (finalProg != null) {
                        candidate = candidate.copy(
                            downloadMbps = finalProg.downloadMbps,
                            uploadMbps = finalProg.uploadMbps,
                            latencyMs = finalProg.latencyMs,
                            jitterMs = finalProg.jitterMs,
                            speedTestStatus = if (finalProg.phase == SpeedTestPhase.FAILED) SpeedTestStatus.FAILED else SpeedTestStatus.COMPLETED
                        )
                    } else {
                        candidate = candidate.copy(speedTestStatus = SpeedTestStatus.SKIPPED)
                    }
                } else {
                    candidate = candidate.copy(speedTestStatus = SpeedTestStatus.SKIPPED)
                }
                state.update { it.copy(pendingSave = candidate, capturePhase = CapturePhase.SAVING) }
                saveCandidate(candidate)
            }
        }
    }

    private fun fail(error: CaptureError) {
        state.update { it.copy(capturePhase = CapturePhase.FAILED, captureError = error, captureVisible = true) }
    }

    private suspend fun saveCandidate(candidate: Reading) {
        try {
            val saved = dao.save(candidate)
            state.update { it.copy(capturePhase = CapturePhase.SAVED, savedReading = saved, pendingSave = null, captureVisible = true) }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            state.update { it.copy(capturePhase = CapturePhase.SAVE_FAILED, captureVisible = true) }
        }
    }

    fun retrySave() {
        if (state.value.capturePhase != CapturePhase.SAVE_FAILED) return
        val candidate = state.value.pendingSave ?: return
        state.update { it.copy(capturePhase = CapturePhase.SAVING, captureVisible = true) }
        operationJob = viewModelScope.launch { saveCandidate(candidate) }
    }

    fun retryReadings() {
        readingsJob?.cancel()
        state.update { it.copy(readingsState = ReadingsState.Loading) }
        readingsJob = viewModelScope.launch {
            dao.observeReadings(null)
                .catch { error ->
                    if (error is CancellationException) throw error
                    state.update { it.copy(readingsState = ReadingsState.Failed) }
                }
                .collect { rows -> state.update { it.copy(readingsState = ReadingsState.Loaded(rows)) } }
        }
    }

    fun onAppBackgrounded() {
        if (state.value.capturePhase in setOf(CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL, CapturePhase.RUNNING_SPEED_TEST)) {
            operationJob?.cancel()
            state.update { it.copy(capturePhase = CapturePhase.INTERRUPTED, captureVisible = true) }
        }
    }
    fun dismissCapture() { state.update { it.copy(captureVisible = false) } }
    fun cancelCapture() {
        if (state.value.capturePhase in setOf(CapturePhase.AWAITING_PERMISSION, CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL, CapturePhase.RUNNING_SPEED_TEST)) {
            operationJob?.cancel()
            state.update { it.copy(capturePhase = CapturePhase.INTERRUPTED, captureVisible = false, permissionLaunched = false) }
        }
    }
    fun setFilter(type: ConnectionType?) { state.update { it.copy(filter = type) } }
    suspend fun deleteReading(reading: Reading): Boolean = try {
        withContext(ioDispatcher) { dao.deleteById(reading.id) }
        state.update { it.copy(
            selectedReading = it.selectedReading?.takeUnless { row -> row.id == reading.id },
            focusedReadingId = it.focusedReadingId?.takeUnless { id -> id == reading.id },
            savedReading = it.savedReading?.takeUnless { row -> row.id == reading.id }) }
        true
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { false }

    suspend fun restoreReading(reading: Reading): Boolean = try {
        withContext(ioDispatcher) { dao.save(reading) }
        true
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { false }
    fun setDestination(destination: Destination) { state.update { it.copy(destination = destination) } }
    fun openReading(reading: Reading) {
        state.update { it.copy(selectedReading = reading, captureVisible = false,
            filter = it.filter?.takeIf { type -> type == reading.connectionType }) }
    }
    fun closeDetails() { state.update { it.copy(selectedReading = null) } }
    fun updateLocationDescription(id: String, description: String) {
        descriptionJobs[id]?.cancel()
        state.update { it.copy(descriptionSavingId = id, descriptionErrorId = null) }
        descriptionJobs[id] = viewModelScope.launch {
            delay(400)
            val saved = setLocationDescription(id, description)
            state.update { current -> current.copy(
                descriptionSavingId = current.descriptionSavingId?.takeUnless { it == id },
                descriptionErrorId = if (saved) current.descriptionErrorId else id) }
            descriptionJobs.remove(id)
        }
    }
    suspend fun setLocationDescription(id: String, description: String): Boolean = try {
        require(description.length <= 200)
        val text = description.trim()
        check(withContext(ioDispatcher) { dao.setLocationDescription(id, text) } == 1)
        state.update { current -> current.copy(
            selectedReading = current.selectedReading?.let { if (it.id == id) it.copy(locationDescription = text) else it },
            savedReading = current.savedReading?.let { if (it.id == id) it.copy(locationDescription = text) else it }) }
        true
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { false }
    suspend fun setManualLocation(reading: Reading, latitude: Double, longitude: Double): Boolean = try {
        val updated = reading.copy(latitude = latitude, longitude = longitude, accuracyMetres = null,
            locationAgeMillisAtCapture = null, noLocationReason = null, locationManuallySet = true)
        validateReading(updated)
        check(withContext(ioDispatcher) { dao.setManualLocation(reading.id, latitude, longitude) } == 1)
        state.update { it.copy(selectedReading = it.selectedReading?.let { row -> if (row.id == reading.id) updated else row },
            savedReading = it.savedReading?.let { row -> if (row.id == reading.id) updated else row }) }
        true
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { false }
    fun showOnMap(reading: Reading) {
        if (reading.latitude == null || reading.longitude == null) return
        state.update { it.copy(destination = Destination.MAP, selectedReading = null, focusedReadingId = reading.id,
            filter = it.filter?.takeIf { type -> type == reading.connectionType }) }
    }
}
