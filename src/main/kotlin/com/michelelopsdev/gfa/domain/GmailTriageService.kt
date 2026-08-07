package com.michelelopsdev.gfa.domain

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Label
import com.google.api.services.gmail.model.ModifyMessageRequest
import com.michelelopsdev.gfa.data.local.EmailDao
import com.michelelopsdev.gfa.data.local.TriagedEmailEntity
import com.michelelopsdev.gfa.data.model.EmailData
import com.michelelopsdev.gfa.data.model.RuleConfig
import com.michelelopsdev.gfa.data.model.TriageAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.pow
import com.michelelopsdev.gfa.utils.AppLogger

class GmailTriageService(
    private val gmailService: Gmail,
    private val emailDao: EmailDao
) {
    private val outputDir = File(System.getProperty("user.home"), ".gfa/output")
    private val rulesFile = File(System.getProperty("user.home"), ".gfa/rules.json")
    private val attachmentsDir = File(System.getProperty("user.home"), ".gfa/attachments")
    private val user = "me"
    
    private val labelCache = mutableMapOf<String, String>() // Label Name -> Label ID

    suspend fun startTriage() = withContext(Dispatchers.IO) {
        if (!rulesFile.exists()) {
            throw IllegalStateException("File rules.json non trovato. L'LLM deve prima generarlo in ${rulesFile.absolutePath}")
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val rulesText = rulesFile.readText()
        val ruleConfig = jsonParser.decodeFromString<RuleConfig>(rulesText)
        val evaluator = RuleEvaluator(ruleConfig.rules)

        loadExistingLabels()

        val jsonFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
            ?: return@withContext

        var totalTriaged = 0

        for (file in jsonFiles) {
            val emailsText = file.readText()
            val emails = jsonParser.decodeFromString<List<EmailData>>(emailsText)

            for (email in emails) {
                if (emailDao.isEmailTriaged(email.id)) continue

                val matchingRule = evaluator.evaluate(email)
                if (matchingRule != null) {
                    when (matchingRule.action) {
                        TriageAction.TRASH -> {
                            if (!containsCodiceFiscale(email.testo) && !containsCodiceFiscale(email.titolo)) {
                                trashEmail(email.id)
                            } else {
                                AppLogger.info("Email ${email.id} non cestinata (contiene Codice Fiscale)")
                            }
                        }
                        TriageAction.KEEP_AND_LABEL -> labelAndDownload(email, matchingRule.labelName ?: "GFA_Default")
                        TriageAction.IGNORE -> { /* Do nothing */ }
                    }
                }

                emailDao.insertTriagedEmail(TriagedEmailEntity(email.id, System.currentTimeMillis(), matchingRule?.action?.name ?: "UNMATCHED"))
                totalTriaged++
            }
            AppLogger.info("File ${file.name} processato.")
        }
        
        AppLogger.info("Triage completato. Email esaminate: $totalTriaged")
    }

    suspend fun simulateTriage(): List<EmailData> = withContext(Dispatchers.IO) {
        if (!rulesFile.exists()) {
            throw IllegalStateException("File rules.json non trovato. L'LLM deve prima generarlo in ${rulesFile.absolutePath}")
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val rulesText = rulesFile.readText()
        val ruleConfig = jsonParser.decodeFromString<RuleConfig>(rulesText)
        val evaluator = RuleEvaluator(ruleConfig.rules)

        val jsonFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
            ?: return@withContext emptyList()

        val trashEmails = mutableListOf<EmailData>()

        for (file in jsonFiles) {
            val emailsText = file.readText()
            val emails = jsonParser.decodeFromString<List<EmailData>>(emailsText)

            for (email in emails) {
                if (emailDao.isEmailTriaged(email.id)) continue

                val matchingRule = evaluator.evaluate(email)
                if (matchingRule?.action == TriageAction.TRASH) {
                    if (!containsCodiceFiscale(email.testo) && !containsCodiceFiscale(email.titolo)) {
                        trashEmails.add(email)
                    }
                }
            }
        }
        
        return@withContext trashEmails
    }

    suspend fun executeTrash(emailsToTrash: List<String>, onProgress: ((Int, Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val total = emailsToTrash.size
        var current = 0
        for (emailId in emailsToTrash) {
            trashEmail(emailId)
            emailDao.insertTriagedEmail(TriagedEmailEntity(emailId, System.currentTimeMillis(), TriageAction.TRASH.name))
            current++
            onProgress?.invoke(current, total)
        }
    }

    suspend fun executeRestoreTrash(onProgress: ((Int, Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val trashedEmails = emailDao.getTriagedEmailsByAction(TriageAction.TRASH.name)
        val total = trashedEmails.size
        var current = 0
        for (emailId in trashedEmails) {
            untrashEmail(emailId)
            emailDao.deleteTriagedEmail(emailId)
            current++
            onProgress?.invoke(current, total)
        }
    }

    suspend fun simulateGems(): List<EmailData> = withContext(Dispatchers.IO) {
        if (!rulesFile.exists()) {
            throw IllegalStateException("File rules.json non trovato. L'LLM deve prima generarlo in ${rulesFile.absolutePath}")
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val rulesText = rulesFile.readText()
        val ruleConfig = jsonParser.decodeFromString<RuleConfig>(rulesText)
        val evaluator = RuleEvaluator(ruleConfig.rules)

        // Non escludiamo le email già scaricate, ma escludiamo solo quelle cestinate
        val trashedIds = emailDao.getTriagedEmailsByAction(TriageAction.TRASH.name).toSet()

        val jsonFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
            ?: return@withContext emptyList()

        val gemEmails = mutableListOf<EmailData>()

        for (file in jsonFiles) {
            val emailsText = file.readText()
            val emails = jsonParser.decodeFromString<List<EmailData>>(emailsText)

            for (email in emails) {
                if (trashedIds.contains(email.id)) continue

                val matchingRule = evaluator.evaluate(email)
                if (matchingRule?.action != TriageAction.TRASH && email.haAllegati) {
                    gemEmails.add(email)
                }
            }
        }
        
        return@withContext gemEmails
    }

    suspend fun executeDownloadGems(emailsToDownload: List<EmailData>, onProgress: ((Int, Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val total = emailsToDownload.size
        var current = 0
        for (email in emailsToDownload) {
            downloadAttachments(email)
            emailDao.insertTriagedEmail(TriagedEmailEntity(email.id, System.currentTimeMillis(), "DOWNLOADED"))
            current++
            onProgress?.invoke(current, total)
        }
    }

    private suspend fun downloadAttachments(email: EmailData) {
        if (!email.haAllegati) return

        try {
            val message = executeWithBackoff {
                gmailService.users().messages().get(user, email.id).execute()
            }
            
            message?.payload?.parts?.forEach { part ->
                if (!part.filename.isNullOrEmpty() && part.body?.attachmentId != null) {
                    val attachment = executeWithBackoff {
                        gmailService.users().messages().attachments().get(user, email.id, part.body.attachmentId).execute()
                    }
                    if (attachment != null) {
                        val fileData = attachment.decodeData()
                        
                        // Pulizia del nome mittente e nome file per creare una cartella/file valida
                        val safeSenderName = email.da.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
                        val safeFilename = part.filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        
                        val senderDir = File(attachmentsDir, safeSenderName)
                        if (!senderDir.exists()) senderDir.mkdirs()
                        
                        val destFile = File(senderDir, "${email.id}_$safeFilename")
                        destFile.writeBytes(fileData)
                        
                        com.michelelopsdev.gfa.utils.AppLogger.info("Allegato scaricato con successo: $safeFilename in ${senderDir.name}")
                    }
                }
            }
        } catch (e: Exception) {
            com.michelelopsdev.gfa.utils.AppLogger.error("Errore durante lo scaricamento degli allegati per email ${email.id}", e)
            throw e
        }
    }

    private suspend fun trashEmail(emailId: String) {
        executeWithBackoff {
            gmailService.users().messages().trash(user, emailId).execute()
        }
    }

    private suspend fun untrashEmail(emailId: String) {
        executeWithBackoff {
            // Untrash it first
            gmailService.users().messages().untrash(user, emailId).execute()
            
            // Remove INBOX label to force it to Archive
            val modifyRequest = com.google.api.services.gmail.model.ModifyMessageRequest().setRemoveLabelIds(listOf("INBOX"))
            gmailService.users().messages().modify(user, emailId, modifyRequest).execute()
        }
    }

    private suspend fun labelAndDownload(email: EmailData, labelName: String) {
        try {
            val labelId = getOrCreateLabel(labelName)
            
            // Applica etichetta
            val modifyRequest = ModifyMessageRequest().setAddLabelIds(listOf(labelId))
            executeWithBackoff {
                gmailService.users().messages().modify(user, email.id, modifyRequest).execute()
            }
    
            // Scarica allegati
            if (email.haAllegati) {
                val message = executeWithBackoff {
                    gmailService.users().messages().get(user, email.id).execute()
                }
                
                message?.payload?.parts?.forEach { part ->
                    if (!part.filename.isNullOrEmpty() && part.body?.attachmentId != null) {
                        val attachment = executeWithBackoff {
                            gmailService.users().messages().attachments().get(user, email.id, part.body.attachmentId).execute()
                        }
                        if (attachment != null) {
                            val fileData = attachment.decodeData()
                            val categoryDir = File(attachmentsDir, labelName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                            if (!categoryDir.exists()) categoryDir.mkdirs()
                            
                            val safeFilename = part.filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            val destFile = File(categoryDir, "${email.id}_$safeFilename")
                            destFile.writeBytes(fileData)
                            
                            com.michelelopsdev.gfa.utils.AppLogger.info("Allegato organizzato: $safeFilename in ${categoryDir.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.michelelopsdev.gfa.utils.AppLogger.error("Errore nell'applicazione dell'etichetta o scaricamento per email ${email.id}", e)
        }
    }

    private suspend fun loadExistingLabels() {
        val listResponse = executeWithBackoff {
            gmailService.users().labels().list(user).execute()
        }
        listResponse?.labels?.forEach { label ->
            labelCache[label.name] = label.id
        }
    }

    private suspend fun getOrCreateLabel(labelName: String): String {
        return labelCache[labelName] ?: run {
            val newLabel = Label().setName(labelName).setLabelListVisibility("labelShow").setMessageListVisibility("show")
            val created = executeWithBackoff {
                gmailService.users().labels().create(user, newLabel).execute()
            }
            val id = created!!.id
            labelCache[labelName] = id
            id
        }
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
                    delay(waitTime)
                    retries++
                } else {
                    throw e
                }
            }
        }
        return null
    }

    private fun containsCodiceFiscale(text: String): Boolean {
        val regex = Regex("\\b[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]\\b", RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(text)
    }
}
