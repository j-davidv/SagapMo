package com.sagapmo.app.map

enum class MapLoadStatus { LOADING, READY, FAILED }
data class MapLoadState(val attempt: Int, val status: MapLoadStatus)
enum class MapLoadEvent { STARTED, STYLE_FAILED, PARTIAL_FRAME, FULL_FRAME, DEADLINE }

fun nextMapLoadState(state: MapLoadState, event: MapLoadEvent, attempt: Int): MapLoadState {
    if (event == MapLoadEvent.STARTED && attempt > state.attempt) return MapLoadState(attempt, MapLoadStatus.LOADING)
    if (attempt != state.attempt) return state
    val status = when (event) {
        MapLoadEvent.FULL_FRAME -> MapLoadStatus.READY
        MapLoadEvent.STYLE_FAILED -> MapLoadStatus.FAILED
        MapLoadEvent.DEADLINE -> if (state.status == MapLoadStatus.LOADING) MapLoadStatus.FAILED else state.status
        MapLoadEvent.PARTIAL_FRAME -> if (state.status == MapLoadStatus.READY) MapLoadStatus.LOADING else state.status
        MapLoadEvent.STARTED -> state.status
    }
    return state.copy(status = status)
}
