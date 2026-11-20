package com.sagapmo.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.sagapmo.app.*
import com.sagapmo.app.reading.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

@Composable fun ReadingsScreen(state: AppUiState, onRecord: () -> Unit, onFilter: (ConnectionType?) -> Unit,
    onOpen: (Reading) -> Unit, onRetry: () -> Unit, onDelete: (Reading) -> Unit) {
    var pendingDeletion by remember { mutableStateOf<Reading?>(null) }
    pendingDeletion?.let { reading ->
        AlertDialog(onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete reading?") },
            text = { Text("Delete this ${reading.connectionType.label()} reading from ${reading.shortTime()}?") },
            confirmButton = { TextButton({ pendingDeletion = null; onDelete(reading) }) {
                AppIcon(AppSymbol.DELETE, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete", color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton({ pendingDeletion = null }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your readings", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${state.allReadings.size} saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Filters(state, onFilter)
        }
        when (state.readingsState) {
            ReadingsState.Loading -> Column(Modifier.padding(20.dp).testTag("readings-loading"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Loading saved readings", style = MaterialTheme.typography.bodyMedium)
                repeat(3) { Box(Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))) }
            }
            ReadingsState.Failed -> EmptyState("Readings could not load", "Your collection has not been reset. Try loading it again.", "Retry readings", onRetry)
            is ReadingsState.Loaded -> if (state.visibleReadings.isEmpty()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyState(if (state.allReadings.isEmpty()) "No readings yet" else "No ${state.filter?.label()} readings",
                        if (state.allReadings.isEmpty()) "Record a signal sample. Find it here later, even without a pin or internet access."
                        else "Try All to see your other saved samples.",
                        if (state.allReadings.isEmpty()) null else "Show all readings", { onFilter(null) })
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(state.visibleReadings, key = { it.id }) { reading ->
                    var deletionRequested by remember { mutableStateOf(false) }
                    var rowWidth by remember { mutableIntStateOf(0) }
                    val swipeState = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart && rowWidth > 0 &&
                            (swipeState.value?.requireOffset() ?: 0f) <= -rowWidth * 0.5f) deletionRequested = true
                        false
                    }, positionalThreshold = { width -> width * 0.5f })
                    SideEffect { swipeState.value = dismiss }
                    LaunchedEffect(deletionRequested) {
                        if (deletionRequested) {
                            snapshotFlow { abs(dismiss.requireOffset()) < 0.5f }.first { it }
                            pendingDeletion = reading
                            deletionRequested = false
                        }
                    }
                    SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem().onSizeChanged { rowWidth = it.width }.semantics { customActions = listOf(CustomAccessibilityAction("Delete reading") { pendingDeletion = reading; true }) },
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppSymbol.DELETE, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }) {
                        Surface { ReadingRow(reading, { onOpen(reading) }) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable private fun ReadingRow(reading: Reading, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("reading-${reading.id}").clickable(onClick = onClick).padding(20.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).widthIn(min = 155.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { AppIcon(reading.connectionType.symbol()) }
                }
                Column(Modifier.weight(1f)) {
                    Text(reading.connectionType.label(), style = MaterialTheme.typography.bodyLarge)
                    Text(reading.shortTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RatingLabel(reading.rating)
                Text(reading.rawDisplay(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            val approximate = reading.noLocationReason in setOf(NoLocationReason.LOCATION_INACCURATE, NoLocationReason.APPROXIMATE_ONLY)
            val color = if (reading.latitude == null || approximate) LocalRatingColors.current.fair else MaterialTheme.colorScheme.onSurfaceVariant
            AppIcon(if (reading.latitude == null) AppSymbol.NO_PIN else AppSymbol.PIN, modifier = Modifier.size(16.dp), tint = color)
            Text(reading.locationSummary(), style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (reading.downloadMbps != null || reading.uploadMbps != null || reading.latencyMs != null) {
            val speedSummary = listOfNotNull(
                reading.downloadMbps?.let { "↓ ${String.format(java.util.Locale.US, "%.1f", it)} Mbps" },
                reading.uploadMbps?.let { "↑ ${String.format(java.util.Locale.US, "%.1f", it)} Mbps" },
                reading.latencyMs?.let { "${String.format(java.util.Locale.US, "%.0f", it)} ms" }
            ).joinToString("  ·  ")
            if (speedSummary.isNotBlank()) {
                Text(speedSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
        if (reading.locationDescription.isNotBlank()) Text(reading.locationDescription,
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
