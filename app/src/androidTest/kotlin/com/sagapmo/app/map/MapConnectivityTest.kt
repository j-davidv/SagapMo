package com.sagapmo.app.map

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.sagapmo.app.sampleReading
import com.sagapmo.app.ui.SagapMoTheme
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.compose.resource.*

class MapConnectivityTest {
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)

    @Test fun twoDimensionalMapLeavesOfflineCoverageOnReconnect() = switchesCoverage(false)
    @Test fun threeDimensionalMapLeavesOfflineCoverageOnReconnect() = switchesCoverage(true)

    @Test fun switchingSourcesWhileLoadingRestartsTheTimeout() {
        var online by mutableStateOf(false)
        val requested = CompletableDeferred<Unit>()
        val onlineGate = CompletableDeferred<Unit>()
        val provider = MapResourceProvider(accepts = { true }, load = { request ->
            if (request.url.startsWith("sagapmo://campus/")) {
                requested.complete(Unit)
                awaitCancellation()
            }
            onlineGate.await()
            MapResourceLoad.Bytes(CampusMapScreenTest.localStyle.json.toByteArray())
        })
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(emptyList(), null, {}, {},
                    baseStyle = campusMapStyle(false, online), resourceProvider = provider)
            }
        }
        compose.waitUntil(5000) { requested.isCompleted }
        android.os.SystemClock.sleep(12_000)
        compose.runOnIdle { online = true }
        android.os.SystemClock.sleep(4_000)
        compose.onNodeWithTag("map-error").assertDoesNotExist()
        onlineGate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun switchesCoverage(use3d: Boolean) {
        var online by mutableStateOf(false)
        val reading = sampleReading().copy(latitude = 13.9, longitude = 121.2)
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val bundled = CampusMapResources { path -> assets.open(path).use { it.readBytes() } }
        val provider = MapResourceProvider(accepts = { true }, load = { request ->
            requests += request.url
            if (request.url.startsWith("sagapmo://campus/")) bundled.load(request)
            else MapResourceLoad.Bytes(CampusMapScreenTest.localStyle.json.toByteArray())
        })
        val notice = "Outside the offline campus map. Use Return to campus overview."
        compose.setContent {
            SagapMoTheme {
                CampusMapScreen(listOf(reading), reading.id, {}, {},
                    baseStyle = campusMapStyle(use3d, online), resourceProvider = provider)
            }
        }
        fun awaitReady() = compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("map-ready").fetchSemanticsNodes().isNotEmpty()
        }
        awaitReady()
        compose.onNodeWithText(notice).assertIsDisplayed()
        compose.onNodeWithTag("pin-test-wifi").assertIsDisplayed()
        repeat(2) {
            compose.runOnIdle { online = true }
            awaitReady()
            compose.onNodeWithText(notice).assertDoesNotExist()
            compose.onNodeWithTag("pin-test-wifi").assertIsDisplayed()
            assertTrue(requests.contains("https://tiles.openfreemap.org/styles/${if (use3d) "liberty" else "bright"}"))
            compose.runOnIdle { online = false }
            awaitReady()
            compose.onNodeWithText(notice).assertIsDisplayed()
            compose.onNodeWithTag("pin-test-wifi").assertIsDisplayed()
        }
    }
}
