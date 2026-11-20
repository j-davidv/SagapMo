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
    val eligible = fix?.takeIf {
        val quality = locationReason(it, LocationAccess(true, true, access.servicesEnabled), sample.capturedElapsedMillis)
        reason in setOf(null, NoLocationReason.LOCATION_INACCURATE, NoLocationReason.APPROXIMATE_ONLY) &&
            (quality == null || quality == NoLocationReason.LOCATION_INACCURATE)
    }
    return Reading(id, sample.capturedAtEpochMillis, sample.connectionType, sample.rawSignalValue,
        sample.signalUnit, sample.rating, sample.signalAgeMillisAtCapture,
        eligible?.latitude, eligible?.longitude, eligible?.accuracyMetres,
        eligible?.let { sample.capturedElapsedMillis - it.measuredElapsedMillis }, reason)
}

fun validateReading(reading: Reading) = with(reading) {
    require(locationDescription.length <= 200)
    require(id.isNotBlank() && capturedAtEpochMillis >= 0)
    if (connectionType == ConnectionType.WIFI) {
        require(signalUnit == SignalUnit.DBM && signalAgeMillisAtCapture == null && rateWifi(rawSignalValue) == rating)
    } else {
        require(signalUnit == SignalUnit.ANDROID_LEVEL && signalAgeMillisAtCapture != null &&
            signalAgeMillisAtCapture in 0..60_000 && rateMobile(rawSignalValue, 0, signalAgeMillisAtCapture) == rating)
    }
    if (latitude == null) {
        require(noLocationReason != null && !locationManuallySet)
        require(latitude == null && longitude == null && accuracyMetres == null && locationAgeMillisAtCapture == null)
    } else {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude != null && longitude.isFinite() && longitude in -180.0..180.0)
        if (locationManuallySet) require(noLocationReason == null && accuracyMetres == null && locationAgeMillisAtCapture == null)
        else {
            require(locationAgeMillisAtCapture != null && locationAgeMillisAtCapture in 0..30_000)
            val reason = locationReason(LocationFix(latitude, longitude, accuracyMetres, 0), LocationAccess(true, true, true), locationAgeMillisAtCapture)
            require((reason == null || reason == NoLocationReason.LOCATION_INACCURATE) &&
                (reason == noLocationReason || noLocationReason == NoLocationReason.APPROXIMATE_ONLY))
        }
    }
    downloadMbps?.let { require(it >= 0.0 && it.isFinite()) }
    uploadMbps?.let { require(it >= 0.0 && it.isFinite()) }
    latencyMs?.let { require(it >= 0.0 && it.isFinite()) }
    jitterMs?.let { require(it >= 0.0 && it.isFinite()) }
    wifiConnectedAgeMillis?.let { require(it >= 0) }
}
