package com.michelelopsdev.gfa.utils

import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

object AppLogger {
    private val logger = Logger.getLogger("GFA")

    init {
        try {
            val logDir = File(System.getProperty("user.home"), ".gfa")
            if (!logDir.exists()) logDir.mkdirs()
            
            val fh = FileHandler(File(logDir, "gfa.log").absolutePath, true)
            fh.formatter = SimpleFormatter()
            logger.addHandler(fh)
        } catch (e: Exception) {
            System.err.println("Impossibile inizializzare il logger: ${e.message}")
        }
    }

    fun info(msg: String) {
        logger.info(msg)
        println("[INFO] $msg") // Mantengo output su console per lo sviluppo
    }

    fun error(msg: String, e: Throwable? = null) {
        if (e != null) {
            logger.severe("$msg\n${e.stackTraceToString()}")
            System.err.println("[ERROR] $msg\n${e.stackTraceToString()}")
        } else {
            logger.severe(msg)
            System.err.println("[ERROR] $msg")
        }
    }
}
