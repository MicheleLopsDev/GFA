package com.michelelopsdev.gfa.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EmailData(
    val id: String,
    val titolo: String,
    val da: String,
    val a: String,
    val data: String = "",
    val testo: String,
    val haAllegati: Boolean,
    val nomiAllegati: List<String>
)
