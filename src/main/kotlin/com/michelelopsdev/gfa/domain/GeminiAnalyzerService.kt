package com.michelelopsdev.gfa.domain

import com.michelelopsdev.gfa.data.model.EmailData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
        
        val sampleFile = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
            ?.firstOrNull() ?: throw IllegalStateException("Nessun file JSON trovato nella Fase 1.")
            
        val emailsText = sampleFile.readText()
        val emails = jsonParser.decodeFromString<List<EmailData>>(emailsText)
        
        val sampleSize = minOf(emails.size, 500)
        val sampleData = emails.take(sampleSize).joinToString("\n") { 
            "Da: ${it.da} | Oggetto: ${it.titolo}" 
        }

        val prompt = """
            Sei un sistema di classificazione email.
            Ecco un campione di $sampleSize email estratte dalla casella di un utente:
            
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

        println("Contatto Gemini 1.5 Flash via REST API per l'analisi di $sampleSize email...")

        // Chiamata REST pura per bypassare i problemi della libreria Android su Desktop
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                addJsonObject {
                    put("parts", buildJsonArray {
                        addJsonObject {
                            put("text", prompt)
                        }
                    })
                }
            })
        }.toString()

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw IllegalStateException("Errore API Gemini (${response.statusCode()}): ${response.body()}")
        }

        // Parsing della risposta JSON di Gemini
        val responseJson = jsonParser.parseToJsonElement(response.body()).jsonObject
        val outputText = responseJson["candidates"]?.jsonArray?.get(0)?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.get(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content ?: throw IllegalStateException("Risposta non valida da Gemini.")
        
        // Pulisce l'output da eventuali tag markdown ```json ... ``` generati da Gemini
        val cleanJson = outputText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        rulesFile.writeText(cleanJson)
        println("File delle regole generato con successo in: ${rulesFile.absolutePath}")
    }
}
