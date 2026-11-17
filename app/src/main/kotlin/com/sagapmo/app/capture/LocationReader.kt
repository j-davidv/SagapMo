package com.sagapmo.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.sagapmo.app.reading.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

interface LocationReader {
    fun currentAccess(): LocationAccess
    suspend fun locate(): LocationResult
}

class AndroidLocationReader(private val context: Context) : LocationReader {
    private val manager = context.getSystemService(LocationManager::class.java)

    override fun currentAccess() = LocationAccess(
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        manager.isLocationEnabled,
    )

    @SuppressLint("MissingPermission")
    override suspend fun locate(): LocationResult {
        locationAccessReason(currentAccess())?.let { return LocationResult.Missing(it) }
        var best: LocationFix? = null
        var providerFailed = false
        fun rank(fix: LocationFix): Int = when (locationReason(fix, currentAccess(), SystemClock.elapsedRealtime())) {
            null -> 0
            NoLocationReason.LOCATION_INACCURATE -> 1
            NoLocationReason.LOCATION_STALE -> 2
            else -> 3
        }
        val qualifying = withTimeoutOrNull(10_000) {
            callbackFlow {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) { trySend(location.toFix()) }
                }
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
                var registered = 0
                for (provider in providers) {
                    try {
                        manager.getLastKnownLocation(provider)?.let { trySend(it.toFix()) }
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        registered++
                    } catch (_: SecurityException) { providerFailed = true }
                    catch (_: IllegalArgumentException) { providerFailed = true }
                }
                if (registered == 0) { providerFailed = true; close() }
                awaitClose { manager.removeUpdates(listener) }
            }.firstOrNull { candidate ->
                val previous = best
                if (previous == null || rank(candidate) < rank(previous) ||
                    (rank(candidate) == rank(previous) && candidate.measuredElapsedMillis > previous.measuredElapsedMillis)) best = candidate
                rank(candidate) == 0
            }
        }
        locationAccessReason(currentAccess())?.let { return LocationResult.Missing(it) }
        return (qualifying ?: best)?.let { LocationResult.Candidate(it) }
            ?: LocationResult.Missing(if (providerFailed) NoLocationReason.LOCATION_UNAVAILABLE else NoLocationReason.LOCATION_TIMEOUT)
    }

    private fun Location.toFix() = LocationFix(latitude, longitude, if (hasAccuracy()) accuracy else null, elapsedRealtimeNanos / 1_000_000)
}
