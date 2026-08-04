package com.michelelopsdev.gfa

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.michelelopsdev.gfa.auth.GmailAuthManager
import com.michelelopsdev.gfa.data.local.DatabaseFactory
import com.michelelopsdev.gfa.domain.ExcelExporterService
import com.michelelopsdev.gfa.domain.GmailFetcherService
import com.michelelopsdev.gfa.ui.RealtimeChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
@Preview
fun App() {
    var isExtracting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Pronto.") }
    val coroutineScope = rememberCoroutineScope()

    var fetcherService by remember { mutableStateOf<GmailFetcherService?>(null) }
    val speedHistory = remember { mutableStateListOf<Int>() }
    var currentSpeed by remember { mutableStateOf(0) }
    var totalProcessed by remember { mutableStateOf(0) }
    var totalInbox by remember { mutableStateOf(0) }
    var currentSubject by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var lastSessionDate by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(fetcherService) {
        fetcherService?.stats?.collect { stats ->
            totalProcessed = stats.totalProcessed
            currentSpeed = stats.speedPerSecond
            totalInbox = stats.totalInboxMessages
            currentSubject = stats.currentEmailSubject
            currentDate = stats.lastEmailDate
            lastSessionDate = stats.lastSessionDate
            progressPercent = stats.progressPercent
            
            if (speedHistory.size > 50) speedHistory.removeAt(0)
            speedHistory.add(currentSpeed)
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
            
            // HEADER - Controlli orizzontali in alto
            Surface(
                color = Color(0xFF1E1E1E),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Mission Control - GFA",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        statusMessage,
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gruppo Estrazione (Fase 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (isExtracting) return@Button
                                isExtracting = true
                                isPaused = false
                                statusMessage = "Estrazione in corso..."
                                
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val database = DatabaseFactory.createDatabase()
                                            val authManager = GmailAuthManager()
                                            val gmailService = authManager.getGmailService()
                                            
                                            val fetcher = GmailFetcherService(gmailService, database.emailDao())
                                            fetcherService = fetcher
                                            fetcher.extractData()
                                        }
                                        statusMessage = "Estrazione completata!"
                                    } catch (e: Exception) {
                                        statusMessage = "Errore: ${e.localizedMessage}"
                                        e.printStackTrace()
                                    } finally {
                                        isExtracting = false
                                    }
                                }
                            }, enabled = !isExtracting) {
                                Text("Avvia (Fase 1)")
                            }

                            if (isExtracting) {
                                Button(
                                    onClick = {
                                        isPaused = !isPaused
                                        fetcherService?.isPaused = isPaused
                                        statusMessage = if (isPaused) "In Pausa..." else "Estrazione in corso..."
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                ) {
                                    Text(if (isPaused) "Riprendi" else "Pausa", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        fetcherService?.isRunning = false
                                        isExtracting = false
                                        statusMessage = "Interrotto."
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                                ) {
                                    Text("Ferma", color = Color.White)
                                }
                            }
                        }

                        // Export Excel
                        Button(
                            onClick = {
                                statusMessage = "Esportazione in Excel in corso..."
                                coroutineScope.launch {
                                    try {
                                        val savedPath = ExcelExporterService().exportToExcel()
                                        statusMessage = if (savedPath != null) {
                                            "Export completato in: $savedPath"
                                        } else {
                                            "Errore: Nessun dato da esportare."
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "Errore Export: ${e.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Esporta in Excel", color = Color.White)
                        }

                        // Gemini IA (Fase 2)
                        Button(
                            onClick = {
                                statusMessage = "Analisi Gemini in corso..."
                                coroutineScope.launch {
                                    try {
                                        com.michelelopsdev.gfa.domain.GeminiAnalyzerService().generateRules()
                                        statusMessage = "Regole generate con successo! (Fase 2)"
                                    } catch (e: Exception) {
                                        statusMessage = "Errore Gemini: ${e.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                        ) {
                            Text("Genera Regole IA", color = Color.White)
                        }

                        // Motore Triage (Fase 3)
                        Button(
                            onClick = {
                                statusMessage = "Triage in corso..."
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val database = DatabaseFactory.createDatabase()
                                            val authManager = GmailAuthManager()
                                            val gmailService = authManager.getGmailService()
                                            val triageService = com.michelelopsdev.gfa.domain.GmailTriageService(gmailService, database.emailDao())
                                            triageService.startTriage()
                                        }
                                        statusMessage = "Triage completato con successo!"
                                    } catch (e: Exception) {
                                        statusMessage = "Errore Triage: ${e.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) {
                            Text("Avvia Triage", color = Color.White)
                        }
                    }
                }
            }

            // MIDDLE - Indicatori KPI e Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // KPIs
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        modifier = Modifier.width(220.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Email Elaborate / Totali", color = Color.LightGray, fontSize = 14.sp)
                            val totalStr = if (totalInbox > 0) " / $totalInbox" else ""
                            Text("$totalProcessed$totalStr", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        modifier = Modifier.width(180.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Velocità (msg/s)", color = Color.LightGray, fontSize = 14.sp)
                            Text("$currentSpeed", color = Color(0xFF00E5FF), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Progress Bar e Info Email Corrente
                Column(
                    modifier = Modifier.weight(1f).padding(start = 24.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val displayPercent = (progressPercent * 100).roundToInt()
                    // La barra diminuisce: 1.0f - progressPercent
                    LinearProgressIndicator(
                        progress = 1.0f - progressPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = Color(0xFFE91E63),
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Da scaricare: ${100 - displayPercent}%", 
                        color = Color.LightGray, 
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (currentSubject.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Scansionando: $currentSubject",
                            color = Color(0xFF00E5FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Data Corrente: $currentDate",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    if (lastSessionDate.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ultima data elaborata in prec. sessione: $lastSessionDate",
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // BOTTOM - Grafico espanso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                RealtimeChart(
                    dataPoints = speedHistory,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gmail Filter Advanced (GFA)"
    ) {
        App()
    }
}
