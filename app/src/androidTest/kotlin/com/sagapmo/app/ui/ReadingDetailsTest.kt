package com.sagapmo.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sagapmo.app.sampleReading
import com.sagapmo.app.reading.Reading
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class ReadingDetailsTest {
    @get:Rule val compose = createComposeRule(effectContext = Dispatchers.Main)

    @Test fun downwardSwipeDismissesTallDetailsWithoutBouncing() {
        swipeToDismiss(sampleReading().copy(downloadMbps = 48.0, uploadMbps = 12.0, latencyMs = 20.0), true)
    }

    @Test fun downwardSwipeDismissesShortDetailsWithoutBouncing() {
        swipeToDismiss(sampleReading(), false)
    }

    @Test fun dragKeepsContentHeightStableAndDismissesOnRelease() {
        swipeToDismiss(sampleReading().copy(downloadMbps = 48.0, uploadMbps = 12.0, latencyMs = 20.0), true, true)
    }

    @Test fun downwardSwipeOnHandleDismissesDetails() {
        swipeToDismiss(sampleReading(), false, fromHandle = true)
    }

    private fun swipeToDismiss(reading: Reading, withEditor: Boolean, shortDragFirst: Boolean = false, fromHandle: Boolean = false) {
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            SagapMoTheme {
                if (visible) ReadingDetails(
                    reading,
                    onDismiss = { visible = false },
                    onShowOnMap = {},
                    onDescriptionChange = if (withEditor) ({}) else null,
                    onSetLocation = if (withEditor) ({}) else null,
                )
            }
        }
        if (shortDragFirst) {
            val initialHeight = compose.onNodeWithTag("reading-details").fetchSemanticsNode().size.height
            compose.onNodeWithTag("reading-details").performTouchInput {
                down(Offset(width * 0.5f, height * 0.1f))
                moveBy(Offset(0f, height * 0.15f), 1000)
            }
            assertEquals(initialHeight, compose.onNodeWithTag("reading-details").fetchSemanticsNode().size.height)
            compose.onNodeWithTag("reading-details").performTouchInput { up() }
            compose.onNodeWithTag("reading-details").assertDoesNotExist()
            return
        }
        if (fromHandle) {
            val distance = compose.onNodeWithTag("reading-details").fetchSemanticsNode().size.height * 0.85f
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performTouchInput {
                swipe(center, center + Offset(0f, distance), 1500)
            }
        } else {
            compose.onNodeWithTag("reading-details").performTouchInput {
                swipe(Offset(width * 0.5f, height * 0.1f), Offset(width * 0.5f, height * 0.95f), 1500)
            }
        }
        compose.onNodeWithTag("reading-details").assertDoesNotExist()
    }
}

