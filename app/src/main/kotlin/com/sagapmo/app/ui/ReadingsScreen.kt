package com.sagapmo.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sagapmo.app.*
import com.sagapmo.app.reading.*
import androidx.compose.ui.unit.dp

@Composable fun ReadingsScreen(state: AppUiState, onRecord: () -> Unit, onFilter: (ConnectionType?) -> Unit,
    onOpen: (Reading) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your readings", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${state.allReadings.size} saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Filters(state, onFilter)
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Newest first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Saved on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    ReadingRow(reading, { onOpen(reading) })
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
            val reason = reading.noLocationReason
            val color = if (reason == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalRatingColors.current.fair
            AppIcon(if (reason == null) AppSymbol.PIN else AppSymbol.NO_PIN, modifier = Modifier.size(16.dp), tint = color)
            Text(if (reason == null) "On map · Accuracy ±${reading.accuracyMetres?.toInt()} m"
                else "No pin · ${reason.explanation().removeSuffix(" Saved without a pin.")}",
                style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
