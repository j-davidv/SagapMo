package com.sagapmo.app.reading

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Reading::class], version = 1, exportSchema = true)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
}
