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

class CampusMapScreenTest {
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)
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
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("pin-distant").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pin-distant").assertIsDisplayed()
        compose.onNodeWithContentDescription("Return to campus overview").performClick()
    }
    companion object {
        val localStyle = BaseStyle.Json("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#eaece6"}}]}""")
    }
}
