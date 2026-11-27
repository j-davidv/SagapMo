package com.sagapmo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sagapmo.app.*
import com.sagapmo.app.R
import com.sagapmo.app.reading.Reading
import com.sagapmo.app.map.CampusMapScreen
import kotlinx.coroutines.launch
import org.maplibre.compose.style.BaseStyle

@Composable fun SagapMoApp(viewModel: SagapMoViewModel, onRecord: () -> Unit = { viewModel.record(false) },
    onRequestPermission: () -> Unit = {}, onOpenSettings: () -> Unit = {},
    baseStyle: BaseStyle? = null, onOpenLocationSettings: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeBaseStyle = baseStyle ?: remember(state.use3dMap, state.internetAvailable) {
        com.sagapmo.app.map.campusMapStyle(state.use3dMap, state.internetAvailable)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingLocation by remember { mutableStateOf<Reading?>(null) }
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
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = { AppHeader(viewModel::openSettings) },
                    bottomBar = {
                        Column {
                            if (expanded) RecordPanel(state, onRecord, viewModel::retrySave, viewModel::showRecordingProgress)
                            if (!expanded) FloatingNavigation(state, viewModel::setDestination, onRecord, viewModel::retrySave, viewModel::showRecordingProgress)
                            else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }) { padding ->
                    val floatingMap = !expanded && state.destination == Destination.MAP
                    val direction = LocalLayoutDirection.current
                    val contentPadding = PaddingValues(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                        top = padding.calculateTopPadding(), bottom = if (floatingMap) 0.dp else padding.calculateBottomPadding())
                    Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(padding)) {
                        if (state.destination == Destination.READINGS) ReadingsScreen(state, onRecord, viewModel::setFilter, viewModel::openReading, viewModel::retryReadings,
                            onDelete = { reading -> scope.launch {
                                if (viewModel.deleteReading(reading)) {
                                    if (snackbar.showSnackbar("Reading deleted", "Undo", withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed &&
                                        !viewModel.restoreReading(reading)) snackbar.showSnackbar("Couldn't restore reading. Try again.")
                                } else snackbar.showSnackbar("Couldn't delete reading. Try again.")
                            } })
                        else Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                            CampusMapScreen(state.visibleReadings, state.focusedReadingId, viewModel::openReading,
                                { viewModel.setDestination(Destination.READINGS) },
                                bottomOverlayHeight = if (floatingMap) padding.calculateBottomPadding() else 0.dp,
                                baseStyle = activeBaseStyle,
                                networkRecoveryCount = state.networkRecoveryCount,
                                emptyTitle = if (state.readingsState is ReadingsState.Loaded && state.visibleReadings.isEmpty())
                                    if (state.allReadings.isEmpty()) "No readings yet" else "No ${state.filter?.label()} readings" else null,
                                onShowAll = if (state.allReadings.isNotEmpty()) ({ viewModel.setFilter(null) }) else null)
                            MapFilters(state, viewModel, Modifier.align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = (if (floatingMap) padding.calculateBottomPadding() else 0.dp) + 64.dp))
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
    if (editingLocation == null) state.selectedReading?.let { ReadingDetails(it, viewModel::closeDetails, viewModel::showOnMap, onOpenSettings,
        onSetLocation = { reading -> editingLocation = reading },
        onDescriptionChange = { text -> viewModel.updateLocationDescription(it.id, text) },
        descriptionSaving = state.descriptionSavingId == it.id, descriptionFailed = state.descriptionErrorId == it.id,
        alwaysCollapseAdvanced = state.alwaysCollapseAdvancedInfo) }
    editingLocation?.let { reading -> LocationPicker(reading, activeBaseStyle, { editingLocation = null },
        onSave = { latitude, longitude -> viewModel.setManualLocation(reading, latitude, longitude) }) }
    if (state.captureVisible && !state.permissionLaunched && !state.locationSettingsLaunched && state.selectedReading == null)
        CaptureStatus(state, viewModel, onRecord, onRequestPermission, onOpenSettings, onOpenLocationSettings)
    if (state.settingsVisible) {
        SettingsDialog(
            state = state,
            onDismiss = viewModel::dismissSettings,
            onToggle3dMap = viewModel::setUse3dMap,
            onToggleSpeedTest = viewModel::setIncludeSpeedTest,
            onToggleAlwaysCollapseAdvanced = viewModel::setAlwaysCollapseAdvancedInfo,
            onOpenSystemSettings = onOpenSettings,
        )
    }
}

@Composable private fun AppHeader(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SagapMo", style = MaterialTheme.typography.titleLarge)
            Text("BatStateU · Alangilan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppTooltip("Settings") {
            FilledTonalIconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                AppIcon(AppSymbol.SETTINGS, "Settings")
            }
        }
    }
}

@Composable private fun RecordPanel(state: AppUiState, onRecord: () -> Unit, onRetrySave: () -> Unit, onViewProgress: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.recording && !state.captureVisible) {
            RecordingProgressButton(onViewProgress, Modifier.fillMaxWidth())
        } else {
            val retry = state.capturePhase == CapturePhase.SAVE_FAILED && !state.captureVisible
            PrimaryAction(if (retry) "Retry save" else if (state.recording) "Recording…" else "Record reading",
                if (retry) onRetrySave else onRecord, enabled = retry || (!state.recording && state.pendingSave == null), symbol = AppSymbol.RECORD)
        }
    }
}

@Composable private fun RecordingProgressButton(onViewProgress: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(onViewProgress, modifier.heightIn(min = 48.dp).semantics {
        liveRegion = LiveRegionMode.Polite
        onClick(label = "View recording progress", action = null)
    },
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp).clearAndSetSemantics {}, color = colors.onPrimaryContainer, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Recording…")
    }
}

@Composable private fun FloatingNavigation(state: AppUiState, onDestination: (Destination) -> Unit,
    onRecord: () -> Unit, onRetrySave: () -> Unit, onViewProgress: () -> Unit) {
    val retry = state.capturePhase == CapturePhase.SAVE_FAILED && !state.captureVisible
    val label = if (retry) "Retry save" else if (state.recording) "Recording…" else "Record reading"
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp, 14.dp)) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = CircleShape,
            color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Destination.entries.forEach { destination ->
                    if (destination == Destination.READINGS) {
                        if (state.recording && !state.captureVisible) {
                            Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                RecordingProgressButton(onViewProgress)
                            }
                        } else AppTooltip(label) {
                            FilledIconButton(if (retry) onRetrySave else onRecord,
                                Modifier.size(64.dp).offset(y = (-3).dp),
                                enabled = retry || (!state.recording && state.pendingSave == null),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                AppIcon(AppSymbol.RECORD, label)
                            }
                        }
                    }
                    val selected = state.destination == destination
                    AppTooltip(if (destination == Destination.MAP) "Map" else "Readings") {
                        IconButton(onClick = { onDestination(destination) }, modifier = Modifier.size(60.dp)
                            .semantics { this.selected = selected; role = Role.Tab }, shape = CircleShape) {
                            Surface(Modifier.size(52.dp), shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppIcon(if (destination == Destination.MAP) AppSymbol.HOME else AppSymbol.LIST,
                                        if (destination == Destination.MAP) "Map" else "Readings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MapFilters(state: AppUiState, model: SagapMoViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, com.sagapmo.app.reading.ConnectionType.WIFI, com.sagapmo.app.reading.ConnectionType.MOBILE).forEach { type ->
            val active = state.filter == type
            AppTooltip(type?.let { "${it.label()} connections" } ?: "All connections") {
                IconButton({ model.setFilter(type) }, Modifier.size(48.dp).testTag("filter-${type?.name ?: "ALL"}")
                    .semantics { selected = active }) {
                    Surface(Modifier.size(40.dp), shape = CircleShape, shadowElevation = 2.dp,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            if (type == null) Icon(painterResource(R.drawable.ic_connections_all), "All connections", Modifier.size(32.dp))
                            else AppIcon(type.symbol(), "${type.label()} connections", Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}
