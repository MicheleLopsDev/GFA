package com.michelelopsdev.gfa.domain

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.michelelopsdev.gfa.data.local.EmailDao
import com.michelelopsdev.gfa.data.local.ProcessedEmailEntity
import com.michelelopsdev.gfa.data.model.EmailData
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.pow

data class FetcherStats(val totalProcessed: Int, val speedPerSecond: Int)

class GmailFetcherService(
    private val gmailService: Gmail,
    private val emailDao: EmailDao
) {
    private val outputDir = File(System.getProperty("user.home"), ".gfa/output")
    private val batchSize = 1000

    private val _isPaused = MutableStateFlow(false)
    var isPaused: Boolean
        get() = _isPaused.value
        set(value) { _isPaused.value = value }

    private val _stats = MutableStateFlow(FetcherStats(0, 0))
    val stats = _stats.asStateFlow()

    @Volatile
    var isRunning = true

    init {
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    suspend fun extractData() {
        var nextPageToken: String? = null
        var totalProcessed = 0
        var currentBatch = mutableListOf<EmailData>()
        var partNumber = 1
        
        println("Avvio connessione a Gmail e inizio scansione messaggi...")

        val user = "me"
        var lastTimeMillis = System.currentTimeMillis()
        var lastProcessedCount = 0

        do {
            while (_isPaused.value && isRunning) {
                delay(500)
            }
            if (!isRunning) break

            val response = executeWithBackoff {
                var request = gmailService.users().messages().list(user)
                    .setMaxResults(500L)
                if (nextPageToken != null) {
                    request = request.setPageToken(nextPageToken)
                }
                request.execute()
            }

            val messages = response?.messages ?: emptyList()

            for (msgItem in messages) {
                while (_isPaused.value && isRunning) { delay(500) }
                if (!isRunning) break

                if (!emailDao.isEmailProcessed(msgItem.id)) {
                    val fullMessage = executeWithBackoff {
                        gmailService.users().messages().get(user, msgItem.id).execute()
                    }

                    if (fullMessage != null) {
                        val emailData = parseMessage(fullMessage)
                        currentBatch.add(emailData)

                        emailDao.insertProcessedEmail(
                            ProcessedEmailEntity(msgItem.id, System.currentTimeMillis())
                        )
                        totalProcessed++
                        
                        val now = System.currentTimeMillis()
                        if (now - lastTimeMillis >= 1000) {
                            val speed = totalProcessed - lastProcessedCount
                            _stats.value = FetcherStats(totalProcessed, speed)
                            lastProcessedCount = totalProcessed
                            lastTimeMillis = now
                        }

                        if (totalProcessed % 50 == 0) {
                            println("Sto elaborando... scaricati $totalProcessed messaggi finora.")
                        }

                        if (currentBatch.size >= batchSize) {
                            saveBatch(currentBatch, partNumber)
                            partNumber++
                            currentBatch.clear()
                        }
                    }
                }
            }
            nextPageToken = response?.nextPageToken
        } while (nextPageToken != null && isRunning)

        // Save remaining
        if (currentBatch.isNotEmpty()) {
            saveBatch(currentBatch, partNumber)
        }
        
        println("Estrazione completata. Email processate in totale in questa sessione: $totalProcessed")
    }

    private fun parseMessage(message: Message): EmailData {
        val headers = message.payload?.headers ?: emptyList()
        val subject = headers.find { it.name.equals("Subject", true) }?.value ?: ""
        val from = headers.find { it.name.equals("From", true) }?.value ?: ""
        val to = headers.find { it.name.equals("To", true) }?.value ?: ""
        val snippet = message.snippet ?: ""

        val attachments = mutableListOf<String>()
        findAttachments(message.payload, attachments)

        return EmailData(
            id = message.id,
            titolo = subject,
            da = from,
            a = to,
            testo = snippet,
            haAllegati = attachments.isNotEmpty(),
            nomiAllegati = attachments
        )
    }

    private fun findAttachments(part: MessagePart?, attachments: MutableList<String>) {
        if (part == null) return
        if (part.filename != null && part.filename.isNotEmpty()) {
            attachments.add(part.filename)
        }
        part.parts?.forEach { childPart ->
            findAttachments(childPart, attachments)
        }
    }

    private fun saveBatch(batch: List<EmailData>, partNumber: Int) {
        val file = File(outputDir, "emails_part_$partNumber.json")
        val json = Json { prettyPrint = true }
        file.writeText(json.encodeToString(batch))
        println("Salvato batch $partNumber con ${batch.size} email in ${file.absolutePath}")
    }

    private suspend fun <T> executeWithBackoff(block: () -> T): T? {
        var retries = 0
        val maxRetries = 5
        while (retries < maxRetries) {
            try {
                return block()
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 429 || e.statusCode >= 500) {
                    val waitTime = (2.0.pow(retries.toDouble()) * 1000).toLong()
                    println("Rate limit o errore server (Codice ${e.statusCode}). Ritento tra $waitTime ms...")
                    delay(waitTime)
                    retries++
                } else {
                    throw e
                }
            }
        }
        return null
    }
}
