package com.michelelopsdev.gfa.domain

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.michelelopsdev.gfa.data.model.EmailData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class GeminiAnalyzerService {

    private val outputDir = File(System.getProperty("user.home"), ".gfa/output")
    private val rulesFile = File(System.getProperty("user.home"), ".gfa/rules.json")
    private val apiKeyFile = File(System.getProperty("user.home"), ".gfa/gemini_api_key.txt")

    suspend fun generateRules() = withContext(Dispatchers.IO) {
        if (!apiKeyFile.exists()) {
            throw IllegalStateException("API Key mancante! Crea il file ${apiKeyFile.absolutePath} e inserisci la tua chiave di Google AI Studio.")
        }
        
        val apiKey = apiKeyFile.readText().trim()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("L'API Key nel file ${apiKeyFile.absolutePath} è vuota.")
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        
        // Prendiamo un campione dalle email scaricate (es. il primo file JSON)
        val sampleFile = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
            ?.firstOrNull() ?: throw IllegalStateException("Nessun file JSON trovato nella Fase 1.")
            
        val emailsText = sampleFile.readText()
        val emails = jsonParser.decodeFromString<List<EmailData>>(emailsText)
        
        // Prendiamo solo le prime 500 email per non saturare i token inutilmente e velocizzare
        val sampleSize = minOf(emails.size, 500)
        val sampleData = emails.take(sampleSize).joinToString("\n") { 
            "Da: ${it.da} | Oggetto: ${it.titolo}" 
        }

        val prompt = """
            Sei un sistema di classificazione email.
            Ecco un campione di 500 email estratte dalla casella di un utente:
            
            $sampleData
            
            Analizza i pattern dei mittenti e degli oggetti. Crea delle regole di classificazione in formato JSON.
            Il JSON DEVE avere questa struttura esatta, senza markdown o testo aggiuntivo (solo il JSON nudo e crudo):
            {
              "rules": [
                {
                  "id": "regola_banca",
                  "patternMittente": ".*@banca\\.it.*",
                  "action": "KEEP_AND_LABEL",
                  "labelName": "Banca"
                },
                {
                  "id": "regola_spam_newsletter",
                  "patternOggetto": ".*(sconto|offerta|newsletter).*",
                  "action": "TRASH"
                }
              ]
            }
            
            Le action consentite sono solo: TRASH, KEEP_AND_LABEL, IGNORE.
            Tenta di coprire il più possibile i domini frequenti presenti nel campione fornito, categorizzandoli logicamente (Bollette, Lavoro, Social, Spam, etc.).
        """.trimIndent()

        println("Contatto Gemini 1.5 Flash per l'analisi di $sampleSize email...")

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )

        val response = generativeModel.generateContent(
            content {
                text(prompt)
            }
        )

        val outputText = response.text ?: throw IllegalStateException("Risposta vuota da Gemini.")
        
        // Pulisce l'output da eventuali tag markdown ```json ... ``` generati da Gemini
        val cleanJson = outputText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        rulesFile.writeText(cleanJson)
        println("File delle regole generato con successo in: ${rulesFile.absolutePath}")
    }
}
