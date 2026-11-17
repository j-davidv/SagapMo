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

class CampusMapScreenTest {
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)
    private val runtimes = mutableListOf<MapRuntime>()
    @After fun closeRuntimes() { compose.runOnUiThread { runtimes.forEach { it.close() } } }
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
        compose.onNodeWithTag("pin-test-wifi").assertHasClickAction().performClick()
        compose.onNodeWithText("Reading details").assertIsDisplayed()
        assertEquals(located, selected)
        compose.onNodeWithTag("pin-test-mobile").assertDoesNotExist()
        compose.onNodeWithText("OpenFreeMap · OpenMapTiles · © OpenStreetMap").assertExists()
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
    @Test fun glyphFailureExposesRecovery() = resourceFailure("glyph")
    @Test fun spriteFailureExposesRecovery() = resourceFailure("sprite")
    private fun resourceFailure(kind: String) {
        val provider = MapResourceProvider(accepts = { true }, load = { MapResourceLoad.Failed(MapResourceError.Server, "test $kind failure") })
        val style = resourceStyle(kind)
        compose.setContent { SagapMoTheme { CampusMapScreen(listOf(sampleReading()), null, {}, {}, baseStyle = style, resourceProvider = provider) } }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View readings").assertIsDisplayed()
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
        val localStyle = BaseStyle.Json("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#eaece6"}}]}""")
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
