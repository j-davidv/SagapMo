package com.sagapmo.app.reading

import com.sagapmo.app.capture.*
import org.junit.Assert.*
import org.junit.Test

class ReadingRulesTest {
    @Test fun assembledReadingKeepsMobileContextWithoutChangingSignal() {
        val sample = SignalSample(ConnectionType.MOBILE, 3, SignalUnit.ANDROID_LEVEL,
            Rating.GOOD, 1000, 5000, 100_000, mobileGeneration = MobileGeneration.FIVE_G)
        val reading = assembleReading("5g", sample, LocationResult.Missing(NoLocationReason.LOCATION_DISABLED),
            LocationAccess(true, true, false))
        assertEquals(MobileGeneration.FIVE_G, reading.mobileGeneration)
        assertEquals(3, reading.rawSignalValue)
        assertEquals(Rating.GOOD, reading.rating)
        validateReading(reading)
        validateReading(reading.copy(mobileGeneration = null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wifiCannotHaveMobileContext() {
        val sample = SignalSample(ConnectionType.WIFI, -65, SignalUnit.DBM, Rating.GOOD, null, 5000, 100_000)
        val reading = assembleReading("wifi", sample, LocationResult.Missing(NoLocationReason.LOCATION_DISABLED),
            LocationAccess(true, true, false))
        validateReading(reading.copy(mobileGeneration = MobileGeneration.FOUR_G))
    }

    @Test fun wifiBoundaries() {
        assertEquals(Rating.GOOD, rateWifi(-65))
        assertEquals(Rating.FAIR, rateWifi(-66))
        assertEquals(Rating.FAIR, rateWifi(-75))
        assertEquals(Rating.POOR, rateWifi(-76))
        listOf(Int.MIN_VALUE, -128, -127, 0, 1).forEach { assertNull(rateWifi(it)) }
    }
    @Test fun mobileBoundariesUseElapsedTime() {
        listOf(1 to Rating.POOR, 2 to Rating.FAIR, 3 to Rating.GOOD, 4 to Rating.GOOD).forEach {
            assertEquals(it.second, rateMobile(it.first, 40_000, 100_000))
        }
        listOf(0, 5, -1).forEach { assertNull(rateMobile(it, 40_000, 100_000)) }
        listOf(39_999L, 100_001L, -1L).forEach { assertNull(rateMobile(3, it, 100_000)) }
    }
    @Test fun changedOrDisappearedConnectionFails() {
        val wifi = ConnectionSnapshot(1, ConnectionType.WIFI, null)
        assertNull(connectionChange(wifi, ConnectionResult.Available(wifi)))
        assertEquals(CaptureError.CONNECTION_CHANGED, connectionChange(wifi, ConnectionResult.Available(wifi.copy(networkHandle = 2))))
        assertEquals(CaptureError.CONNECTION_CHANGED, connectionChange(wifi, ConnectionResult.Failed(CaptureError.NO_CONNECTION)))
        val mobile = ConnectionSnapshot(1, ConnectionType.MOBILE, 3)
        listOf(mobile.copy(dataSubscriptionId = 4), mobile.copy(dataSubscriptionId = -1), mobile.copy(dataSubscriptionId = null)).forEach {
            assertEquals(CaptureError.CONNECTION_CHANGED, connectionChange(mobile, ConnectionResult.Available(it)))
        }
        assertEquals(CaptureError.CONNECTION_CHANGED, connectionChange(wifi, ConnectionResult.Available(mobile)))
    }
}
