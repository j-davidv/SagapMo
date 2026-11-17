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
    private fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, "readings-test.db").build()
    @Before fun setUp() { context.deleteDatabase("readings-test.db"); database = open() }
    @After fun tearDown() { database.close(); context.deleteDatabase("readings-test.db") }

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
