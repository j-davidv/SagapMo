package com.sagapmo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sagapmo.app.mobileReading
import com.sagapmo.app.sampleReading
import com.sagapmo.app.reading.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class CellularDetailsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(reading: Reading) {
        compose.setContent {
            SagapMoTheme {
                ReadingDetails(reading, onDismiss = {}, onShowOnMap = {}, alwaysCollapseAdvanced = false)
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("reading-details").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun carrierAndSeparateLteNrMetricsShowTheirUnitsAndValidZero() {
        show(mobileReading().copy(mobileGeneration = MobileGeneration.FIVE_G,
            cellularDetails = CellularDetails("Smart", 13, -102, -12, 0, -75, -96, -11, 17)))
        compose.onNodeWithText("Smart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LTE", substring = false).performScrollTo().assertIsDisplayed()
        listOf("LTE RSRP" to "-102 dBm", "LTE RSRQ" to "-12 dB", "LTE RSSNR" to "0 dB",
            "LTE RSSI" to "-75 dBm", "5G SS-RSRP" to "-96 dBm", "5G SS-RSRQ" to "-11 dB",
            "5G SS-SINR" to "17 dB").forEach { (label, value) ->
            compose.onNodeWithText(value).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test fun legacyMobileDetailsShowUnavailableWithoutInventingMetrics() {
        show(mobileReading())
        listOf("Carrier", "Radio type", "LTE RSRP", "LTE RSRQ", "LTE RSSNR", "LTE RSSI",
            "5G SS-RSRP", "5G SS-RSRQ", "5G SS-SINR").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        assertEquals(9, compose.onAllNodesWithText("Unavailable").fetchSemanticsNodes().size)
        compose.onNodeWithText("0 dB").assertDoesNotExist()
    }

    @Test fun wifiDetailsHaveNoCellularMetrics() {
        show(sampleReading())
        compose.onNodeWithText("Carrier").assertDoesNotExist()
        compose.onNodeWithText("Radio type").assertDoesNotExist()
        compose.onNodeWithText("LTE RSRP").assertDoesNotExist()
        compose.onNodeWithText("5G SS-RSRP").assertDoesNotExist()
    }

    @Test fun eachCellularMetricExplainsItsMeaningEvenWhenUnavailable() {
        show(mobileReading())
        listOf(
            "Signal age at capture" to "Age of the modem's signal report",
            "LTE RSRP" to "Reference Signal Received Power",
            "LTE RSRQ" to "Reference Signal Received Quality",
            "LTE RSSNR" to "Reference Signal Signal-to-Noise Ratio",
            "LTE RSSI" to "Received Signal Strength Indicator",
            "5G SS-RSRP" to "Synchronization Signal Reference Signal Received Power",
            "5G SS-RSRQ" to "Synchronization Signal Reference Signal Received Quality",
            "5G SS-SINR" to "Synchronization Signal-to-Interference-plus-Noise Ratio",
        ).forEach { (label, explanation) ->
            compose.onNodeWithContentDescription("About $label").performScrollTo()
            compose.onNodeWithContentDescription("About $label").performClick()
            compose.onNodeWithText("Got it").assertExists()
            compose.onNodeWithText(explanation, substring = true).assertIsDisplayed()
            compose.onNodeWithText("Got it").performScrollTo().performClick()
            compose.onNodeWithText("Got it").assertDoesNotExist()
        }
    }
}
