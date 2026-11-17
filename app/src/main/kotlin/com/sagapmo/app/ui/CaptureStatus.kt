package com.sagapmo.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import com.sagapmo.app.*
import com.sagapmo.app.capture.CaptureError
import com.sagapmo.app.reading.*

fun CaptureError.explanation() = when (this) {
    CaptureError.NO_CONNECTION -> "Connect to Wi-Fi or mobile data, then try recording again. Saved readings are still available."
    CaptureError.UNSUPPORTED_CONNECTION -> "This connection is not supported. Use Wi-Fi or Mobile as the active default connection."
    CaptureError.VPN_ACTIVE -> "The pilot needs a Wi-Fi or Mobile connection it can identify reliably. Disconnect the VPN in Android, then retry."
    CaptureError.CONNECTION_CHANGED -> "Your connection changed while the signal was being read. No reading was saved."
    CaptureError.SIGNAL_UNAVAILABLE, CaptureError.SIGNAL_INVALID -> "Android did not provide a valid signal value. Check your connection and try again. No reading was saved."
    CaptureError.SIGNAL_STALE -> "The available mobile measurement is older than 60 seconds. Wait for an updated signal, then retry. No reading was saved."
    CaptureError.SIGNAL_PERMISSION_DENIED -> "Signal access unavailable. Android denied access to the signal. Check app permissions, then try again."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CaptureStatus(state: AppUiState, model: SagapMoViewModel, onRecord: () -> Unit,
    onRequestPermission: () -> Unit, onOpenSettings: () -> Unit) {
    val phase = state.capturePhase
    val dismiss = { if (phase == CapturePhase.AWAITING_PERMISSION) model.cancelCapture() else model.dismissCapture() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), containerColor = MaterialTheme.colorScheme.surface) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp, 0.dp, 24.dp, 26.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val title = when (phase) {
                CapturePhase.AWAITING_PERMISSION -> "Add a pin to your reading"
                CapturePhase.LOCATING -> "Getting location"
                CapturePhase.ACQUIRING_SIGNAL -> "Reading signal"
                CapturePhase.SAVING -> "Saving reading"
                CapturePhase.SAVED -> "Reading saved"
                CapturePhase.SAVE_FAILED -> "Reading could not be saved"
                CapturePhase.INTERRUPTED -> "Recording interrupted"
                CapturePhase.FAILED -> when (state.captureError) {
                    CaptureError.NO_CONNECTION -> "No supported connection"
                    CaptureError.VPN_ACTIVE -> "Disconnect your VPN"
                    CaptureError.CONNECTION_CHANGED -> "Connection changed. Try again."
                    CaptureError.SIGNAL_STALE -> "Mobile signal too old"
                    else -> "Signal unavailable"
                }
                else -> "Reading not recorded"
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                SheetHeading(title, "Close recording status", dismiss, closable = phase != CapturePhase.SAVING)
            }
            when (phase) {
                CapturePhase.AWAITING_PERMISSION -> {
                    Text("Precise location helps place your outdoor sample on the campus map.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoBox("You can still save without location", "A valid signal reading stays in your list, even if you skip location or Android cannot get a usable fix.")
                    Text("Location is used while recording. Android will ask you to choose an access level next.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("Continue to location", onRequestPermission)
                    TextButton(model::onPermissionResult, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save without a pin") }
                }
                CapturePhase.LOCATING, CapturePhase.ACQUIRING_SIGNAL, CapturePhase.SAVING -> {
                    Text(if (phase == CapturePhase.SAVING) "Your signal is ready. Wait for confirmation before leaving the app."
                        else "Stay at your sampling spot until recording finishes. Location can take up to 10 seconds.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val step = if (phase == CapturePhase.SAVING) 2 else if (phase == CapturePhase.ACQUIRING_SIGNAL) 1 else 0
                    LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Get an outdoor location", "Read the active connection", "Save one reading").forEachIndexed { index, label ->
                            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(shape = RoundedCornerShape(16.dp), color = if (index == step) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    contentColor = if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    border = BorderStroke(1.dp, if (index == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(Modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                                        if (index < step) AppIcon(AppSymbol.CHECK, modifier = Modifier.size(16.dp)) else Text("${index + 1}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(label, color = if (index == step) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("$title…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(model::dismissCapture, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Keep browsing") }
                    if (phase != CapturePhase.SAVING) TextButton(model::cancelCapture, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel recording") }
                }
                CapturePhase.SAVED -> state.savedReading?.let { reading ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(AppSymbol.CHECK, tint = LocalRatingColors.current.good)
                        Column {
                            Text("${reading.connectionType.label()} · ${reading.rating.label()}", style = MaterialTheme.typography.bodyLarge)
                            Text("${reading.rawDisplay()} · ${reading.shortTime().substringAfter(" · ")}")
                        }
                    }
                    InfoBox(if (reading.noLocationReason == null) "Saved with a map pin" else "Saved without a pin",
                        reading.noLocationReason?.explanation() ?: "Your reading is in the list and on the map.", warning = reading.noLocationReason != null)
                    if (state.filter != null && state.filter != reading.connectionType) Text("Your ${state.filter.label()} filter hides this ${reading.connectionType.label()} sample. Show saved reading will reveal it.")
                    PrimaryAction("Show saved reading", { model.openReading(reading) })
                    TextButton(model::dismissCapture, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
                }
                CapturePhase.SAVE_FAILED -> {
                    Text("Your measured sample is still held for this attempt. Retry saving it without taking a new measurement. Closing the app may lose this unsaved sample.")
                    PrimaryAction("Retry save", model::retrySave)
                }
                CapturePhase.INTERRUPTED -> {
                    Text("The app left the foreground before saving began. Return to your sampling spot and record a new reading.")
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
}
