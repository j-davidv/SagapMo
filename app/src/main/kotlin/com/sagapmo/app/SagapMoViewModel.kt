package com.sagapmo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagapmo.app.capture.*
import com.sagapmo.app.reading.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class CapturePhase { IDLE, AWAITING_PERMISSION, LOCATING, ACQUIRING_SIGNAL, SAVING, SAVE_FAILED, SAVED, FAILED, INTERRUPTED }
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
) {
    val allReadings get() = (readingsState as? ReadingsState.Loaded)?.readings.orEmpty()
    val visibleReadings get() = allReadings.filter { filter == null || it.connectionType == filter }
    val recording get() = capturePhase in setOf(CapturePhase.AWAITING_PERMISSION, CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL, CapturePhase.SAVING)
}

class SagapMoViewModel(
    private val signalReader: SignalReader,
    private val locationReader: LocationReader,
    private val dao: ReadingDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val state = MutableStateFlow(AppUiState())
    val uiState = state.asStateFlow()
    private var operationId: String? = null
    private var operationJob: Job? = null
    private var readingsJob: Job? = null

    init { retryReadings() }

    fun record(needsPermissionPrompt: Boolean) {
        if (state.value.recording || state.value.pendingSave != null) return
        operationId = UUID.randomUUID().toString()
        state.update { it.copy(capturePhase = CapturePhase.ACQUIRING_SIGNAL, captureError = null,
            savedReading = null, captureVisible = true, permissionLaunched = false, selectedReading = null) }
        operationJob = viewModelScope.launch {
            when (val connection = withContext(ioDispatcher) { signalReader.currentConnection() }) {
                is ConnectionResult.Failed -> fail(connection.error)
                is ConnectionResult.Available -> if (needsPermissionPrompt) {
                    state.update { it.copy(capturePhase = CapturePhase.AWAITING_PERMISSION) }
                } else acquire()
            }
        }
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
                val candidate = assembleReading(requireNotNull(operationId), signal.sample, location, access)
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
        if (state.value.capturePhase in setOf(CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL)) {
            operationJob?.cancel()
            state.update { it.copy(capturePhase = CapturePhase.INTERRUPTED, captureVisible = true) }
        }
    }
    fun dismissCapture() { state.update { it.copy(captureVisible = false) } }
    fun setFilter(type: ConnectionType?) { state.update { it.copy(filter = type) } }
    fun setDestination(destination: Destination) { state.update { it.copy(destination = destination) } }
    fun openReading(reading: Reading) {
        state.update { it.copy(selectedReading = reading, captureVisible = false,
            filter = it.filter?.takeIf { type -> type == reading.connectionType }) }
    }
    fun closeDetails() { state.update { it.copy(selectedReading = null) } }
    fun showOnMap(reading: Reading) {
        if (reading.latitude == null || reading.longitude == null) return
        state.update { it.copy(destination = Destination.MAP, selectedReading = null, focusedReadingId = reading.id,
            filter = it.filter?.takeIf { type -> type == reading.connectionType }) }
    }
}
