package com.michelelopsdev.gfa

import com.michelelopsdev.gfa.domain.ExcelExporterService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    try {
        println("Inizio test export Excel...")
        val path = ExcelExporterService().exportToExcel()
        println("SUCCESS. Path: $path")
    } catch (e: Throwable) {
        println("ERROR:")
        e.printStackTrace()
    }
}
