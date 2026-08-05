package com.michelelopsdev.gfa.domain

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Filter
import com.google.api.services.gmail.model.FilterAction
import com.google.api.services.gmail.model.FilterCriteria
import com.michelelopsdev.gfa.data.model.Rule
import com.michelelopsdev.gfa.data.model.TriageAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GmailFilterExporter(private val gmailService: Gmail) {
    private val user = "me"

    suspend fun exportRulesToFilters(rules: List<Rule>): ExportResult = withContext(Dispatchers.IO) {
        var successCount = 0
        var errorCount = 0
        val ignoredRules = mutableListOf<String>()

        // Esportiamo solo le regole attive che spostano nel cestino
        val activeRules = rules.filter { it.isActive && it.action == TriageAction.TRASH }

        for (rule in activeRules) {
            val query = convertRegexToQuery(rule.patternMittente, rule.patternOggetto)
            if (query == null) {
                ignoredRules.add("${rule.id} (Pattern troppo complesso per Gmail)")
                errorCount++
                continue
            }

            try {
                val criteria = FilterCriteria().setQuery(query)
                val action = FilterAction().setRemoveLabelIds(listOf("INBOX")).setAddLabelIds(listOf("TRASH"))

                val filter = Filter().setCriteria(criteria).setAction(action)
                gmailService.users().settings().filters().create(user, filter).execute()
                successCount++
            } catch (e: Exception) {
                ignoredRules.add("${rule.id}: ${e.message}")
                errorCount++
            }
        }

        ExportResult(successCount, errorCount, ignoredRules)
    }

    private fun convertRegexToQuery(patternMittente: String?, patternOggetto: String?): String? {
        val queryParts = mutableListOf<String>()
        
        patternMittente?.let { pm ->
            // Puliamo i pattern basilari
            val clean = pm.removePrefix(".*").removeSuffix(".*")
                .replace("\\.", ".") // Riportiamo i punti escaped alla normalità
                
            if (clean.contains("|") && clean.contains("(")) {
                // Caso regex IA: @(sito1|sito2).com
                try {
                    val prefix = clean.substringBefore("(").removePrefix("@")
                    val options = clean.substringAfter("(").substringBefore(")").split("|")
                    val suffix = clean.substringAfter(")")
                    val conditions = options.joinToString(" OR ") { "from:${prefix}${it}${suffix}" }
                    queryParts.add("($conditions)")
                } catch (e: Exception) {
                    return null // Regex troppo contorta
                }
            } else if (!clean.contains("|") && !clean.contains("(")) {
                // Caso stringa esatta: spam.com o nome@spam.com
                val addr = clean.removePrefix("@")
                queryParts.add("from:$addr")
            } else {
                return null
            }
        }

        patternOggetto?.let { po ->
            val clean = po.removePrefix(".*").removeSuffix(".*").replace("\\.", ".")
            if (!clean.contains("|") && !clean.contains("(")) {
                queryParts.add("subject:\"$clean\"")
            } else {
                return null
            }
        }

        if (queryParts.isEmpty()) return null
        return queryParts.joinToString(" ")
    }
}

data class ExportResult(val success: Int, val errors: Int, val ignoredDetails: List<String>)
