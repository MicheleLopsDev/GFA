package com.michelelopsdev.gfa.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TriageAction {
    TRASH,
    KEEP_AND_LABEL,
    IGNORE
}

@Serializable
data class Rule(
    val id: String,
    val patternMittente: String? = null,
    val patternOggetto: String? = null,
    val action: TriageAction,
    val labelName: String? = null, // Usato solo se action == KEEP_AND_LABEL
    val isActive: Boolean = true // Per spegnere le regole senza cancellarle
)

@Serializable
data class RuleConfig(
    val rules: List<Rule>
)
