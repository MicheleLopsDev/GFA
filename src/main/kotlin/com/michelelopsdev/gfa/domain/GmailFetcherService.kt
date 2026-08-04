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

data class FetcherStats(
    val totalProcessed: Int, 
    val speedPerSecond: Int,
    val totalInboxMessages: Int = 0,
    val currentEmailSubject: String = "",
    val lastEmailDate: String = "",
    val lastSessionDate: String = "",
    val progressPercent: Float = 0f
)

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

    private val _stats = MutableStateFlow(FetcherStats(0, 0, 0, "", "", "", 0f))
    val stats = _stats.asStateFlow()

    @Volatile
    var isRunning = true

    init {
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    suspend fun extractData() {
        var nextPageToken: String? = null
        
        println("Recupero numero totale di email nell'account...")
        var totalInboxMessages = 0
        try {
            val profile = executeWithBackoff { gmailService.users().getProfile("me").execute() }
            if (profile != null) {
                totalInboxMessages = profile.messagesTotal ?: 0
                println("Email totali nella casella: $totalInboxMessages")
            }
        } catch (e: Exception) {
            println("Impossibile recuperare il totale delle email: ${e.message}")
        }

        var totalProcessed = emailDao.getProcessedCount()
        println("Email già processate nel DB: $totalProcessed")

        var currentBatch = mutableListOf<EmailData>()
        var partNumber = 1
        
        var lastSessionDateStr = ""
        
        // Cerca i file già esistenti per non sovrascriverli in caso di riavvio dopo "Ferma"
        val existingFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
        if (existingFiles != null && existingFiles.isNotEmpty()) {
            val maxPart = existingFiles.mapNotNull { 
                it.name.substringAfter("emails_part_").substringBefore(".json").toIntOrNull() 
            }.maxOrNull()
            if (maxPart != null) {
                partNumber = maxPart + 1
                
                // Recupera l'ultima data dell'ultimo file salvato
                try {
                    val lastFile = File(outputDir, "emails_part_$maxPart.json")
                    if (lastFile.exists()) {
                        val jsonParser = Json { ignoreUnknownKeys = true }
                        val emailsInFile = jsonParser.decodeFromString<List<EmailData>>(lastFile.readText())
                        if (emailsInFile.isNotEmpty()) {
                            lastSessionDateStr = emailsInFile.last().data
                        }
                    }
                } catch (e: Exception) {
                    println("Impossibile leggere l'ultima data: ${e.message}")
                }
            }
        }
        
        // Invia stat iniziale (specialmente se c'è una lastSessionDate)
        _stats.value = FetcherStats(
            totalProcessed = totalProcessed,
            speedPerSecond = 0,
            totalInboxMessages = totalInboxMessages,
            lastSessionDate = lastSessionDateStr,
            progressPercent = if (totalInboxMessages > 0) totalProcessed.toFloat() / totalInboxMessages else 0f
        )
        
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
                            val percent = if (totalInboxMessages > 0) {
                                (totalProcessed.toFloat() / totalInboxMessages.toFloat())
                            } else 0f
                            
                            _stats.value = FetcherStats(
                                totalProcessed = totalProcessed,
                                speedPerSecond = speed,
                                totalInboxMessages = totalInboxMessages,
                                currentEmailSubject = emailData.titolo,
                                lastEmailDate = emailData.data,
                                lastSessionDate = lastSessionDateStr,
                                progressPercent = percent
                            )
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
        val date = headers.find { it.name.equals("Date", true) }?.value ?: ""
        val snippet = message.snippet ?: ""

        val attachments = mutableListOf<String>()
        findAttachments(message.payload, attachments)

        return EmailData(
            id = message.id,
            titolo = subject,
            da = from,
            a = to,
            data = date,
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
