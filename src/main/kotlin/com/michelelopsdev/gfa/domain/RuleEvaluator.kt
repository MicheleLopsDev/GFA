package com.michelelopsdev.gfa.domain

import com.michelelopsdev.gfa.data.model.EmailData
import com.michelelopsdev.gfa.data.model.Rule

class RuleEvaluator(private val rules: List<Rule>) {

    fun evaluate(email: EmailData): Rule? {
        // Filtro Globale di Sicurezza: Protegge le email che contengono un Codice Fiscale
        val cfRegex = Regex("[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]", RegexOption.IGNORE_CASE)
        if (cfRegex.containsMatchIn(email.titolo) || cfRegex.containsMatchIn(email.testo)) {
            return null
        }

        for (rule in rules) {
            if (!rule.isActive) continue
            
            val matchesMittente = rule.patternMittente?.let {
                Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(email.da)
            } ?: true

            val matchesOggetto = rule.patternOggetto?.let {
                Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(email.titolo)
            } ?: true

            if (matchesMittente && matchesOggetto) {
                return rule
            }
        }
        return null
    }
}
