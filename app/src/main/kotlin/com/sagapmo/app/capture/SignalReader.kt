package com.sagapmo.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.sagapmo.app.reading.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SignalReader {
    fun currentConnection(): ConnectionResult
    suspend fun read(): SignalResult
}

class AndroidSignalReader(context: Context) : SignalReader {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private var lastNetworkHandle: Long? = null
    private var networkConnectedElapsedMillis: Long? = null

    override fun currentConnection(): ConnectionResult = try {
        val network = connectivity.activeNetwork
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
        val now = SystemClock.elapsedRealtime()
        val handle = network?.networkHandle
        if (handle != null && handle != lastNetworkHandle) {
            lastNetworkHandle = handle
            networkConnectedElapsedMillis = now
        } else if (handle == null) {
            lastNetworkHandle = null
            networkConnectedElapsedMillis = null
        }
        val connAge = networkConnectedElapsedMillis?.let { now - it }

        when {
            caps == null -> ConnectionResult.Failed(CaptureError.NO_CONNECTION)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionResult.Failed(CaptureError.VPN_ACTIVE)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionResult.Failed(CaptureError.UNSUPPORTED_CONNECTION)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                ConnectionResult.Failed(CaptureError.UNSUPPORTED_CONNECTION)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                ConnectionResult.Available(ConnectionSnapshot(network.networkHandle, ConnectionType.WIFI, null), connAge)
            else -> {
                val subscription = SubscriptionManager.getActiveDataSubscriptionId()
                if (!SubscriptionManager.isValidSubscriptionId(subscription)) ConnectionResult.Failed(CaptureError.SIGNAL_UNAVAILABLE)
                else ConnectionResult.Available(ConnectionSnapshot(network.networkHandle, ConnectionType.MOBILE, subscription), connAge)
            }
        }
    } catch (_: SecurityException) {
        ConnectionResult.Failed(CaptureError.SIGNAL_PERMISSION_DENIED)
    } catch (_: RuntimeException) {
        ConnectionResult.Failed(CaptureError.SIGNAL_UNAVAILABLE)
    }

    @SuppressLint("MissingPermission")
    override suspend fun read(): SignalResult = withContext(Dispatchers.IO) {
        val (before, connAge) = when (val result = currentConnection()) {
            is ConnectionResult.Available -> Pair(result.snapshot, result.connectionAgeMillis)
            is ConnectionResult.Failed -> return@withContext SignalResult.Failed(result.error)
        }
        try {
            val raw: Int
            val measured: Long?
            val unit: SignalUnit
            var generation: MobileGeneration? = null
            var cellularDetails: CellularDetails? = null
            if (before.connectionType == ConnectionType.WIFI) {
                val network = connectivity.activeNetwork
                val info = network?.let { connectivity.getNetworkCapabilities(it)?.transportInfo } as? WifiInfo
                    ?: return@withContext SignalResult.Failed(CaptureError.SIGNAL_UNAVAILABLE)
                raw = info.rssi
                measured = null
                unit = SignalUnit.DBM
            } else {
                val subscriptionTelephony = telephony.createForSubscriptionId(requireNotNull(before.dataSubscriptionId))
                val (strength, displayInfo) = subscriptionTelephony.readMobileSignal()
                if (strength == null) return@withContext SignalResult.Failed(CaptureError.SIGNAL_UNAVAILABLE)
                generation = displayInfo?.let { mobileGeneration(it.networkType, it.overrideNetworkType) }
                val carrierName = try { subscriptionTelephony.networkOperatorName } catch (_: RuntimeException) { null }
                cellularDetails = strength.cellularDetails(carrierName, displayInfo?.networkType)
                raw = strength.level
                measured = strength.timestampMillis
                unit = SignalUnit.ANDROID_LEVEL
            }
            val now = SystemClock.elapsedRealtime()
            val capturedAt = System.currentTimeMillis()
            connectionChange(before, currentConnection())?.let { return@withContext SignalResult.Failed(it) }
            val rating = if (unit == SignalUnit.DBM) rateWifi(raw) else rateMobile(raw, requireNotNull(measured), now)
            if (rating == null) {
                val error = when {
                    measured != null && (measured < 0 || measured > now || now - measured > 60_000) -> CaptureError.SIGNAL_STALE
                    unit == SignalUnit.ANDROID_LEVEL && raw == 0 -> CaptureError.SIGNAL_UNAVAILABLE
                    else -> CaptureError.SIGNAL_INVALID
                }
                SignalResult.Failed(error)
            } else SignalResult.Available(SignalSample(before.connectionType, raw, unit, rating,
                measured?.let { now - it }, capturedAt, now, connAge, generation, cellularDetails))
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            SignalResult.Failed(CaptureError.SIGNAL_PERMISSION_DENIED)
        } catch (_: RuntimeException) {
            SignalResult.Failed(CaptureError.SIGNAL_UNAVAILABLE)
        }
    }
}
