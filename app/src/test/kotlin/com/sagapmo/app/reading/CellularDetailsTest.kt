package com.sagapmo.app.reading

import com.sagapmo.app.capture.*
import org.junit.Assert.*
import org.junit.Test

class CellularDetailsTest {
    @Test fun unavailableAndOutOfRangeValuesStayUnknown() {
        assertNull(normalizeCellularDetails(CellularDetails(
            carrierName = "  ", networkType = 0, lteRsrpDbm = Int.MAX_VALUE,
            lteRsrqDb = -35, lteRssnrDb = 31, lteRssiDbm = 0,
            nrSsRsrpDbm = -157, nrSsRsrqDb = 21, nrSsSinrDb = -24)))
        assertNull(normalizeCellularDetails(CellularDetails()))
    }

    @Test fun bothRadiosAndValidZeroValuesArePreserved() {
        val details = CellularDetails("Smart", 13, -100, -12, 0, -75, -95, 0, 0)
        assertEquals(details, normalizeCellularDetails(details.copy(carrierName = "  Smart  ")))
        assertEquals(CellularDetails(lteRsrpDbm = -140, lteRsrqDb = -34, lteRssnrDb = -20,
            lteRssiDbm = -113, nrSsRsrpDbm = -156, nrSsRsrqDb = -43, nrSsSinrDb = -23),
            normalizeCellularDetails(CellularDetails(lteRsrpDbm = -140, lteRsrqDb = -34, lteRssnrDb = -20,
                lteRssiDbm = -113, nrSsRsrpDbm = -156, nrSsRsrqDb = -43, nrSsSinrDb = -23)))
        val upper = CellularDetails(lteRsrpDbm = -43, lteRsrqDb = 3, lteRssnrDb = 30,
            lteRssiDbm = -51, nrSsRsrpDbm = -31, nrSsRsrqDb = 20, nrSsSinrDb = 40)
        assertEquals(upper, normalizeCellularDetails(upper))
    }

    @Test fun contextCanBeSavedWithoutAnyRadioMetrics() {
        val details = CellularDetails("Globe", 13)
        val sample = SignalSample(ConnectionType.MOBILE, 3, SignalUnit.ANDROID_LEVEL,
            Rating.GOOD, 1000, 5000, 100_000, cellularDetails = details)
        val reading = assembleReading("cellular", sample, LocationResult.Missing(NoLocationReason.LOCATION_DISABLED),
            LocationAccess(true, true, false))
        assertEquals(details, reading.cellularDetails)
        validateReading(reading)
        validateReading(reading.copy(cellularDetails = null))
        listOf(reading.copy(cellularDetails = details.copy(lteRsrpDbm = Int.MAX_VALUE)),
            reading.copy(connectionType = ConnectionType.WIFI, signalUnit = SignalUnit.DBM,
                rawSignalValue = -65, signalAgeMillisAtCapture = null)).forEach {
            try { validateReading(it); fail("Invalid cellular details accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
