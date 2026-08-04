package com.michelelopsdev.gfa.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

object DatabaseFactory {
    private var instance: AppDatabase? = null

    fun createDatabase(): AppDatabase {
        if (instance == null) {
            val dbFile = File(System.getProperty("user.home"), ".gfa/gfa_database.db")
            dbFile.parentFile?.mkdirs()
            
            instance = Room.databaseBuilder<AppDatabase>(
                name = dbFile.absolutePath
            ).setDriver(BundledSQLiteDriver()).build()
        }
        return instance!!
    }
}
