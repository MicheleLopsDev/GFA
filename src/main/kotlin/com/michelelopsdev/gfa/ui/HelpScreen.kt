package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.io.File

@Composable
fun HelpScreen(onSetStatusMessage: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Centro di Supporto & Documentazione", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(0.8f).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("⚠️ ATTENZIONE ALLA SICUREZZA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.height(16.dp))
                Text("• NON condividere MAI il file credentials.json", color = MaterialTheme.colorScheme.onErrorContainer)
                Text("• NON condividere MAI il file gemini_api_key.txt", color = MaterialTheme.colorScheme.onErrorContainer)
                Text("• Se pubblichi il codice online, assicurati che la cartella .gfa e i file json delle credenziali NON vengano caricati.", color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.height(16.dp))
                Text("La condivisione involontaria della chiave API può causare addebiti inaspettati sulla tua carta di credito.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                // Cerchiamo il file sia dal percorso relativo (quando avviato da IDE)
                // sia in altri possibili percorsi se necessario
                val docFile = File("DOC/MANUALE_UTENTE.md")
                if (docFile.exists()) {
                    try {
                        Desktop.getDesktop().open(docFile)
                        onSetStatusMessage("Manuale aperto nel visualizzatore predefinito.")
                    } catch (e: Exception) {
                        onSetStatusMessage("Errore nell'apertura del file: ${e.message}")
                    }
                } else {
                    onSetStatusMessage("Errore: File MANUALE_UTENTE.md non trovato in: ${docFile.absolutePath}")
                }
            },
            modifier = Modifier.height(60.dp).width(300.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Text("Apri Manuale Completo", fontSize = 18.sp, color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Si aprirà con il programma predefinito del tuo computer", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}
