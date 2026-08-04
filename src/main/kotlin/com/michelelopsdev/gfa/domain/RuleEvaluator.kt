package com.michelelopsdev.gfa.domain

import com.michelelopsdev.gfa.data.model.EmailData
import com.michelelopsdev.gfa.data.model.Rule

class RuleEvaluator(private val rules: List<Rule>) {

    fun evaluate(email: EmailData): Rule? {
        for (rule in rules) {
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
