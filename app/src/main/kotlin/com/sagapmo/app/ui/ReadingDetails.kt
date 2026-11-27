package com.sagapmo.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sagapmo.app.reading.*
import com.sagapmo.app.speedtest.SpeedTestStatus
import java.time.*
import java.util.Locale

data class MetricInfo(val title: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReadingDetails(
    reading: Reading,
    onDismiss: () -> Unit,
    onShowOnMap: (Reading) -> Unit,
    onOpenSettings: () -> Unit = {},
    onSetLocation: ((Reading) -> Unit)? = null,
    onDescriptionChange: ((String) -> Unit)? = null,
    descriptionSaving: Boolean = false,
    descriptionFailed: Boolean = false,
    alwaysCollapseAdvanced: Boolean = true,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(!alwaysCollapseAdvanced) }
    var activeMetricInfo by remember { mutableStateOf<MetricInfo?>(null) }

    activeMetricInfo?.let { info ->
        MetricInfoDialog(info = info, onDismiss = { activeMetricInfo = null })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Keep the expanded anchor stable as the sheet consumes top insets during a drag.
        modifier = Modifier.statusBarsPadding(),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .testTag("reading-details")
                .verticalScroll(rememberScrollState())
                .padding(24.dp, 0.dp, 24.dp, 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeading("Reading details", "Close reading details", onDismiss)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { AppIcon(reading.connectionType.symbol()) }
                }
                Column {
                    Text(reading.connectionLabel(), style = MaterialTheme.typography.bodyLarge)
                    val subtitle = when {
                        reading.connectionType != ConnectionType.MOBILE -> "Saved sample"
                        reading.mobileGeneration == null -> "Network generation unavailable"
                        else -> "Android network label at capture"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Signal banner with (i) explaining dBm / level & Mbps
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SignalValue(reading)
                IconButton(
                    onClick = {
                        val isWifi = reading.connectionType == ConnectionType.WIFI
                        activeMetricInfo = MetricInfo(
                            title = if (isWifi) "dBm & Mbps" else "Signal Level & Mbps",
                            description = if (isWifi) {
                                "• dBm (Decibel-milliwatts): Measures wireless signal strength. Values are negative; closer to 0 is stronger (e.g. -50 dBm is good, -85 dBm is weak).\n\n• Mbps (Megabits per second): Speed of data transmission. Higher numbers mean faster file transfers and web browsing."
                            } else {
                                "• Signal Level (0-4): Android standard cellular signal bars. Level 4 indicates maximum reception.\n\n• Mbps (Megabits per second): Speed of data transmission. Higher numbers mean faster file transfers and web browsing.\n\n• 2G/3G/4G/5G: Android's network label at capture, based on carrier policy. A 5G label does not guarantee an active 5G radio connection or a particular speed."
                            }
                        )
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    AppIcon(AppSymbol.INFO, "About signal strength and speed", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RatingLabel(reading.rating)
                Text(reading.fullTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val offset = Instant.ofEpochMilli(reading.capturedAtEpochMillis).atZone(ZoneId.systemDefault()).offset.id.let { if (it == "Z") "+00:00" else it }
                Text("Local time · UTC$offset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (reading.connectionType == ConnectionType.MOBILE) {
                Column {
                    DetailRow("Carrier", reading.cellularDetails?.carrierName ?: "Unavailable")
                    DetailRow("Radio type", reading.cellularDetails?.networkType.radioTypeLabel(), onInfoClick = {
                        activeMetricInfo = MetricInfo("Radio type",
                            "The base radio type reported for the active data SIM. LTE can appear alongside a 5G display label. Carrier is the currently registered network, which can differ from the SIM provider when roaming.")
                    })
                }
            }

            // Basic speed metrics (Always visible when available)
            if (reading.downloadMbps != null || reading.uploadMbps != null) {
                Column {
                    reading.downloadMbps?.let {
                        DetailRow(
                            label = "Download Speed",
                            value = String.format(Locale.US, "%.1f Mbps", it),
                            onInfoClick = {
                                activeMetricInfo = MetricInfo(
                                    title = "Download Speed",
                                    description = "How quickly your device downloads content from the internet. Essential for loading webpages, video streaming, and app downloads. Measured in Megabits per second (Mbps)."
                                )
                            }
                        )
                    }
                    reading.uploadMbps?.let {
                        DetailRow(
                            label = "Upload Speed",
                            value = String.format(Locale.US, "%.1f Mbps", it),
                            onInfoClick = {
                                activeMetricInfo = MetricInfo(
                                    title = "Upload Speed",
                                    description = "How quickly your device sends data up to internet servers. Essential for video calls, sending messages, submitting online quizzes, and uploading documents. Measured in Megabits per second (Mbps)."
                                )
                            }
                        )
                    }
                }
            }

            if (reading.speedTestStatus == SpeedTestStatus.FAILED) {
                val message = when {
                    reading.downloadMbps != null -> "Upload speed unavailable"
                    reading.uploadMbps != null -> "Download speed unavailable"
                    else -> "Download and upload speeds unavailable"
                }
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Advanced Information collapsible section
            val hasAdvancedInfo = reading.connectionType == ConnectionType.MOBILE || reading.latencyMs != null || reading.jitterMs != null || reading.latitude != null || reading.wifiConnectedAgeMillis != null
            if (hasAdvancedInfo) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { showAdvanced = !showAdvanced }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("toggle-advanced-info"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Advanced Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AppIcon(
                                if (showAdvanced) AppSymbol.CHEVRON_UP else AppSymbol.CHEVRON_DOWN,
                                description = if (showAdvanced) "Collapse advanced information" else "Expand advanced information",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                                if (reading.connectionType == ConnectionType.MOBILE) {
                                    val cellular = reading.cellularDetails
                                    DetailRow("Signal age at capture", reading.signalAgeMillisAtCapture?.let { "${it / 1000.0} seconds" } ?: "Unavailable", onInfoClick = {
                                        activeMetricInfo = MetricInfo("Signal age at capture",
                                            "Age of the modem's signal report when this reading was saved. Smaller values mean a newer measurement. Android controls when the modem updates its report. This saved age stays the same when you reopen the reading.")
                                    })
                                    listOf(
                                        Triple(MetricInfo("LTE RSRP",
                                            "Reference Signal Received Power. Measures the strength of the LTE reference signal in dBm. Less negative values mean a stronger signal: -90 dBm is stronger than -110 dBm. This measures radio signal power rather than download speed."), cellular?.lteRsrpDbm, "dBm"),
                                        Triple(MetricInfo("LTE RSRQ",
                                            "Reference Signal Received Quality. Compares the LTE reference signal with total received power, in dB. Higher values generally mean better quality: -8 dB is higher than -16 dB. Interference and network traffic can affect this measurement."), cellular?.lteRsrqDb, "dB"),
                                        Triple(MetricInfo("LTE RSSNR",
                                            "Reference Signal Signal-to-Noise Ratio, reported by Android in dB. Compares the LTE reference signal with background noise. Higher values generally mean a clearer signal. Negative values mean noise is stronger relative to the measured signal."), cellular?.lteRssnrDb, "dB"),
                                        Triple(MetricInfo("LTE RSSI",
                                            "Received Signal Strength Indicator. Total received LTE power, including useful signal, other transmissions, and noise, in dBm. Less negative values mean more received power. Higher RSSI alone does not guarantee better reception because interference also contributes power."), cellular?.lteRssiDbm, "dBm"),
                                        Triple(MetricInfo("5G SS-RSRP",
                                            "Synchronization Signal Reference Signal Received Power. Measures 5G NR synchronization reference signal strength in dBm. Less negative values mean a stronger signal. A 4G-only sample normally has no reported value here; phones may also omit it while displaying a 5G label."), cellular?.nrSsRsrpDbm, "dBm"),
                                        Triple(MetricInfo("5G SS-RSRQ",
                                            "Synchronization Signal Reference Signal Received Quality. Measures 5G NR synchronization signal quality relative to total received power, in dB. Higher values generally mean better quality. A phone can report LTE quality while leaving this 5G measurement unavailable."), cellular?.nrSsRsrqDb, "dB"),
                                        Triple(MetricInfo("5G SS-SINR",
                                            "Synchronization Signal-to-Interference-plus-Noise Ratio. Compares the 5G NR synchronization signal with interference and noise, in dB. Higher values generally mean a clearer signal. Zero means equal signal and interference-plus-noise power; negative values mean interference and noise are stronger."), cellular?.nrSsSinrDb, "dB"),
                                    ).forEach { (info, value, unit) ->
                                        DetailRow(info.title, value?.let { "$it $unit" } ?: "Unavailable", onInfoClick = {
                                            activeMetricInfo = info.copy(description = "${info.description}\n\nUnavailable means Android did not report a usable value for this saved reading.")
                                        })
                                    }
                                    Text("RSRP measures signal power; RSRQ measures quality. LTE RSSNR is Android's signal-to-noise ratio; 5G SS-SINR includes interference. LTE and 5G values remain separate. Unavailable means the phone did not report a usable value.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                reading.latencyMs?.let {
                                    DetailRow(
                                        label = "Network Latency",
                                        value = String.format(Locale.US, "%.0f ms", it),
                                        onInfoClick = {
                                            activeMetricInfo = MetricInfo(
                                                title = "Network Latency (Ping)",
                                                description = "The round-trip delay in milliseconds (ms) for data to travel from your phone to the server and back. Lower is better (e.g., <50 ms is great, >150 ms feels sluggish)."
                                            )
                                        }
                                    )
                                }
                                reading.jitterMs?.let {
                                    DetailRow(
                                        label = "Latency Jitter",
                                        value = String.format(Locale.US, "%.0f ms", it),
                                        onInfoClick = {
                                            activeMetricInfo = MetricInfo(
                                                title = "Latency Jitter",
                                                description = "Variation or inconsistency in response time between packets. Low jitter (under 10 ms) ensures smooth real-time video calls and live sessions without stutter."
                                            )
                                        }
                                    )
                                }
                                if (reading.latitude != null) {
                                    DetailRow(
                                        label = "Coordinates",
                                        value = String.format(Locale.US, "%.5f, %.5f", reading.latitude, reading.longitude),
                                        onInfoClick = {
                                            activeMetricInfo = MetricInfo(
                                                title = "Coordinates",
                                                description = "Geographic latitude and longitude where the signal reading was registered on campus."
                                            )
                                        }
                                    )
                                    if (!reading.locationManuallySet) {
                                        DetailRow(
                                            label = "Reported accuracy",
                                            value = reading.accuracyMetres?.let { "±${it.toString().removeSuffix(".0")} metres" } ?: "Unknown",
                                            onInfoClick = {
                                                activeMetricInfo = MetricInfo(
                                                    title = "Reported Accuracy",
                                                    description = "The estimated radius reported by device sensors. There is a 68% probability that the actual position lies within this distance."
                                                )
                                            }
                                        )
                                    }
                                    reading.locationAgeMillisAtCapture?.let { age ->
                                        DetailRow(
                                            label = "Location age at capture",
                                            value = "${age / 1000.0} seconds",
                                            onInfoClick = {
                                                activeMetricInfo = MetricInfo(
                                                    title = "Location Age at Capture",
                                                    description = "How fresh the GPS or network fix was when you recorded this reading. Fresher fixes provide greater positioning reliability."
                                                )
                                            }
                                        )
                                    }
                                    if (reading.locationManuallySet) DetailRow("Location source", "Set manually")
                                    reading.wifiConnectedAgeMillis?.let { age ->
                                        DetailRow("Wi-Fi connected age", "${age / 1000} seconds")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (reading.latitude != null) {
                if (reading.noLocationReason in setOf(NoLocationReason.LOCATION_INACCURATE, NoLocationReason.APPROXIMATE_ONLY)) {
                    InfoBox("Approximate location", "The phone reported approximate or uncertain coordinates. You can correct the pin using Set location.", warning = true)
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
            onSetLocation?.let { setLocation ->
                OutlinedButton({ setLocation(reading) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    AppIcon(AppSymbol.PIN)
                    Spacer(Modifier.width(8.dp))
                    Text("Set location")
                }
            }
            if (reading.latitude != null) PrimaryAction("Show on map", { onShowOnMap(reading) }, symbol = AppSymbol.MAP)
            else PrimaryAction("Back to readings", onDismiss)
        }
    }
}

@Composable
private fun MetricInfoDialog(info: MetricInfo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppIcon(AppSymbol.INFO, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

@Composable internal fun SheetHeading(title: String, closeLabel: String, onDismiss: () -> Unit, closable: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (closable) AppTooltip(closeLabel) {
            FilledTonalIconButton(onDismiss, Modifier.size(48.dp),
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

@Composable private fun DetailRow(
    label: String,
    value: String,
    onInfoClick: (() -> Unit)? = null
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(label, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                    AppIcon(AppSymbol.INFO, description = "About $label", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
