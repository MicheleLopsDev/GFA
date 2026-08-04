package com.michelelopsdev.gfa.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

object DatabaseFactory {
    fun createDatabase(): AppDatabase {
        val dbFile = File(System.getProperty("user.home"), ".gfa/gfa_database.db")
        dbFile.parentFile?.mkdirs()
        
        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath
        )
        return builder.setDriver(BundledSQLiteDriver()).build()
    }
}
