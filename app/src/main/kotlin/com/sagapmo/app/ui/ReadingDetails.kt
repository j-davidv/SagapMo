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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReadingDetails(reading: Reading, onDismiss: () -> Unit, onShowOnMap: (Reading) -> Unit,
    onOpenSettings: () -> Unit = {}) {
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
            Text(reading.rawDisplay(), style = MaterialTheme.typography.headlineLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RatingLabel(reading.rating)
                Text(reading.fullTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val offset = Instant.ofEpochMilli(reading.capturedAtEpochMillis).atZone(ZoneId.systemDefault()).offset.id.let { if (it == "Z") "+00:00" else it }
                Text("Local time · UTC$offset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (reading.noLocationReason == null) {
                Column {
                    DetailRow("Coordinates", String.format(Locale.US, "%.5f, %.5f", reading.latitude, reading.longitude))
                    DetailRow("Reported accuracy", "±${reading.accuracyMetres} metres")
                    DetailRow("Location age at capture", "${reading.locationAgeMillisAtCapture!! / 1000.0} seconds")
                    DetailRow("Signal age at capture", reading.signalAgeMillisAtCapture?.let { "${it / 1000.0} seconds" } ?: "Unavailable for Wi-Fi")
                }
            } else {
                Text(reading.noLocationReason.explanation(), style = MaterialTheme.typography.bodyMedium)
                DetailRow("Signal age at capture", reading.signalAgeMillisAtCapture?.let { "${it / 1000.0} seconds" } ?: "Unavailable for Wi-Fi")
                if (reading.noLocationReason in setOf(NoLocationReason.PERMISSION_DENIED, NoLocationReason.APPROXIMATE_ONLY)) {
                    TextButton(onOpenSettings, Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
                }
            }
            if (reading.connectionType == ConnectionType.WIFI) Text("Wi-Fi measurement age unavailable. Android does not provide a timestamp for this RSSI value.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reading.latitude != null) PrimaryAction("Show on map", { onShowOnMap(reading) }, symbol = AppSymbol.MAP)
        }
    }
}

@Composable internal fun SheetHeading(title: String, closeLabel: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton(onDismiss, Modifier.size(48.dp)) { AppIcon(AppSymbol.CLOSE, closeLabel) }
    }
}
@Composable private fun DetailRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.weight(1f).widthIn(min = 150.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
