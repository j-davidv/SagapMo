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

fun locationAccessReason(access: LocationAccess): NoLocationReason? = when {
    !access.precise && !access.coarse -> NoLocationReason.PERMISSION_DENIED
    !access.precise -> NoLocationReason.APPROXIMATE_ONLY
    !access.servicesEnabled -> NoLocationReason.LOCATION_DISABLED
    else -> null
}

fun locationReason(fix: LocationFix, access: LocationAccess, atElapsedMillis: Long): NoLocationReason? {
    locationAccessReason(access)?.let { return it }
    if (!fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 ||
        !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0 ||
        fix.measuredElapsedMillis < 0 || fix.measuredElapsedMillis > atElapsedMillis ||
        (fix.accuracyMetres != null && (!fix.accuracyMetres.isFinite() || fix.accuracyMetres < 0))) {
        return NoLocationReason.LOCATION_UNAVAILABLE
    }
    if (atElapsedMillis - fix.measuredElapsedMillis > 30_000) return NoLocationReason.LOCATION_STALE
    if (fix.accuracyMetres == null || fix.accuracyMetres > 30f) return NoLocationReason.LOCATION_INACCURATE
    return null
}

fun assembleReading(id: String, sample: SignalSample, location: LocationResult, access: LocationAccess): Reading {
    val fix = (location as? LocationResult.Candidate)?.fix
    val reason = locationAccessReason(access) ?: when (location) {
        is LocationResult.Candidate -> locationReason(location.fix, access, sample.capturedElapsedMillis)
        is LocationResult.Missing -> location.reason
    }
    val eligible = fix?.takeIf { reason == null }
    return Reading(id, sample.capturedAtEpochMillis, sample.connectionType, sample.rawSignalValue,
        sample.signalUnit, sample.rating, sample.signalAgeMillisAtCapture,
        eligible?.latitude, eligible?.longitude, eligible?.accuracyMetres,
        eligible?.let { sample.capturedElapsedMillis - it.measuredElapsedMillis }, reason)
}
