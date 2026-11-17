package com.sagapmo.app.map

import org.junit.Assert.*
import org.junit.Test

class MapLoadStateTest {
    @Test fun deadlineChangesLoadingToFailed() {
        assertEquals(MapLoadStatus.FAILED, nextMapLoadState(MapLoadState(1, MapLoadStatus.LOADING), MapLoadEvent.DEADLINE, 1).status)
    }
    @Test fun lateFullFrameClearsTimeout() {
        assertEquals(MapLoadStatus.READY, nextMapLoadState(MapLoadState(1, MapLoadStatus.FAILED), MapLoadEvent.FULL_FRAME, 1).status)
    }
    @Test fun oldAttemptCannotReplaceRetryState() {
        val retry = MapLoadState(2, MapLoadStatus.LOADING)
        MapLoadEvent.entries.filter { it != MapLoadEvent.STARTED }.forEach { assertEquals(retry, nextMapLoadState(retry, it, 1)) }
        assertEquals(retry, nextMapLoadState(retry, MapLoadEvent.STARTED, 1))
        assertEquals(retry, nextMapLoadState(retry, MapLoadEvent.FULL_FRAME, 3))
        assertEquals(MapLoadState(3, MapLoadStatus.LOADING), nextMapLoadState(retry, MapLoadEvent.STARTED, 3))
    }
    @Test fun readyMapSurvivesOldDeadline() {
        val ready = MapLoadState(1, MapLoadStatus.READY)
        assertEquals(ready, nextMapLoadState(ready, MapLoadEvent.DEADLINE, 1))
    }
    @Test fun partialFrameChangesReadyToLoading() {
        val loading = nextMapLoadState(MapLoadState(1, MapLoadStatus.READY), MapLoadEvent.PARTIAL_FRAME, 1)
        assertEquals(MapLoadStatus.LOADING, loading.status)
        assertEquals(loading, nextMapLoadState(loading, MapLoadEvent.PARTIAL_FRAME, 1))
        val failed = MapLoadState(1, MapLoadStatus.FAILED)
        assertEquals(failed, nextMapLoadState(failed, MapLoadEvent.PARTIAL_FRAME, 1))
    }
    @Test fun styleFailureAndLateRecoveryAreBothVisible() {
        val failed = nextMapLoadState(MapLoadState(1, MapLoadStatus.LOADING), MapLoadEvent.STYLE_FAILED, 1)
        assertEquals(MapLoadStatus.FAILED, failed.status)
        assertEquals(MapLoadStatus.READY, nextMapLoadState(failed, MapLoadEvent.FULL_FRAME, 1).status)
    }
}
