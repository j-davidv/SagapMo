package com.sagapmo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sagapmo.app.map.AndroidSpriteResources
import com.sagapmo.app.map.CampusLabel
import com.sagapmo.app.map.MapAttribution
import com.sagapmo.app.map.campusLandmarks
import com.sagapmo.app.reading.Reading
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.*
import org.maplibre.compose.overlay.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import java.util.Locale

@Composable internal fun LocationPicker(reading: Reading, baseStyle: BaseStyle, onDismiss: () -> Unit,
    onSave: suspend (Double, Double) -> Boolean) {
    val runtime = remember { createMapRuntime { resourceProvider = AndroidSpriteResources } }
    DisposableEffect(runtime) { onDispose { runtime.close() } }
    val map = rememberMapState(runtime = runtime, baseStyle = baseStyle,
        initialCameraPosition = CameraPosition(center = Position(reading.longitude ?: 121.07428, reading.latitude ?: 13.78421), zoom = 18.0))
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val position = map.cameraPosition.center
    val longitude = ((position.longitude + 180) % 360 + 360) % 360 - 180
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Set location", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onDismiss, enabled = !saving) { Text("Cancel") }
                }
                Text("Move the map to place the pin where this reading was taken.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    MaplibreMap(state = map) {
                        Box(Modifier.fillMaxSize().padding(8.dp)) { MapAttribution(map.style.attributions(), Modifier.align(Alignment.TopEnd)) }
                        if (map.style.loadState == StyleLoadState.Ready && map.cameraPosition.zoom >= 17.0) campusLandmarks.forEach {
                            CampusLabel(it, Modifier.placedAt(it.position, alignment = Alignment.Center))
                        }
                    }
                    AppIcon(AppSymbol.TARGET, "Selected location", Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(String.format(Locale.US, "%.5f, %.5f", position.latitude, longitude), style = MaterialTheme.typography.bodyMedium)
                    if (failed) Text("Couldn't save location. Try again.", color = MaterialTheme.colorScheme.error)
                    if (map.style.loadState != StyleLoadState.Ready && map.style.loadState !is StyleLoadState.Failed) Text("Loading map…", style = MaterialTheme.typography.bodySmall)
                    if (map.style.loadState is StyleLoadState.Failed) Text("Map unavailable. Check your connection and reopen this editor.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { saving = true; failed = false; scope.launch {
                        if (onSave(position.latitude, longitude)) onDismiss() else { failed = true; saving = false }
                    } }, enabled = !saving && map.style.loadState == StyleLoadState.Ready, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (saving) "Saving location…" else "Save location")
                    }
                }
            }
        }
    }
}
