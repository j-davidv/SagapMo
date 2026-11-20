package com.sagapmo.app.capture

import com.sagapmo.app.reading.*

enum class CaptureError {
    NO_CONNECTION, UNSUPPORTED_CONNECTION, VPN_ACTIVE, CONNECTION_CHANGED,
    SIGNAL_UNAVAILABLE, SIGNAL_INVALID, SIGNAL_STALE, SIGNAL_PERMISSION_DENIED
}
data class SignalSample(
    val connectionType: ConnectionType,
    val rawSignalValue: Int,
    val signalUnit: SignalUnit,
    val rating: Rating,
    val signalAgeMillisAtCapture: Long?,
    val capturedAtEpochMillis: Long,
    val capturedElapsedMillis: Long,
    val connectionAgeMillis: Long? = null,
)
sealed interface SignalResult {
    data class Available(val sample: SignalSample) : SignalResult
    data class Failed(val error: CaptureError) : SignalResult
}
data class ConnectionSnapshot(val networkHandle: Long, val connectionType: ConnectionType, val dataSubscriptionId: Int?)
sealed interface ConnectionResult {
    data class Available(val snapshot: ConnectionSnapshot, val connectionAgeMillis: Long? = null) : ConnectionResult
    data class Failed(val error: CaptureError) : ConnectionResult
}

data class LocationAccess(val precise: Boolean, val coarse: Boolean, val servicesEnabled: Boolean)
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMetres: Float?, val measuredElapsedMillis: Long)
sealed interface LocationResult {
    data class Candidate(val fix: LocationFix) : LocationResult
    data class Missing(val reason: NoLocationReason) : LocationResult
}
