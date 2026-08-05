package com.michelelopsdev.gfa.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GeminiSessionLog(
    val timestamp: Long,
    val request: String,
    val response: String,
    val successfulModel: String?,
    val failedModels: List<String>
)
