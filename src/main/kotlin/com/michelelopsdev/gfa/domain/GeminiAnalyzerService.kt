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


    // Nuova logica di aggregazione pulita
    suspend fun generateCleanupRules(onLog: ((String, String, String?, List<String>) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (!apiKeyFile.exists()) throw IllegalStateException("API Key mancante! Crea il file ${apiKeyFile.absolutePath}")
        val apiKey = apiKeyFile.readText().trim()
        if (apiKey.isEmpty()) throw IllegalStateException("L'API Key è vuota.")

        val jsonParser = Json { ignoreUnknownKeys = true }
        val jsonFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
            ?.filter { it.length() > 0 } ?: throw IllegalStateException("Nessun dato trovato.")

        println("Fase 2: Aggregazione dati locali in corso...")

        val domainCount = mutableMapOf<String, Int>()
        val domainSubjects = mutableMapOf<String, MutableSet<String>>()

        var scanned = 0
        for (file in jsonFiles) {
            val text = file.readText()
            if (text.isBlank()) continue
            val emails = jsonParser.decodeFromString<List<EmailData>>(text)
            for (email in emails) {
                scanned++
                val domain = extractDomain(email.da)
                if (domain.isNotEmpty()) {
                    domainCount[domain] = domainCount.getOrDefault(domain, 0) + 1
                    val subjects = domainSubjects.getOrPut(domain) { mutableSetOf() }
                    if (subjects.size < 3 && email.titolo.isNotBlank()) {
                        subjects.add(email.titolo.take(40).replace("\n", " "))
                    }
                }
            }
        }

        // Prendiamo i top 100 domini più frequenti
        val topDomains = domainCount.entries
            .sortedByDescending { it.value }
            .take(150)
            .filter { it.value > 2 } // ignoriamo i domini che compaiono 1 o 2 volte

        val sampleData = topDomains.joinToString("\n") { entry ->
            val dom = entry.key
            val count = entry.value
            val subs = domainSubjects[dom]?.joinToString(" | ") ?: ""
            "Dominio: @$dom (Conteggio: $count email) - Esempi Oggetti: $subs"
        }

        val prompt = """
            Sei un sistema di pulizia e Triage email.
            Ho analizzato una casella di posta di $scanned email. Ecco i mittenti più frequenti (aggregati per dominio) che ingombrano la casella, con il loro conteggio e alcuni oggetti di esempio:
            
            $sampleData
            
            Il tuo compito è generare regole di classificazione in formato JSON ESCLUSIVAMENTE per eliminare la spazzatura (Newsletter, Spam, Social, Notifiche automatiche inutili).
            Ignora (non creare regole per) i domini che sembrano importanti (es. banche, comunicazioni personali, lavoro), perché ce ne occuperemo nella Fase 4.
            
            Il JSON DEVE avere questa struttura esatta, senza markdown o testo aggiuntivo (solo il JSON nudo e crudo):
            {
              "rules": [
                {
                  "id": "regola_social",
                  "patternMittente": ".*@(facebook|instagram|twitter|linkedin)\\.com.*",
                  "action": "TRASH"
                },
                {
                  "id": "regola_marketing",
                  "patternMittente": ".*@newsletter\\.ecommerce\\.it.*",
                  "patternOggetto": ".*(sconto|offerta).*",
                  "action": "TRASH"
                }
              ]
            }
            
            Le action consentite per ora è solo: TRASH.
            Crea espressioni regolari (regex) intelligenti. Raggruppa domini simili con (dominio1|dominio2) se l'azione è la stessa per risparmiare regole.
        """.trimIndent()

        val modelsToTry = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.6-flash", "gemini-2.0-flash")
        var lastError = ""
        var success = false
        var successfulModel: String? = null
        val failedModels = mutableListOf<String>()

        for (modelName in modelsToTry) {
            println("Tentativo con il modello: $modelName...")
            
            val requestBody = buildJsonObject {
                put("contents", buildJsonArray {
                    addJsonObject { put("parts", buildJsonArray { addJsonObject { put("text", prompt) } }) }
                })
            }.toString()

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseJson = jsonParser.parseToJsonElement(response.body()).jsonObject
                val outputText = responseJson["candidates"]?.jsonArray?.get(0)?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: throw IllegalStateException("Risposta non valida da Gemini.")
                
                val cleanJson = outputText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                rulesFile.writeText(cleanJson)
                println("File regole di pulizia (Fase 2) generato in: ${rulesFile.absolutePath} usando $modelName")
                successfulModel = modelName
                onLog?.invoke(prompt, cleanJson, successfulModel, failedModels)
                success = true
                break // Esce dal loop se ha avuto successo
            } else {
                lastError = "Errore $modelName: ${response.statusCode()}"
                println(lastError)
                failedModels.add("$modelName (KO: ${response.statusCode()})")
                onLog?.invoke(prompt, lastError, null, failedModels)
                // Se c'è errore, continua il loop col prossimo modello
            }
        }

        if (!success) {
            throw IllegalStateException("Tutti i modelli sono sovraccarichi. Ultimo errore: $lastError")
        }
    }
    
    // Per retrocompatibilità temporanea chiamiamo la nuova funzione
    suspend fun generateRules(onLog: ((String, String, String?, List<String>) -> Unit)? = null) {
        generateCleanupRules(onLog)
    }

    private fun extractDomain(sender: String): String {
        // Esempio: "Amazon <auto-confirm@amazon.it>" -> "amazon.it"
        // Esempio: "newsletter@sub.domain.com" -> "sub.domain.com"
        val emailPart = if (sender.contains("<") && sender.contains(">")) {
            sender.substringAfter("<").substringBefore(">")
        } else {
            sender
        }
        return emailPart.substringAfter("@", "").trim().lowercase()
    }
}
