package com.sagapmo.app.map

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.sagapmo.app.reading.Reading
import com.sagapmo.app.ui.*
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.*
import org.maplibre.compose.overlay.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import kotlinx.coroutines.*
import org.maplibre.compose.resource.MapResourceProvider

private val CampusCamera = CameraPosition(center = Position(121.07428, 13.78421), zoom = 17.0)

@Composable internal fun CampusMapScreen(readings: List<Reading>, focusedReadingId: String?,
    onOpenReading: (Reading) -> Unit, onViewReadings: () -> Unit,
    runtime: MapRuntime? = null,
    baseStyle: BaseStyle = DefaultMapStyle2D,
    emptyTitle: String? = null, onShowAll: (() -> Unit)? = null, resourceProvider: MapResourceProvider? = null,
    topOverlayHeight: Dp = 0.dp, bottomOverlayHeight: Dp = 0.dp) {
    var attempt by remember { mutableIntStateOf(1) }
    var camera by remember { mutableStateOf(CampusCamera) }
    key(attempt) {
        MapAttempt(readings, focusedReadingId, onOpenReading, onViewReadings, runtime, baseStyle,
            attempt, camera, { camera = it; attempt++ }, emptyTitle, onShowAll, resourceProvider, topOverlayHeight, bottomOverlayHeight)
    }
}

@Composable private fun MapAttempt(readings: List<Reading>, focusedReadingId: String?, onOpenReading: (Reading) -> Unit,
    onViewReadings: () -> Unit, runtime: MapRuntime?, baseStyle: BaseStyle, attempt: Int,
    initialCamera: CameraPosition, onRetry: (CameraPosition) -> Unit, emptyTitle: String?, onShowAll: (() -> Unit)?, resourceProvider: MapResourceProvider?, topOverlayHeight: Dp, bottomOverlayHeight: Dp) {
    var load by remember { mutableStateOf(MapLoadState(attempt, MapLoadStatus.LOADING)) }
    var observing by remember { mutableStateOf(false) }
    var lastFrameFull by remember { mutableStateOf(false) }
    val failedResources = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val mapRuntime = remember(runtime, resourceProvider) {
        runtime ?: createMapRuntime {
            this.resourceProvider = ObservedMapResources(resourceProvider ?: AndroidSpriteResources) { url, failed ->
                scope.launch(Dispatchers.Main.immediate) {
                    if (failed) {
                        if (url !in failedResources) failedResources += url
                        load = nextMapLoadState(load, MapLoadEvent.STYLE_FAILED, attempt)
                    } else failedResources.remove(url)
                }
            }
        }
    }
    DisposableEffect(mapRuntime) { onDispose { if (runtime == null) mapRuntime.close() } }
    val locatedReadings = remember(readings) {
        readings.mapNotNull { reading ->
            val latitude = reading.latitude ?: return@mapNotNull null
            val longitude = reading.longitude ?: return@mapNotNull null
            reading to Position(longitude, latitude)
        }
    }
    val pinArtwork = locatedReadings.map { (reading, _) -> reading.connectionType to reading.rating }
        .distinct().associateWith { (type, rating) -> key(type, rating) { rememberPinArtwork(type, rating) } }
    val map = rememberMapState(runtime = mapRuntime, baseStyle = baseStyle, initialCameraPosition = initialCamera) {
        campusLandmarks.forEach { CampusLabel(it) }
        locatedReadings.forEach { (reading, position) ->
            key(reading.id) {
                ReadingPinLayer(reading.id, position, pinArtwork.getValue(reading.connectionType to reading.rating))
            }
        }
    }
    LaunchedEffect(map) {
        withContext(Dispatchers.Main.immediate) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                map.events.collect { event ->
                    if (event is MapEvent.FrameRendered) lastFrameFull = event.stats?.mode == RenderStats.Mode.Full
                    val next = when (event) {
                        is MapEvent.StyleLoadFailed -> MapLoadEvent.STYLE_FAILED
                        is MapEvent.FrameRendered -> when (event.stats?.mode) {
                            RenderStats.Mode.Full -> if (map.style.loadState == StyleLoadState.Ready && failedResources.isEmpty()) MapLoadEvent.FULL_FRAME else null
                            RenderStats.Mode.Partial -> MapLoadEvent.PARTIAL_FRAME
                            else -> null
                        }
                        else -> null
                    }
                    if (next != null) load = nextMapLoadState(load, next, attempt)
                }
            }
            observing = true
            awaitCancellation()
        }
    }
    LaunchedEffect(map) {
        snapshotFlow { Triple(map.style.loadState, lastFrameFull, failedResources.isEmpty()) }.collect { (style, fullFrame, resourcesReady) ->
            if (style is StyleLoadState.Failed) load = nextMapLoadState(load, MapLoadEvent.STYLE_FAILED, attempt)
            else if (style == StyleLoadState.Ready && fullFrame && resourcesReady) load = nextMapLoadState(load, MapLoadEvent.FULL_FRAME, attempt)
        }
    }
    LaunchedEffect(load.status) {
        if (load.status == MapLoadStatus.LOADING) {
            withContext(Dispatchers.Main.immediate) {
                delay(15_000)
                load = nextMapLoadState(load, MapLoadEvent.DEADLINE, attempt)
            }
        }
    }
    val focusedReading = readings.find { it.id == focusedReadingId }
    LaunchedEffect(focusedReadingId, focusedReading?.latitude, focusedReading?.longitude) {
        readings.find { it.id == focusedReadingId && it.latitude != null && it.longitude != null }?.let {
            map.setCameraPosition(CameraPosition(center = Position(it.longitude!!, it.latitude!!), zoom = 18.0))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (observing) MaplibreMap(state = map, modifier = Modifier.fillMaxSize().testTag("map-surface").semantics {
                if (load.status == MapLoadStatus.READY && map.cameraPosition.zoom >= 17.0)
                    contentDescription = campusLandmarks.joinToString(", ") { it.name.replace('\n', ' ') }
            }, viewportInsets = PaddingValues(top = topOverlayHeight, bottom = bottomOverlayHeight)) {
                Box(Modifier.fillMaxSize().padding(top = topOverlayHeight, bottom = bottomOverlayHeight).padding(8.dp)) {
                    DisappearingScaleBar(metersPerDp = { map.viewport?.metersPerDpAtCenter ?: 0.0 },
                        zoom = { map.cameraPosition.zoom }, modifier = Modifier.align(Alignment.TopStart))
                    DisappearingCompassButton(Modifier.align(Alignment.TopEnd).padding(top = 56.dp))
                }
                locatedReadings.forEach { (reading, position) ->
                    key(reading.id) {
                        val artwork = pinArtwork.getValue(reading.connectionType to reading.rating)
                        SamplePinTarget(reading, artwork, showArtwork = map.style.loadState != StyleLoadState.Ready,
                            modifier = Modifier.placedAt(position, alignment = Alignment.BottomCenter)) {
                            onOpenReading(reading)
                        }
                    }
                }
            }
            if (load.status == MapLoadStatus.READY && emptyTitle != null) Box(Modifier.fillMaxSize().padding(top = topOverlayHeight, bottom = bottomOverlayHeight)) { Surface(
                Modifier.align(Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyState(emptyTitle, if (onShowAll == null) "Record your first signal sample. A usable outdoor location adds a pin."
                        else "This filter has no samples. Your other readings are still saved.",
                        if (onShowAll == null) null else "Show all readings", { onShowAll?.invoke() })
                }
            } }
            MapAttribution(map.style.attributions(), Modifier.align(Alignment.TopEnd)
                .padding(top = maxOf(topOverlayHeight, 16.dp), end = 16.dp))
            Surface(Modifier.align(Alignment.BottomEnd).padding(bottom = bottomOverlayHeight + 8.dp, end = 16.dp), shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp) {
                AppTooltip("Return to campus overview") {
                    IconButton(onClick = { map.setCameraPosition(CampusCamera) }, modifier = Modifier.size(48.dp)) {
                        AppIcon(AppSymbol.TARGET, "Return to campus overview")
                    }
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = bottomOverlayHeight + 8.dp), shape = RoundedCornerShape(6.dp)) {
                FlowRow(Modifier.padding(10.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.sagapmo.app.reading.Rating.entries.forEach { rating ->
                        val (ink, _) = ratingPair(rating)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).background(ink, RoundedCornerShape(3.dp)))
                            Text(rating.label(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            when (load.status) {
                MapLoadStatus.LOADING -> Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomOverlayHeight).padding(12.dp).testTag("map-loading"), shape = RoundedCornerShape(6.dp)) {
                    Text("Loading map… Readings are still available.", Modifier.padding(10.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                MapLoadStatus.FAILED -> Box(Modifier.fillMaxSize().padding(top = topOverlayHeight, bottom = bottomOverlayHeight)) { Surface(Modifier.align(Alignment.Center).padding(16.dp).testTag("map-error"), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Map could not load", style = MaterialTheme.typography.titleSmall)
                        Text("Your saved readings are still available. You can record a valid signal even if map tiles fail.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onRetry(map.cameraPosition) }, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text("Retry map") }
                            TextButton(onViewReadings, Modifier.heightIn(min = 48.dp)) { Text("View readings") }
                        }
                    }
                } }
                MapLoadStatus.READY -> Box(Modifier.testTag("map-ready"))
            }
        }
    }
}

@Composable internal fun MapAttribution(attributions: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.End) {
            AppTooltip("Map attribution") {
                IconButton({ expanded = !expanded }, Modifier.size(48.dp).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
                    Surface(Modifier.size(32.dp), shape = RoundedCornerShape(10.dp), shadowElevation = 2.dp) {
                        Box(contentAlignment = Alignment.Center) { AppIcon(AppSymbol.INFO, "Map attribution", Modifier.size(18.dp)) }
                    }
                }
            }
            if (expanded) Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp) {
                Column(Modifier.widthIn(max = 280.dp).padding(12.dp)) {
                    AttributionLinks(attributions, textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
                }
            }
    }
}

@Composable private fun SamplePinTarget(
    reading: Reading, artwork: PinArtwork, showArtwork: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    Box(modifier.offset(y = artwork.labelHeight).testTag("pin-${reading.id}")
        .size(artwork.width, maxOf(64.dp, artwork.height))
        .semantics(mergeDescendants = true) { contentDescription = "${reading.connectionType.label()}, ${reading.rating.label()}, ${reading.rawDisplay()}, ${reading.shortTime()}" }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.BottomCenter) {
        if (showArtwork) {
            Image(artwork.painter, contentDescription = null, modifier = Modifier.size(artwork.width, artwork.height))
        }
    }
}
