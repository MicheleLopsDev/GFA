package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelelopsdev.gfa.data.model.GeminiSessionLog
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun AiRequestBanner() {
    var log by remember { mutableStateOf<GeminiSessionLog?>(null) }
    LaunchedEffect(Unit) {
        val logFile = File(System.getProperty("user.home"), ".gfa/last_gemini_run.json")
        if (logFile.exists()) {
            try {
                val jsonParser = Json { ignoreUnknownKeys = true }
                log = jsonParser.decodeFromString<GeminiSessionLog>(logFile.readText())
            } catch (e: Exception) {}
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (log != null) {
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date(log!!.timestamp))
                Text(
                    "Ultima esecuzione IA: $dateStr", 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Prompt usato per i filtri: ${log!!.request}", 
                    color = MaterialTheme.colorScheme.onPrimaryContainer, 
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    "Nessuna esecuzione IA precedente", 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Torna nella Fase 2 e usa 'Genera Regole IA (Trash)' per generare nuovi filtri intelligenti.", 
                    color = MaterialTheme.colorScheme.onPrimaryContainer, 
                    fontSize = 12.sp
                )
            }
        }
    }
}
