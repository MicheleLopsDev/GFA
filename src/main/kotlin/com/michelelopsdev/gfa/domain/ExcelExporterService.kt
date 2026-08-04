package com.michelelopsdev.gfa.domain

import com.michelelopsdev.gfa.data.model.EmailData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class ExcelExporterService {
    private val outputDir = File(System.getProperty("user.home"), ".gfa/output")
    private val exportFile = File(System.getProperty("user.home"), "Desktop/GFA_Export_Email.xlsx")

    suspend fun exportToExcel() = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) return@withContext
        
        val jsonFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
        if (jsonFiles == null || jsonFiles.isEmpty()) {
            throw IllegalStateException("Nessun file JSON trovato da esportare.")
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Email Estratte")
            
            // Header
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("ID")
            headerRow.createCell(1).setCellValue("Mittente")
            headerRow.createCell(2).setCellValue("Destinatario")
            headerRow.createCell(3).setCellValue("Oggetto")
            headerRow.createCell(4).setCellValue("Snippet")
            headerRow.createCell(5).setCellValue("Ha Allegati")
            headerRow.createCell(6).setCellValue("Nomi Allegati")

            var rowNum = 1

            for (file in jsonFiles) {
                val emailsText = file.readText()
                val emails = jsonParser.decodeFromString<List<EmailData>>(emailsText)
                
                for (email in emails) {
                    val row = sheet.createRow(rowNum++)
                    row.createCell(0).setCellValue(email.id)
                    row.createCell(1).setCellValue(email.da)
                    row.createCell(2).setCellValue(email.a)
                    row.createCell(3).setCellValue(email.titolo)
                    row.createCell(4).setCellValue(email.testo)
                    row.createCell(5).setCellValue(if (email.haAllegati) "SI" else "NO")
                    row.createCell(6).setCellValue(email.nomiAllegati.joinToString(", "))
                }
            }

            FileOutputStream(exportFile).use { out ->
                workbook.write(out)
            }
        }
        println("Export completato! File salvato in: ${exportFile.absolutePath}")
    }
}
