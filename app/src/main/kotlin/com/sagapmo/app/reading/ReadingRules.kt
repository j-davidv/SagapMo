package com.sagapmo.app.reading

import com.sagapmo.app.capture.*

fun rateWifi(rssi: Int): Rating? = when {
    rssi !in -126..-1 -> null
    rssi >= -65 -> Rating.GOOD
    rssi >= -75 -> Rating.FAIR
    else -> Rating.POOR
}

fun rateMobile(level: Int, measuredElapsedMillis: Long, nowElapsedMillis: Long): Rating? {
    if (measuredElapsedMillis < 0 || measuredElapsedMillis > nowElapsedMillis ||
        nowElapsedMillis - measuredElapsedMillis > 60_000) return null
    return when (level) {
        1 -> Rating.POOR
        2 -> Rating.FAIR
        3, 4 -> Rating.GOOD
        else -> null
    }
}

fun connectionChange(before: ConnectionSnapshot, after: ConnectionResult): CaptureError? {
    val snapshot = (after as? ConnectionResult.Available)?.snapshot
    if (snapshot != before || (before.connectionType == ConnectionType.MOBILE &&
            (before.dataSubscriptionId == null || before.dataSubscriptionId < 0))) return CaptureError.CONNECTION_CHANGED
    return null
}
