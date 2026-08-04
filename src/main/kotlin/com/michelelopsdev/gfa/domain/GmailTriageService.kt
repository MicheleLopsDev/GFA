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
                        TriageAction.TRASH -> trashEmail(email.id)
                        TriageAction.KEEP_AND_LABEL -> labelAndDownload(email, matchingRule.labelName ?: "GFA_Default")
                        TriageAction.IGNORE -> { /* Do nothing */ }
                    }
                }

                emailDao.insertTriagedEmail(TriagedEmailEntity(email.id, System.currentTimeMillis(), matchingRule?.action?.name ?: "UNMATCHED"))
                totalTriaged++
            }
            println("File ${file.name} processato.")
        }
        
        println("Triage completato. Email esaminate: $totalTriaged")
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
                    trashEmails.add(email)
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

    private suspend fun trashEmail(emailId: String) {
        executeWithBackoff {
            gmailService.users().messages().trash(user, emailId).execute()
        }
    }

    private suspend fun labelAndDownload(email: EmailData, labelName: String) {
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
                        val categoryDir = File(attachmentsDir, labelName)
                        if (!categoryDir.exists()) categoryDir.mkdirs()
                        
                        val destFile = File(categoryDir, "${email.id}_${part.filename}")
                        destFile.writeBytes(fileData)
                    }
                }
            }
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
}
