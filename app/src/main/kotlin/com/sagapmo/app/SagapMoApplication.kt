package com.sagapmo.app

import android.app.Application
import androidx.room.Room
import com.sagapmo.app.reading.ReadingDatabase

class SagapMoApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, ReadingDatabase::class.java, "sagapmo.db").build() }
}
