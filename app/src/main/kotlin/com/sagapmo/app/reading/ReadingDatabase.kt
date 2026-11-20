package com.sagapmo.app.reading

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Reading::class], version = 4, exportSchema = true)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readings ADD COLUMN downloadMbps REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE readings ADD COLUMN uploadMbps REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE readings ADD COLUMN latencyMs REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE readings ADD COLUMN jitterMs REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE readings ADD COLUMN speedTestStatus TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readings ADD COLUMN locationDescription TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readings ADD COLUMN locationManuallySet INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
