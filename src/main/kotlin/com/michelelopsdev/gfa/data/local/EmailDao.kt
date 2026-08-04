package com.michelelopsdev.gfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedEmail(email: ProcessedEmailEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM processed_emails WHERE emailId = :emailId LIMIT 1)")
    suspend fun isEmailProcessed(emailId: String): Boolean

    @Query("SELECT COUNT(*) FROM processed_emails")
    suspend fun getProcessedCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTriagedEmail(email: TriagedEmailEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM triaged_emails WHERE emailId = :emailId LIMIT 1)")
    suspend fun isEmailTriaged(emailId: String): Boolean

    @Query("DELETE FROM processed_emails")
    suspend fun clearProcessedEmails()

    @Query("DELETE FROM triaged_emails")
    suspend fun clearTriagedEmails()
}
