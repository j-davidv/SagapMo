package com.sagapmo.app.reading

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sagapmo.app.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ReadingDatabase
    private val dao get() = database.readings()
    private fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, "readings-test.db")
        .addMigrations(ReadingDatabase.MIGRATION_1_2, ReadingDatabase.MIGRATION_2_3, ReadingDatabase.MIGRATION_3_4, ReadingDatabase.MIGRATION_4_5, ReadingDatabase.MIGRATION_5_6, ReadingDatabase.MIGRATION_6_7)
        .build()
    @Before fun setUp() { context.deleteDatabase("readings-test.db"); database = open() }
    @After fun tearDown() { database.close(); context.deleteDatabase("readings-test.db") }
    @Test fun versionSixMigrationKeepsContextAndCellularMetricsPersistAcrossReopen() = runBlocking {
        database.close(); context.deleteDatabase("readings-test.db")
        context.openOrCreateDatabase("readings-test.db", Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE readings (id TEXT NOT NULL PRIMARY KEY, capturedAtEpochMillis INTEGER NOT NULL, connectionType TEXT NOT NULL, rawSignalValue INTEGER NOT NULL, signalUnit TEXT NOT NULL, rating TEXT NOT NULL, signalAgeMillisAtCapture INTEGER, latitude REAL, longitude REAL, accuracyMetres REAL, locationAgeMillisAtCapture INTEGER, noLocationReason TEXT, locationManuallySet INTEGER NOT NULL DEFAULT 0, locationDescription TEXT NOT NULL DEFAULT '', downloadMbps REAL, uploadMbps REAL, latencyMs REAL, jitterMs REAL, speedTestStatus TEXT, wifiConnectedAgeMillis INTEGER, mobileGeneration TEXT)")
            old.execSQL("INSERT INTO readings (id,capturedAtEpochMillis,connectionType,rawSignalValue,signalUnit,rating,signalAgeMillisAtCapture,noLocationReason,mobileGeneration,locationDescription) VALUES ('v6',1000,'MOBILE',3,'ANDROID_LEVEL','GOOD',1000,'PERMISSION_DENIED','FIVE_G','Library')")
            old.version = 6
        }
        database = open()
        val migrated = requireNotNull(dao.getById("v6"))
        assertNull(migrated.cellularDetails)
        assertEquals(MobileGeneration.FIVE_G, migrated.mobileGeneration)
        assertEquals("Library", migrated.locationDescription)
        val detailed = migrated.copy(id = "detailed", cellularDetails = CellularDetails(
            "Smart", 13, -100, -12, 0, -75, -95, -10, 15))
        val partial = migrated.copy(id = "partial", cellularDetails = CellularDetails(carrierName = "Globe"))
        dao.save(detailed); dao.save(partial)
        database.close(); database = open()
        assertEquals(migrated, dao.getById("v6"))
        assertEquals(detailed, dao.getById(detailed.id))
        assertEquals(partial, dao.getById(partial.id))
    }

    @Test fun versionFiveMigrationPreservesUnknownContextAndNewGenerationsSurviveReopen() = runBlocking {
        database.close()
        context.deleteDatabase("readings-test.db")
        context.openOrCreateDatabase("readings-test.db", Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE readings (id TEXT NOT NULL PRIMARY KEY, capturedAtEpochMillis INTEGER NOT NULL, connectionType TEXT NOT NULL, rawSignalValue INTEGER NOT NULL, signalUnit TEXT NOT NULL, rating TEXT NOT NULL, signalAgeMillisAtCapture INTEGER, latitude REAL, longitude REAL, accuracyMetres REAL, locationAgeMillisAtCapture INTEGER, noLocationReason TEXT, locationManuallySet INTEGER NOT NULL DEFAULT 0, locationDescription TEXT NOT NULL DEFAULT '', downloadMbps REAL, uploadMbps REAL, latencyMs REAL, jitterMs REAL, speedTestStatus TEXT, wifiConnectedAgeMillis INTEGER)")
            old.execSQL("INSERT INTO readings (id,capturedAtEpochMillis,connectionType,rawSignalValue,signalUnit,rating,signalAgeMillisAtCapture,noLocationReason,locationDescription,downloadMbps) VALUES ('v5',1000,'MOBILE',3,'ANDROID_LEVEL','GOOD',1000,'PERMISSION_DENIED','Library',45.5)")
            old.version = 5
        }
        database = open()
        val migrated = requireNotNull(dao.getById("v5"))
        assertNull(migrated.mobileGeneration)
        assertEquals("Library", migrated.locationDescription)
        assertEquals(45.5, migrated.downloadMbps!!, 0.0)
        validateReading(migrated)
        val readings = MobileGeneration.entries.map { migrated.copy(id = it.name, mobileGeneration = it) }
        readings.forEach { dao.save(it) }
        database.close(); database = open()
        assertEquals(migrated, dao.getById("v5"))
        readings.forEach { assertEquals(it, dao.getById(it.id)) }
    }

    @Test fun versionThreeMigrationPreservesReadingsAndAddsSpeedTestColumns() = runBlocking {
        database.close()
        context.deleteDatabase("readings-test.db")
        context.openOrCreateDatabase("readings-test.db", Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE readings (id TEXT NOT NULL PRIMARY KEY, capturedAtEpochMillis INTEGER NOT NULL, connectionType TEXT NOT NULL, rawSignalValue INTEGER NOT NULL, signalUnit TEXT NOT NULL, rating TEXT NOT NULL, signalAgeMillisAtCapture INTEGER, latitude REAL, longitude REAL, accuracyMetres REAL, locationAgeMillisAtCapture INTEGER, noLocationReason TEXT, locationManuallySet INTEGER NOT NULL DEFAULT 0, locationDescription TEXT NOT NULL DEFAULT '')")
            old.execSQL("INSERT INTO readings (id,capturedAtEpochMillis,connectionType,rawSignalValue,signalUnit,rating,noLocationReason,locationDescription) VALUES ('v3',1000,'WIFI',-65,'DBM','GOOD','PERMISSION_DENIED','Library')")
            old.version = 3
        }
        database = open()
        val migrated = requireNotNull(dao.getById("v3"))
        validateReading(migrated)
        assertEquals(NoLocationReason.PERMISSION_DENIED, migrated.noLocationReason)
        assertEquals("Library", migrated.locationDescription)
        assertNull(migrated.downloadMbps)
        assertNull(migrated.uploadMbps)
        assertNull(migrated.latencyMs)
        assertNull(migrated.jitterMs)
        assertNull(migrated.speedTestStatus)

        val speedReading = migrated.copy(id = "speed-reading", downloadMbps = 45.5, uploadMbps = 12.3, latencyMs = 15.0, jitterMs = 2.1, speedTestStatus = com.sagapmo.app.speedtest.SpeedTestStatus.COMPLETED)
        validateReading(speedReading)
        dao.save(speedReading)
        database.close(); database = open()
        assertEquals(migrated, dao.getById("v3"))
        val reloaded = requireNotNull(dao.getById(speedReading.id))
        assertEquals(45.5, reloaded.downloadMbps!!, 0.01)
        assertEquals(12.3, reloaded.uploadMbps!!, 0.01)
        assertEquals(15.0, reloaded.latencyMs!!, 0.01)
        assertEquals(2.1, reloaded.jitterMs!!, 0.01)
        assertEquals(com.sagapmo.app.speedtest.SpeedTestStatus.COMPLETED, reloaded.speedTestStatus)
    }

    @Test fun versionTwoMigrationPreservesPinAndDescriptionsPersistAcrossReopen() = runBlocking {
        database.close(); context.deleteDatabase("readings-test.db")
        context.openOrCreateDatabase("readings-test.db", Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE readings (id TEXT NOT NULL PRIMARY KEY, capturedAtEpochMillis INTEGER NOT NULL, connectionType TEXT NOT NULL, rawSignalValue INTEGER NOT NULL, signalUnit TEXT NOT NULL, rating TEXT NOT NULL, signalAgeMillisAtCapture INTEGER, latitude REAL, longitude REAL, accuracyMetres REAL, locationAgeMillisAtCapture INTEGER, noLocationReason TEXT, locationManuallySet INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("INSERT INTO readings (id,capturedAtEpochMillis,connectionType,rawSignalValue,signalUnit,rating,latitude,longitude,locationManuallySet) VALUES ('v2',1000,'WIFI',-65,'DBM','GOOD',13.78421,121.07428,1)")
            old.version = 2
        }
        database = open()
        val original = requireNotNull(dao.getById("v2"))
        assertEquals("", original.locationDescription)
        assertEquals(1, dao.setLocationDescription(original.id, "Beside the entrance"))
        database.close(); database = open()
        assertEquals(original.copy(locationDescription = "Beside the entrance"), dao.getById(original.id))
        assertEquals(1, dao.setManualLocation(original.id, 13.785, 121.075))
        assertEquals("Beside the entrance", dao.getById(original.id)!!.locationDescription)
        assertEquals(1, dao.setLocationDescription(original.id, ""))
        assertEquals("", dao.getById(original.id)!!.locationDescription)
    }

    @Test fun deletePersistsAcrossReopenAndUndoRestoresOriginal() = runBlocking {
        val original = sampleReading("delete-me")
        val other = sampleReading("keep-me")
        dao.save(original); dao.save(other)
        dao.deleteById(original.id)
        database.close(); database = open()
        assertNull(dao.getById(original.id))
        assertEquals(other, dao.getById(other.id))
        dao.save(original)
        assertEquals(original, dao.getById(original.id))
    }

    @Test fun migrationKeepsOldReadingsAndManualEditPersists() = runBlocking {
        database.close()
        context.deleteDatabase("readings-test.db")
        context.openOrCreateDatabase("readings-test.db", Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("""CREATE TABLE IF NOT EXISTS `readings` (`id` TEXT NOT NULL, `capturedAtEpochMillis` INTEGER NOT NULL, `connectionType` TEXT NOT NULL, `rawSignalValue` INTEGER NOT NULL, `signalUnit` TEXT NOT NULL, `rating` TEXT NOT NULL, `signalAgeMillisAtCapture` INTEGER, `latitude` REAL, `longitude` REAL, `accuracyMetres` REAL, `locationAgeMillisAtCapture` INTEGER, `noLocationReason` TEXT, PRIMARY KEY(`id`))""")
            old.execSQL("INSERT INTO readings (id,capturedAtEpochMillis,connectionType,rawSignalValue,signalUnit,rating,noLocationReason) VALUES ('legacy',1000,'WIFI',-65,'DBM','GOOD','LOCATION_INACCURATE')")
            old.version = 1
        }
        database = open()
        val legacy = requireNotNull(dao.getById("legacy"))
        assertFalse(legacy.locationManuallySet)
        assertNull(legacy.latitude)
        assertEquals(1, dao.setManualLocation(legacy.id, 13.78421, 121.07428))
        database.close(); database = open()
        val updated = requireNotNull(dao.getById(legacy.id))
        assertTrue(updated.locationManuallySet)
        assertEquals(legacy.rawSignalValue, updated.rawSignalValue)
        assertEquals(legacy.capturedAtEpochMillis, updated.capturedAtEpochMillis)
        assertNull(updated.accuracyMetres)
        assertNull(updated.noLocationReason)
        validateReading(updated)
        assertEquals(0, dao.setManualLocation("missing", 13.0, 121.0))
    }
    @Test fun saveRetryIsIdempotentAndConflictPreservesOriginal() = runBlocking {
        val original = sampleReading("same-operation")
        assertEquals(original, dao.save(original)); assertEquals(original, dao.save(original))
        assertEquals(1, dao.observeReadings(null).first().size)
        try { dao.save(original.copy(rawSignalValue = -70, rating = Rating.FAIR)); fail("Conflicting ID accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(original, dao.getById(original.id))
    }
    @Test fun reopenKeepsLocatedAndUnlocatedReadings() = runBlocking {
        dao.save(sampleReading()); dao.save(mobileReading())
        database.close(); database = open()
        assertEquals(sampleReading(), dao.getById("test-wifi"))
        assertEquals(mobileReading(), dao.getById("test-mobile"))
        database.openHelper.readableDatabase.query("SELECT connectionType,rating FROM readings WHERE id='test-wifi'").use {
            assertTrue(it.moveToFirst()); assertEquals("WIFI", it.getString(0)); assertEquals("GOOD", it.getString(1))
        }
    }
    @Test fun filtersAndTimestampTiesAreDeterministic() = runBlocking {
        dao.save(sampleReading("a")); dao.save(sampleReading("z")); dao.save(mobileReading())
        assertEquals(listOf("z", "a", "test-mobile"), dao.observeReadings(null).first().map { it.id })
        assertEquals(2, dao.observeReadings(ConnectionType.WIFI).first().size)
        assertEquals(listOf("test-mobile"), dao.observeReadings(ConnectionType.MOBILE).first().map { it.id })
    }
    @Test fun invalidRowsAreRejected() = runBlocking {
        listOf(sampleReading().copy(latitude = null), sampleReading().copy(rating = Rating.POOR),
            sampleReading().copy(signalAgeMillisAtCapture = 0), mobileReading().copy(signalAgeMillisAtCapture = 60_001),
            mobileReading().copy(rawSignalValue = 0), sampleReading().copy(accuracyMetres = Float.NaN),
            sampleReading().copy(locationAgeMillisAtCapture = -1)).forEach {
            try { dao.save(it); fail("Invalid reading saved: $it") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(dao.observeReadings(null).first().isEmpty())
    }
    @Test fun concurrentIdenticalSavesCreateOneRow() = runBlocking {
        coroutineScope { (1..10).map { async(Dispatchers.IO) { dao.save(sampleReading()) } }.awaitAll() }
        assertEquals(1, dao.observeReadings(null).first().size)
    }
}
