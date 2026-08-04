package com.michelelopsdev.gfa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_emails")
data class ProcessedEmailEntity(
    @PrimaryKey val emailId: String,
    val processedAt: Long
)
