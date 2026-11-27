package com.sagapmo.app.map

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.Dispatchers
import com.sagapmo.app.*
import com.sagapmo.app.reading.Reading
import com.sagapmo.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.map.*
import org.maplibre.compose.resource.*
import kotlinx.coroutines.CompletableDeferred
import android.os.SystemClock
import java.util.UUID
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

class CampusMapScreenTest {
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)
    private val runtimes = mutableListOf<MapRuntime>()
    @After fun closeRuntimes() { compose.runOnUiThread { runtimes.forEach { it.close() } } }
    @Test fun campusLabelIsRenderedAtItsMapAnchorAfterPanAndZoom() {
        val landmark = campusLandmarks.first()
        lateinit var map: MapState
        compose.setContent {
            map = rememberMapState(baseStyle = localStyle,
                initialCameraPosition = CameraPosition(center = landmark.position, zoom = 18.0)) {
                CampusLabel(landmark)
            }
            MaplibreMap(state = map, modifier = Modifier.fillMaxSize())
        }
        fun renderedAtAnchor(): Boolean = runBlocking(Dispatchers.Main) {
            val anchor = map.screenLocationFromPosition(landmark.position) ?: return@runBlocking false
            map.queryRenderedFeatures(anchor, layerIds = setOf("campus-${landmark.name}")).isNotEmpty()
        }
        compose.waitUntil(10_000) { renderedAtAnchor() }
        compose.runOnIdle { map.panBy(DpOffset(35.dp, 25.dp)); map.scaleBy(1.5) }
        compose.waitUntil(5000) { map.cameraPosition.zoom > 18.0 && !map.isCameraMoving && renderedAtAnchor() }
        compose.runOnIdle { map.setCameraPosition(CameraPosition(center = landmark.position, zoom = 16.0)) }
        compose.waitUntil(5000) { map.cameraPosition.zoom < 17.0 && !renderedAtAnchor() }
    }
    @Test fun onlyLocatedRowsBecomePinsAndOpenStoredDetails() {
        val located = sampleReading()
        var selected by mutableStateOf<Reading?>(null)
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(listOf(located, mobileReading()), null, { selected = it }, {}, baseStyle = localStyle)
                selected?.let { ReadingDetails(it, { selected = null }, {}) }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("pin-test-wifi").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Test map attribution").assertDoesNotExist()
        compose.onNodeWithContentDescription("Map attribution").performClick()
        compose.onNodeWithText("Test map attribution").assertExists()
        compose.onNodeWithTag("pin-test-wifi").assertHasClickAction().performClick()
        compose.onNodeWithText("Reading details").assertIsDisplayed()
        assertEquals(located, selected)
        compose.onNodeWithTag("pin-test-mobile").assertDoesNotExist()
    }
    @Test fun readingPinIsRenderedAtItsSavedLocationAfterPanAndZoom() {
        val reading = sampleReading()
        val position = Position(reading.longitude!!, reading.latitude!!)
        lateinit var map: MapState
        compose.setContent {
            SagapMoTheme {
                val artwork = rememberPinArtwork(reading.connectionType, reading.rating)
                map = rememberMapState(baseStyle = localStyle,
                    initialCameraPosition = CameraPosition(center = position, zoom = 17.0)) {
                    ReadingPinLayer(reading.id, position, artwork)
                }
                MaplibreMap(state = map, modifier = Modifier.fillMaxSize())
            }
        }
        fun pinAnchor() = runBlocking(Dispatchers.Main) { map.screenLocationFromPosition(position) }
        fun pinIsRendered(): Boolean = runBlocking(Dispatchers.Main) {
            val anchor = map.screenLocationFromPosition(position) ?: return@runBlocking false
            map.queryRenderedFeatures(anchor, layerIds = setOf("pin-${reading.id}")).isNotEmpty()
        }
        compose.waitUntil(10_000) { pinIsRendered() }
        val initialAnchor = pinAnchor()
        compose.runOnIdle { map.panBy(DpOffset(35.dp, 25.dp)); map.scaleBy(1.5) }
        compose.waitUntil(5000) {
            map.cameraPosition.zoom > 17.0 && !map.isCameraMoving && pinAnchor() != initialAnchor && pinIsRendered()
        }
    }
    @Test fun focusMovesToSelectedGeographicReading() {
        val distant = sampleReading("distant").copy(latitude = 13.7900, longitude = 121.0800)
        compose.setContent { SagapMoTheme { CampusMapScreen(listOf(distant), "distant", {}, {}, baseStyle = localStyle) } }
        compose.waitUntil(10_000) { compose.onNodeWithTag("pin-distant").isDisplayed() }
        compose.onNodeWithTag("pin-distant").assertIsDisplayed()
        compose.onNodeWithContentDescription("Return to campus overview").performClick()
    }
    private fun runtime(provider: MapResourceProvider): MapRuntime = createMapRuntime { resourceProvider = provider }.also { runtimes += it }
    @Test fun styleFailureOffersRetryAndReadingsAndRecovers() {
        var fail = true
        val mapRuntime = runtime(MapResourceProvider(accepts = { true }, load = {
            if (fail) MapResourceLoad.Failed(MapResourceError.Server, "test style failure")
            else MapResourceLoad.Bytes(localStyle.json.toByteArray())
        }))
        var visible by mutableStateOf(true)
        var viewed = false
        val style = BaseStyle.Uri("sagapmo-test://${UUID.randomUUID()}/style")
        compose.setContent { SagapMoTheme { if (visible) CampusMapScreen(emptyList(), null, {}, { viewed = true }, mapRuntime, style) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View readings").performClick(); assertTrue(viewed)
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Retry map").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { visible = false }; compose.waitForIdle()
        compose.runOnIdle { visible = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Map could not load").assertDoesNotExist()
    }
    @Test fun tileFailureExposesRecovery() = resourceFailure("tile")
    @Test fun restoredInternetRetriesFailedMapWithoutTappingRetry() {
        var fail = true
        var recoveries by mutableIntStateOf(0)
        val provider = MapResourceProvider(accepts = { true }, load = {
            if (fail) MapResourceLoad.Failed(MapResourceError.Server, "offline")
            else MapResourceLoad.Bytes(localStyle.json.toByteArray())
        })
        val style = BaseStyle.Uri("sagapmo-test://${UUID.randomUUID()}/style")
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(listOf(sampleReading()), null, {}, {}, baseStyle = style,
                    resourceProvider = provider, networkRecoveryCount = recoveries)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Close map notice").performClick()
        compose.runOnIdle { fail = false; recoveries++ }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("map-error").assertDoesNotExist()
        compose.onNodeWithTag("pin-test-wifi").assertExists()
    }
    @Test fun restoredInternetDoesNotReloadAReadyMap() {
        var recoveries by mutableIntStateOf(0)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val provider = MapResourceProvider(accepts = { true }, load = {
            requests.incrementAndGet()
            MapResourceLoad.Bytes(localStyle.json.toByteArray())
        })
        val style = BaseStyle.Uri("sagapmo-test://${UUID.randomUUID()}/style")
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(emptyList(), null, {}, {}, baseStyle = style,
                    resourceProvider = provider, networkRecoveryCount = recoveries)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        val loadedRequests = requests.get()
        compose.runOnIdle { recoveries++ }
        compose.waitForIdle()
        compose.onNodeWithTag("map-ready").assertExists()
        assertEquals(loadedRequests, requests.get())
    }
    @Test fun reconnectDuringLoadingRetriesIfTheOldRequestFailsLate() {
        var recoveries by mutableIntStateOf(0)
        val gate = CompletableDeferred<Unit>()
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val provider = MapResourceProvider(accepts = { true }, load = {
            if (requests.incrementAndGet() == 1) {
                gate.await()
                MapResourceLoad.Failed(MapResourceError.Server, "late offline failure")
            } else MapResourceLoad.Bytes(localStyle.json.toByteArray())
        })
        val style = BaseStyle.Uri("sagapmo-test://${UUID.randomUUID()}/style")
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(emptyList(), null, {}, {}, baseStyle = style,
                    resourceProvider = provider, networkRecoveryCount = recoveries)
            }
        }
        compose.waitUntil(5000) { requests.get() == 1 }
        compose.runOnIdle { recoveries++ }
        compose.waitForIdle()
        gate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, requests.get())
    }
    @Test fun glyphFailureExposesRecovery() = resourceFailure("glyph")
    @Test fun spriteFailureExposesRecovery() = resourceFailure("sprite")
    private fun resourceFailure(kind: String) {
        val provider = MapResourceProvider(accepts = { true }, load = { MapResourceLoad.Failed(MapResourceError.Server, "test $kind failure") })
        val style = resourceStyle(kind)
        compose.setContent { SagapMoTheme { CampusMapScreen(listOf(sampleReading()), null, {}, {}, baseStyle = style, resourceProvider = provider) } }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View readings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close map notice").performClick()
        compose.onNodeWithTag("map-error").assertDoesNotExist()
        compose.onNodeWithTag("pin-test-wifi").assertExists()
    }
    @Test fun stalledMapExposesRetryAfter15SecondsAndLateFullFrameClearsIt() {
        val gate = CompletableDeferred<Unit>()
        val mapRuntime = runtime(MapResourceProvider(accepts = { true }, load = { gate.await(); MapResourceLoad.NoContent() }))
        val style = resourceStyle("tile")
        compose.setContent { SagapMoTheme { CampusMapScreen(emptyList(), null, {}, {}, mapRuntime, style) } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("map-loading").fetchSemanticsNodes().isNotEmpty() }
        val started = SystemClock.elapsedRealtime()
        repeat(6) {
            SystemClock.sleep(1900)
            compose.onNodeWithContentDescription("Return to campus overview").performClick()
        }
        compose.onNodeWithText("Retry map").assertDoesNotExist()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(SystemClock.elapsedRealtime() - started in 14_000..20_000)
        gate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Map could not load").assertDoesNotExist()
    }
    companion object {
        val localStyle = BaseStyle.Json("""{"version":8,"sources":{"credits":{"type":"geojson","attribution":"Test map attribution","data":{"type":"FeatureCollection","features":[]}}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#eaece6"}}]}""")
        fun resourceStyle(kind: String): BaseStyle {
            val prefix = "sagapmo-test://${UUID.randomUUID()}"
            val background = """{"id":"background","type":"background","paint":{"background-color":"#eaece6"}}"""
            return BaseStyle.Json(when (kind) {
                "tile" -> """{"version":8,"sources":{"tiles":{"type":"raster","tiles":["$prefix/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[$background,{"id":"tiles","type":"raster","source":"tiles"}]}"""
                "glyph" -> """{"version":8,"glyphs":"$prefix/{fontstack}/{range}.pbf","sources":{"point":{"type":"geojson","data":{"type":"Feature","geometry":{"type":"Point","coordinates":[121.07428,13.78421]},"properties":{}}}},"layers":[$background,{"id":"label","type":"symbol","source":"point","layout":{"text-field":"Campus","text-font":["Test Font"]}}]}"""
                else -> """{"version":8,"sprite":"$prefix/sprite","sources":{"point":{"type":"geojson","data":{"type":"Feature","geometry":{"type":"Point","coordinates":[121.07428,13.78421]},"properties":{}}}},"layers":[$background,{"id":"marker","type":"symbol","source":"point","layout":{"icon-image":"pin"}}]}"""
            })
        }
    }
}
