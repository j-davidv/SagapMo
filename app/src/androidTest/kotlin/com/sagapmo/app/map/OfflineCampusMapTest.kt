package com.sagapmo.app.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.sagapmo.app.sampleReading
import com.sagapmo.app.ui.SagapMoTheme
import com.sagapmo.app.ui.LocationPicker
import java.io.File
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.*
import org.maplibre.compose.resource.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ExperimentalMaplibreComposeApi
import org.maplibre.spatialk.geojson.Position

@OptIn(ExperimentalMaplibreComposeApi::class)
class OfflineCampusMapTest {
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)

    @Test fun twoDimensionalCampusMapLoadsWithoutNetworkOrPriorCache() = rendersOffline(DefaultMapStyle2D)
    @Test fun threeDimensionalCampusMapLoadsWithoutNetworkOrPriorCache() = rendersOffline(MapStyle3D)

    @Test fun bundledBuildingsAndPinsRenderAfterPanZoomAndStyleSwitch() {
        val reading = sampleReading()
        val position = Position(reading.longitude!!, reading.latitude!!)
        lateinit var map: MapState
        var threeDimensional by mutableStateOf(false)
        compose.setContent {
            SagapMoTheme {
                val resources = rememberCampusMapResources()
                val runtime = remember { createMapRuntime { resourceProvider = resources; maximumCacheSizeBytes = 0 } }
                DisposableEffect(runtime) { onDispose { runtime.close() } }
                val artwork = rememberPinArtwork(reading.connectionType, reading.rating)
                map = rememberMapState(runtime = runtime, baseStyle = if (threeDimensional) MapStyle3D else DefaultMapStyle2D,
                    initialCameraPosition = CameraPosition(center = position, zoom = 17.0)) {
                    campusLandmarks.forEach { CampusLabel(it) }
                    ReadingPinLayer(reading.id, position, artwork)
                }
                MaplibreMap(state = map, modifier = Modifier.fillMaxSize())
            }
        }
        fun features(layer: String) = runBlocking(Dispatchers.Main) {
            withTimeout(3000) { map.queryRenderedFeatures(DpRect(0.dp, 0.dp, 2000.dp, 2000.dp), layerIds = setOf(layer)) }
        }
        fun pinAtAnchor() = runBlocking(Dispatchers.Main) {
            val anchor = map.screenLocationFromPosition(position) ?: return@runBlocking false
            map.queryRenderedFeatures(anchor, layerIds = setOf("pin-${reading.id}")).isNotEmpty()
        }
        compose.waitUntil(20_000) { map.style.loadState == StyleLoadState.Ready }
        compose.waitUntil(20_000) { features("building").isNotEmpty() && pinAtAnchor() }
        compose.runOnIdle { map.panBy(DpOffset(35.dp, 25.dp)); map.scaleBy(1.5) }
        compose.waitUntil(10_000) { !map.isCameraMoving && map.cameraPosition.zoom > 17.0 && pinAtAnchor() }
        compose.runOnIdle {
            threeDimensional = true
            map.setCameraPosition(CameraPosition(center = position, zoom = 18.0, pitch = 45.0))
        }
        compose.waitUntil(20_000) { features("building-3d").isNotEmpty() && pinAtAnchor() }
    }

    @Test fun defaultLocationPickerSavesFromBundledMap() {
        var saved: Pair<Double, Double>? = null
        var dismissed = false
        compose.setContent {
            SagapMoTheme {
                LocationPicker(sampleReading(), onDismiss = { dismissed = true }, onSave = { latitude, longitude ->
                    saved = latitude to longitude
                    true
                })
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Save location") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save location").performClick()
        compose.waitUntil(5000) { dismissed }
        val coordinates = requireNotNull(saved)
        assertEquals(13.78422, coordinates.first, 0.00001)
        assertEquals(121.07431, coordinates.second, 0.00001)
    }

    @Test fun missingBundledStyleOffersRecovery() {
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(emptyList(), null, {}, {}, baseStyle = BaseStyle.Uri("sagapmo://campus/styles/missing-${UUID.randomUUID()}.json"))
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View readings").assertIsDisplayed()
    }

    @Test fun locationPickerCanReturnFromOutsideCampus() {
        var saved: Pair<Double, Double>? = null
        compose.setContent {
            SagapMoTheme {
                LocationPicker(sampleReading().copy(latitude = 13.9, longitude = 121.2), onDismiss = {},
                    onSave = { latitude, longitude -> saved = latitude to longitude; true })
            }
        }
        compose.onNodeWithText("Return to campus").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Save location") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save location").performClick()
        compose.waitUntil(5000) { saved != null }
        val coordinates = requireNotNull(saved)
        assertEquals(13.78421, coordinates.first, 0.00001)
        assertEquals(121.07428, coordinates.second, 0.00001)
    }

    @Test fun readingOutsideBundledAreaExplainsCoverageAndCanReturnToCampus() {
        val reading = sampleReading().copy(latitude = 13.9, longitude = 121.2)
        compose.setContent { SagapMoTheme { CampusMapScreen(listOf(reading), reading.id, {}, {}) } }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Outside the offline campus map. Use Return to campus overview.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Return to campus overview").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Outside the offline campus map. Use Return to campus overview.").fetchSemanticsNodes().isEmpty() }
    }

    private fun rendersOffline(style: BaseStyle) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val externalRequests = Collections.synchronizedList(mutableListOf<String>())
        val loaded = Collections.synchronizedList(mutableListOf<String>())
        val cache = File(context.cacheDir, "offline-test-${UUID.randomUUID()}.db")
        val assets = CampusMapResources { path ->
            context.assets.open(path).use { it.readBytes() }.also { loaded += path.removePrefix("campus-map/") }
        }
        val provider = MapResourceProvider(accepts = { true }, load = { request ->
            if (!request.url.startsWith("sagapmo://campus/")) {
                externalRequests += request.url
                MapResourceLoad.Failed(MapResourceError.Connection, "Network is disabled in this test")
            } else {
                assets.load(request)
            }
        })
        val reading = sampleReading()
        compose.setContent {
            SagapMoTheme {
                val runtime = remember { createMapRuntime { cacheFile = Path(cache.absolutePath); resourceProvider = provider } }
                DisposableEffect(runtime) { onDispose { runtime.close() } }
                CampusMapScreen(listOf(reading), null, {}, {}, runtime = runtime, baseStyle = style)
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Unexpected network requests: $externalRequests", externalRequests.isEmpty())
        assertTrue("No campus vector tiles loaded: $loaded", loaded.any { it.startsWith("tiles/") })
        assertTrue("No bundled label fonts loaded: $loaded", loaded.any { it.startsWith("fonts/") })
        assertTrue("No bundled map icons loaded: $loaded", loaded.any { it.startsWith("sprites/") && it.endsWith(".png") })
        compose.onNodeWithTag("pin-${reading.id}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Map attribution").performClick()
        compose.onNodeWithText("OpenFreeMap", substring = true).assertExists()
        compose.onNodeWithContentDescription("Return to campus overview").performClick()
        compose.onNodeWithTag("map-error").assertDoesNotExist()
    }
}
