package com.michelelopsdev.gfa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "triaged_emails")
data class TriagedEmailEntity(
    @PrimaryKey val emailId: String,
    val triagedAt: Long,
    val actionTaken: String
)
