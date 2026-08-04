package com.michelelopsdev.gfa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProcessedEmailEntity::class, TriagedEmailEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao
}
