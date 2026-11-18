package com.sagapmo.app.reading

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReadingDao {
    @Query("SELECT * FROM readings WHERE (:type IS NULL OR connectionType = :type) ORDER BY capturedAtEpochMillis DESC, id DESC")
    abstract fun observeReadings(type: ConnectionType?): Flow<List<Reading>>

    @Query("SELECT * FROM readings WHERE id = :id")
    abstract suspend fun getById(id: String): Reading?

    @Query("DELETE FROM readings WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("UPDATE readings SET latitude = :latitude, longitude = :longitude, accuracyMetres = NULL, locationAgeMillisAtCapture = NULL, noLocationReason = NULL, locationManuallySet = 1 WHERE id = :id")
    abstract suspend fun setManualLocation(id: String, latitude: Double, longitude: Double): Int

    @Query("UPDATE readings SET locationDescription = :description WHERE id = :id")
    abstract suspend fun setLocationDescription(id: String, description: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(reading: Reading): Long

    @Transaction
    open suspend fun save(reading: Reading): Reading {
        validateReading(reading)
        insertIfAbsent(reading)
        val persisted = requireNotNull(getById(reading.id))
        require(persisted == reading) { "A different reading already uses this operation ID" }
        return persisted
    }
}
