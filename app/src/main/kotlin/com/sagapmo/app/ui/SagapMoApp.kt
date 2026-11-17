package com.sagapmo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sagapmo.app.*
import com.sagapmo.app.map.CampusMapScreen
import org.maplibre.compose.style.BaseStyle

@Composable fun SagapMoApp(viewModel: SagapMoViewModel, onRecord: () -> Unit = { viewModel.record(false) },
    onRequestPermission: () -> Unit = {}, onOpenSettings: () -> Unit = {},
    baseStyle: BaseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty")) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 600.dp
            Row {
                if (expanded) NavigationRail(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top)), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Destination.entries.forEach { destination -> NavigationRailItem(
                        selected = state.destination == destination, onClick = { viewModel.setDestination(destination) },
                        icon = { AppIcon(if (destination == Destination.MAP) AppSymbol.MAP else AppSymbol.LIST) },
                        label = { Text(if (destination == Destination.MAP) "Map" else "Readings") }) }
                }
                Scaffold(Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surface,
                    topBar = { AppHeader() },
                    bottomBar = {
                        Column {
                            RecordPanel(state, onRecord, viewModel::retrySave)
                            if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
                                Destination.entries.forEach { destination -> NavigationBarItem(
                                    selected = state.destination == destination,
                                    onClick = { viewModel.setDestination(destination) },
                                    icon = { AppIcon(if (destination == Destination.MAP) AppSymbol.MAP else AppSymbol.LIST) },
                                    label = { Text(if (destination == Destination.MAP) "Map" else "Readings", style = MaterialTheme.typography.bodySmall) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurface, unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
                            } else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        if (state.destination == Destination.READINGS) ReadingsScreen(state, onRecord, viewModel::setFilter, viewModel::openReading, viewModel::retryReadings)
                        else Column(Modifier.fillMaxSize()) {
                            val largeText = LocalDensity.current.fontScale > 1.3f
                            if (largeText) MapFilters(state, viewModel)
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                            CampusMapScreen(state.visibleReadings, state.focusedReadingId, viewModel::openReading,
                                { viewModel.setDestination(Destination.READINGS) },
                                baseStyle = baseStyle, topOverlayHeight = if (largeText) 0.dp else 104.dp,
                                emptyTitle = if (state.readingsState is ReadingsState.Loaded && state.visibleReadings.isEmpty())
                                    if (state.allReadings.isEmpty()) "No readings yet" else "No ${state.filter?.label()} readings" else null,
                                onShowAll = if (state.allReadings.isNotEmpty()) ({ viewModel.setFilter(null) }) else null)
                            if (!largeText) MapFilters(state, viewModel)
                            if (state.readingsState == ReadingsState.Failed) Surface(Modifier.align(Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                                EmptyState("Readings could not load", "Your collection has not been reset.", "Retry readings", viewModel::retryReadings)
                            }
                            }
                        }
                    }
                }
            }
        }
    }
    state.selectedReading?.let { ReadingDetails(it, viewModel::closeDetails, viewModel::showOnMap, onOpenSettings) }
    if (state.captureVisible && !state.permissionLaunched && state.selectedReading == null) CaptureStatus(state, viewModel, onRecord, onRequestPermission, onOpenSettings)
}

@Composable private fun AppHeader() {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(buildAnnotatedString { append("SagapMo"); withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("?") } }, style = MaterialTheme.typography.titleLarge)
            Text("BatStateU · Alangilan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text("On this phone", Modifier.padding(10.dp, 8.dp).widthIn(max = 104.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun RecordPanel(state: AppUiState, onRecord: () -> Unit, onRetrySave: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            AppIcon(AppSymbol.INFO, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(if (state.destination == Destination.MAP) "Pins are individual samples" else "Your readings stay with you", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(if (state.destination == Destination.MAP) "Areas without pins have not been sampled." else "Stored on this phone. No account needed.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val retry = state.capturePhase == CapturePhase.SAVE_FAILED && !state.captureVisible
        PrimaryAction(if (retry) "Retry save" else if (state.recording) "Recording…" else "Record reading",
            if (retry) onRetrySave else onRecord, enabled = retry || (!state.recording && state.pendingSave == null), symbol = AppSymbol.RECORD)
    }
}

@Composable private fun MapFilters(state: AppUiState, model: SagapMoViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Filters(state, model::setFilter)
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
            Text("${state.visibleReadings.count { it.latitude != null }} pinned · ${state.visibleReadings.count { it.latitude == null }} without a pin",
                Modifier.padding(10.dp, 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
