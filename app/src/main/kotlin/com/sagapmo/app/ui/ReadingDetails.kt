package com.sagapmo.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sagapmo.app.reading.*
import java.time.*
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReadingDetails(reading: Reading, onDismiss: () -> Unit, onShowOnMap: (Reading) -> Unit,
    onOpenSettings: () -> Unit = {}, onSetLocation: ((Reading) -> Unit)? = null,
    onDescriptionChange: ((String) -> Unit)? = null, descriptionSaving: Boolean = false, descriptionFailed: Boolean = false) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.testTag("reading-details").verticalScroll(rememberScrollState()).padding(24.dp, 0.dp, 24.dp, 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeading("Reading details", "Close reading details", onDismiss)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { AppIcon(reading.connectionType.symbol()) }
                }
                Column {
                    Text(reading.connectionType.label(), style = MaterialTheme.typography.bodyLarge)
                    Text("Saved sample", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SignalValue(reading)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RatingLabel(reading.rating)
                Text(reading.fullTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val offset = Instant.ofEpochMilli(reading.capturedAtEpochMillis).atZone(ZoneId.systemDefault()).offset.id.let { if (it == "Z") "+00:00" else it }
                Text("Local time · UTC$offset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (reading.downloadMbps != null || reading.uploadMbps != null || reading.latencyMs != null) {
                Column {
                    reading.downloadMbps?.let { DetailRow("Download Speed", String.format(Locale.US, "%.1f Mbps", it)) }
                    reading.uploadMbps?.let { DetailRow("Upload Speed", String.format(Locale.US, "%.1f Mbps", it)) }
                    reading.latencyMs?.let { DetailRow("Network Latency", String.format(Locale.US, "%.0f ms", it)) }
                    reading.jitterMs?.let { DetailRow("Latency Jitter", String.format(Locale.US, "%.0f ms", it)) }
                }
            }
            if (reading.latitude != null) {
                Column {
                    DetailRow("Coordinates", String.format(Locale.US, "%.5f, %.5f", reading.latitude, reading.longitude))
                    if (!reading.locationManuallySet) DetailRow("Reported accuracy", reading.accuracyMetres?.let { "±${it.toString().removeSuffix(".0")} metres" } ?: "Unknown")
                    reading.locationAgeMillisAtCapture?.let { age -> DetailRow("Location age at capture", "${age / 1000.0} seconds") }
                    if (reading.locationManuallySet) DetailRow("Location source", "Set manually")
                    if (reading.noLocationReason in setOf(NoLocationReason.LOCATION_INACCURATE, NoLocationReason.APPROXIMATE_ONLY)) InfoBox("Approximate location", "The phone reported approximate or uncertain coordinates. You can correct the pin using Set location.", warning = true)
                }
            } else {
                DetailRow("Map pin", "Not available")
                InfoBox("Saved without a pin", reading.noLocationReason?.explanation() ?: "No location available.", warning = true)
                Text("This reading stays in your list. You can add a pin using Set location.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (reading.noLocationReason in setOf(NoLocationReason.PERMISSION_DENIED, NoLocationReason.APPROXIMATE_ONLY)) {
                    TextButton(onOpenSettings, Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
                }
            }
            onDescriptionChange?.let { LocationDescriptionEditor(reading, descriptionSaving, descriptionFailed, it) }
            onSetLocation?.let { setLocation -> OutlinedButton({ setLocation(reading) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { AppIcon(AppSymbol.PIN); Spacer(Modifier.width(8.dp)); Text("Set location") } }
            if (reading.latitude != null) PrimaryAction("Show on map", { onShowOnMap(reading) }, symbol = AppSymbol.MAP)
            else PrimaryAction("Back to readings", onDismiss)
        }
    }
}

@Composable internal fun SheetHeading(title: String, closeLabel: String, onDismiss: () -> Unit, closable: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (closable) AppTooltip(closeLabel) {
            if (closable) FilledTonalIconButton(onDismiss, Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) { AppIcon(AppSymbol.CLOSE, closeLabel) }
        }
    }
}
@Composable internal fun InfoBox(title: String, text: String, warning: Boolean = false) {
    val ratings = LocalRatingColors.current
    Surface(shape = RoundedCornerShape(10.dp), color = if (warning) ratings.fairBg else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (warning) ratings.fair else MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable private fun DetailRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.weight(1f).widthIn(min = 150.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
