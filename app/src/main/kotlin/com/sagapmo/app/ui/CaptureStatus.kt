package com.sagapmo.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sagapmo.app.*
import com.sagapmo.app.capture.CaptureError
import com.sagapmo.app.reading.*

fun CaptureError.explanation() = when (this) {
    CaptureError.NO_CONNECTION -> "No supported connection. Connect to Wi-Fi or Mobile in Android settings, then try again."
    CaptureError.UNSUPPORTED_CONNECTION -> "This connection is not supported. Use Wi-Fi or Mobile as the active default connection."
    CaptureError.VPN_ACTIVE -> "Disconnect the VPN to record. The pilot needs to identify Wi-Fi or Mobile reliably."
    CaptureError.CONNECTION_CHANGED -> "Connection changed. Try again."
    CaptureError.SIGNAL_UNAVAILABLE -> "Signal unavailable. Android did not provide a usable signal value. Try again."
    CaptureError.SIGNAL_INVALID -> "Signal value unavailable. Android returned an invalid value. Try again."
    CaptureError.SIGNAL_STALE -> "Mobile signal too old. Wait for Android to refresh the signal, then try again."
    CaptureError.SIGNAL_PERMISSION_DENIED -> "Signal access unavailable. Android denied access to the signal. Check app permissions, then try again."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CaptureStatus(state: AppUiState, model: SagapMoViewModel, onRecord: () -> Unit,
    onRequestPermission: () -> Unit, onOpenSettings: () -> Unit) {
    val phase = state.capturePhase
    val dismiss = { if (phase == CapturePhase.AWAITING_PERMISSION) model.cancelCapture() else model.dismissCapture() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp, 0.dp, 24.dp, 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = when (phase) {
                CapturePhase.AWAITING_PERMISSION -> "Add a pin to your reading"
                CapturePhase.LOCATING -> "Getting location"
                CapturePhase.ACQUIRING_SIGNAL -> "Reading signal"
                CapturePhase.SAVING -> "Saving reading"
                CapturePhase.SAVED -> "Reading saved"
                CapturePhase.SAVE_FAILED -> "Reading could not be saved"
                CapturePhase.INTERRUPTED -> "Recording interrupted"
                else -> "Reading not recorded"
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                SheetHeading(title, "Close recording status", dismiss)
            }
            when (phase) {
                CapturePhase.AWAITING_PERMISSION -> {
                    AppIcon(AppSymbol.PIN)
                    Text("Precise foreground location places this sample on the campus map. Location is used only while you record.")
                    Text("A valid signal can still be saved without a pin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("Continue to location", onRequestPermission)
                    TextButton(model::onPermissionResult, Modifier.heightIn(min = 48.dp)) { Text("Save without a pin") }
                }
                CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL, CapturePhase.SAVING -> {
                    Text(if (phase == CapturePhase.SAVING) "Your sample is being stored on this phone. Success appears only after saving finishes."
                        else "Stay at your sampling spot until recording finishes. Location can take up to 10 seconds.")
                    Text("You can keep browsing your saved readings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("Keep browsing", model::dismissCapture)
                    if (phase != CapturePhase.SAVING) TextButton(model::cancelCapture, Modifier.heightIn(min = 48.dp)) { Text("Cancel recording") }
                }
                CapturePhase.SAVED -> state.savedReading?.let { reading ->
                    AppIcon(AppSymbol.CHECK, tint = LocalRatingColors.current.good)
                    Text(reading.connectionType.label(), style = MaterialTheme.typography.bodyLarge)
                    Text(reading.rawDisplay(), style = MaterialTheme.typography.headlineLarge)
                    RatingLabel(reading.rating)
                    Text(reading.noLocationReason?.explanation() ?: "A pin is available on the campus map.")
                    if (state.filter != null && state.filter != reading.connectionType) Text("Your ${state.filter.label()} filter hides this ${reading.connectionType.label()} reading. It is still saved.")
                    PrimaryAction("Show saved reading", { model.openReading(reading) })
                    TextButton(model::dismissCapture, Modifier.heightIn(min = 48.dp)) { Text("Done") }
                }
                CapturePhase.SAVE_FAILED -> {
                    Text("Your captured sample is still here for this session. Retry saving the same reading without measuring again.")
                    PrimaryAction("Retry save", model::retrySave)
                }
                CapturePhase.INTERRUPTED -> {
                    Text("Recording ended before saving because the app left the foreground. No reading was saved.")
                    PrimaryAction("Record again", onRecord)
                }
                CapturePhase.FAILED -> {
                    Text(state.captureError?.explanation().orEmpty())
                    PrimaryAction("Retry reading", onRecord)
                    if (state.captureError == CaptureError.SIGNAL_PERMISSION_DENIED) TextButton(onOpenSettings, Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
                }
                else -> Unit
            }
        }
    }
}
