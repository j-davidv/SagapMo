package com.sagapmo.app.capture

import android.telephony.TelephonyCallback
import android.telephony.SignalStrength
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyDisplayInfo.*
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.*
import com.sagapmo.app.reading.MobileGeneration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

@Suppress("DEPRECATION")
internal fun mobileGeneration(networkType: Int, overrideNetworkType: Int): MobileGeneration? = when {
    overrideNetworkType == OVERRIDE_NETWORK_TYPE_NR_NSA ||
        overrideNetworkType == OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
        networkType == NETWORK_TYPE_NR -> MobileGeneration.FIVE_G
    networkType == NETWORK_TYPE_LTE -> MobileGeneration.FOUR_G
    networkType in listOf(NETWORK_TYPE_UMTS, NETWORK_TYPE_EVDO_0, NETWORK_TYPE_EVDO_A,
        NETWORK_TYPE_EVDO_B, NETWORK_TYPE_HSDPA, NETWORK_TYPE_HSUPA, NETWORK_TYPE_HSPA,
        NETWORK_TYPE_EHRPD, NETWORK_TYPE_HSPAP, NETWORK_TYPE_TD_SCDMA) -> MobileGeneration.THREE_G
    networkType in listOf(NETWORK_TYPE_GPRS, NETWORK_TYPE_EDGE, NETWORK_TYPE_CDMA,
        NETWORK_TYPE_1xRTT, NETWORK_TYPE_IDEN, NETWORK_TYPE_GSM) -> MobileGeneration.TWO_G
    else -> null
}

internal suspend fun TelephonyManager.readMobileSignal(): Pair<SignalStrength?, TelephonyDisplayInfo?> {
    val signal = CompletableDeferred<SignalStrength>()
    val display = CompletableDeferred<TelephonyDisplayInfo>()
    val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.SignalStrengthsListener {
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            display.complete(info)
        }
        override fun onSignalStrengthsChanged(strength: SignalStrength) {
            signal.complete(strength)
        }
    }
    try {
        registerTelephonyCallback({ it.run() }, callback)
        withTimeoutOrNull(1_500) { signal.await(); display.await() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        // A timestamp-checked cached signal remains usable if callbacks are unavailable.
    } finally {
        try {
            unregisterTelephonyCallback(callback)
        } catch (_: RuntimeException) {
            // Optional context must not discard an otherwise valid signal reading.
        }
    }
    return (if (signal.isCompleted) signal.await() else signalStrength) to
        (if (display.isCompleted) display.await() else null)
}
