package com.sagapmo.app.capture

import com.sagapmo.app.reading.*
import org.junit.Assert.*
import org.junit.Test

class LocationRulesTest {
    private val access = LocationAccess(true, true, true)
    private val fix = LocationFix(13.78421, 121.07428, 30f, 70_000)
    @Test fun impreciseAndUnknownAccuracyKeepCoordinatesAndWarning() {
        val sample = SignalSample(ConnectionType.WIFI, -65, SignalUnit.DBM, Rating.GOOD, null, 1000, 100_000)
        for (accuracy in listOf(100f, null)) {
            val reading = assembleReading("approximate", sample, LocationResult.Candidate(fix.copy(accuracyMetres = accuracy)), access)
            assertEquals(fix.latitude, reading.latitude!!, 0.0)
            assertEquals(fix.longitude, reading.longitude!!, 0.0)
            assertEquals(accuracy, reading.accuracyMetres)
            assertEquals(NoLocationReason.LOCATION_INACCURATE, reading.noLocationReason)
            validateReading(reading)
        }
    }
    @Test fun coarsePermissionKeepsValidCoordinatesButNotInvalidOrStaleFixes() {
        val sample = SignalSample(ConnectionType.WIFI, -65, SignalUnit.DBM, Rating.GOOD, null, 1000, 100_000)
        val coarse = access.copy(precise = false)
        val reading = assembleReading("coarse", sample, LocationResult.Candidate(fix.copy(accuracyMetres = 1000f)), coarse)
        assertNotNull(reading.latitude)
        assertEquals(NoLocationReason.APPROXIMATE_ONLY, reading.noLocationReason)
        validateReading(reading)
        for (invalid in listOf(fix.copy(latitude = Double.NaN), fix.copy(measuredElapsedMillis = 0), fix.copy(accuracyMetres = -1f))) {
            val unlocated = assembleReading("invalid", sample, LocationResult.Candidate(invalid), coarse)
            assertNull(unlocated.latitude)
            validateReading(unlocated)
        }
    }
    @Test fun exactLocationLimitsQualify() {
        assertNull(locationReason(fix, access, 100_000))
        assertEquals(NoLocationReason.LOCATION_STALE, locationReason(fix, access, 100_001))
        assertEquals(NoLocationReason.LOCATION_INACCURATE, locationReason(fix.copy(accuracyMetres = 30.01f), access, 100_000))
    }
    @Test fun invalidCoordinatesAndTimestampsAreUnavailable() {
        listOf(fix.copy(latitude = Double.NaN), fix.copy(latitude = 91.0), fix.copy(longitude = -181.0),
            fix.copy(longitude = Double.POSITIVE_INFINITY), fix.copy(measuredElapsedMillis = -1),
            fix.copy(measuredElapsedMillis = 100_001), fix.copy(accuracyMetres = -1f),
            fix.copy(accuracyMetres = Float.NaN), fix.copy(accuracyMetres = Float.POSITIVE_INFINITY)).forEach {
            assertEquals(NoLocationReason.LOCATION_UNAVAILABLE, locationReason(it, access, 100_000))
        }
        assertEquals(NoLocationReason.LOCATION_INACCURATE, locationReason(fix.copy(accuracyMetres = null), access, 100_000))
    }
    @Test fun permissionAndServicesPrecedeCandidateQuality() {
        assertEquals(NoLocationReason.PERMISSION_DENIED, locationReason(fix, LocationAccess(false, false, false), 100_000))
        assertEquals(NoLocationReason.APPROXIMATE_ONLY, locationReason(fix, LocationAccess(false, true, false), 100_000))
        assertEquals(NoLocationReason.LOCATION_DISABLED, locationReason(fix, access.copy(servicesEnabled = false), 100_000))
    }
    @Test fun finalAttachmentUsesAcquisitionTimeAndKeepsSignal() {
        val sample = SignalSample(ConnectionType.WIFI, -65, SignalUnit.DBM, Rating.GOOD, null, 1000, 100_001)
        val reading = assembleReading("operation", sample, LocationResult.Candidate(fix), access)
        assertEquals(-65, reading.rawSignalValue)
        assertEquals(1000L, reading.capturedAtEpochMillis)
        assertEquals(NoLocationReason.LOCATION_STALE, reading.noLocationReason)
        assertNull(reading.latitude); assertNull(reading.longitude); assertNull(reading.accuracyMetres)
        assertNull(reading.locationAgeMillisAtCapture); assertNull(reading.signalAgeMillisAtCapture)
        val located = assembleReading("pin", sample.copy(capturedElapsedMillis = 100_000), LocationResult.Candidate(fix), access)
        assertEquals(13.78421, located.latitude!!, 0.0)
        assertEquals(30_000L, located.locationAgeMillisAtCapture)
        assertNull(located.noLocationReason)
        val revoked = assembleReading("revoked", sample, LocationResult.Missing(NoLocationReason.LOCATION_TIMEOUT), access.copy(precise = false, coarse = false))
        assertEquals(NoLocationReason.PERMISSION_DENIED, revoked.noLocationReason)
    }
}
