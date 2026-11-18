package com.sagapmo.app.reading

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

enum class ConnectionType { WIFI, MOBILE }
enum class SignalUnit { DBM, ANDROID_LEVEL }
enum class Rating { GOOD, FAIR, POOR }
enum class NoLocationReason {
    PERMISSION_DENIED, APPROXIMATE_ONLY, LOCATION_DISABLED, LOCATION_UNAVAILABLE,
    LOCATION_STALE, LOCATION_INACCURATE, LOCATION_TIMEOUT
}

@Entity(tableName = "readings")
data class Reading(
    @PrimaryKey val id: String,
    val capturedAtEpochMillis: Long,
    val connectionType: ConnectionType,
    val rawSignalValue: Int,
    val signalUnit: SignalUnit,
    val rating: Rating,
    val signalAgeMillisAtCapture: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMetres: Float?,
    val locationAgeMillisAtCapture: Long?,
    val noLocationReason: NoLocationReason?,
    @ColumnInfo(defaultValue = "0") val locationManuallySet: Boolean = false,
    @ColumnInfo(defaultValue = "''") val locationDescription: String = "",
)
