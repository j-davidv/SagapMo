package com.sagapmo.app.map

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sagapmo.app.reading.Reading
import com.sagapmo.app.ui.*
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.*
import org.maplibre.compose.overlay.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private val CampusCamera = CameraPosition(center = Position(121.07428, 13.78421), zoom = 17.0)

@Composable internal fun CampusMapScreen(readings: List<Reading>, focusedReadingId: String?,
    onOpenReading: (Reading) -> Unit, onViewReadings: () -> Unit,
    runtime: MapRuntime = DefaultMapRuntime.instance,
    baseStyle: BaseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty")) {
    val map = rememberMapState(runtime = runtime, baseStyle = baseStyle, initialCameraPosition = CampusCamera)
    LaunchedEffect(focusedReadingId) {
        readings.find { it.id == focusedReadingId && it.latitude != null && it.longitude != null }?.let {
            map.setCameraPosition(CameraPosition(center = Position(it.longitude!!, it.latitude!!), zoom = 18.0))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MaplibreMap(state = map, modifier = Modifier.fillMaxSize()) {
                include(MapOverlay.Default)
                readings.filter { it.latitude != null && it.longitude != null }.forEach { reading ->
                    SamplePin(reading, Modifier.placedAt(Position(reading.longitude!!, reading.latitude!!), alignment = Alignment.BottomCenter)) {
                        onOpenReading(reading)
                    }
                }
            }
            Surface(Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 16.dp), shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp) {
                IconButton(onClick = { map.setCameraPosition(CampusCamera) }, modifier = Modifier.size(48.dp)) {
                    AppIcon(AppSymbol.TARGET, "Return to campus overview")
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 48.dp), shape = RoundedCornerShape(6.dp)) {
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
        }
        Text("OpenFreeMap · OpenMapTiles · © OpenStreetMap", Modifier.fillMaxWidth().padding(12.dp, 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PinShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w / 2, h)
    cubicTo(w * .36f, h * .80f, 0f, h * .53f, 0f, h * .39f)
    cubicTo(0f, -h * .13f, w, -h * .13f, w, h * .39f)
    cubicTo(w, h * .53f, w * .64f, h * .80f, w / 2, h)
    close()
}
@Composable private fun SamplePin(reading: Reading, modifier: Modifier, onClick: () -> Unit) {
    val (ink, background) = ratingPair(reading.rating)
    Column(modifier.testTag("pin-${reading.id}").widthIn(min = 48.dp).heightIn(min = 64.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${reading.connectionType.label()}, ${reading.rating.label()}, ${reading.rawDisplay()}, ${reading.shortTime()}" }
        .clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(36.dp, 44.dp), shape = PinShape, color = ink, contentColor = background, shadowElevation = 3.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {
            Box(Modifier.padding(bottom = 10.dp), contentAlignment = Alignment.Center) { AppIcon(reading.connectionType.symbol(), modifier = Modifier.size(22.dp)) }
        }
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
            Text(reading.rating.label(), Modifier.padding(5.dp, 2.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
