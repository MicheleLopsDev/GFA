package com.michelelopsdev.gfa.auth

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

class GmailAuthManager {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val tokensDir = File(System.getProperty("user.home"), ".gfa/tokens")
    private val credentialsFile = File(System.getProperty("user.home"), ".gfa/credentials.json")

    private val scopes = listOf(GmailScopes.GMAIL_MODIFY)

    suspend fun getGmailService(): Gmail = withContext(Dispatchers.IO) {
        val credential = authorize()
        Gmail.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Gmail Filter Advanced")
            .build()
    }

    private fun authorize(): Credential {
        if (!credentialsFile.exists()) {
            throw IllegalStateException("File credentials.json non trovato in ${credentialsFile.absolutePath}")
        }

        val clientSecrets = GoogleClientSecrets.load(jsonFactory, FileReader(credentialsFile))

        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport, jsonFactory, clientSecrets, scopes
        )
            .setDataStoreFactory(FileDataStoreFactory(tokensDir))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }
}
