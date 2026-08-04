package com.michelelopsdev.gfa

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(fetcherService) {
        fetcherService?.stats?.collect { stats ->
            totalProcessed = stats.totalProcessed
            currentSpeed = stats.speedPerSecond
            if (speedHistory.size > 50) speedHistory.removeAt(0)
            speedHistory.add(currentSpeed)
        }
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Panel (Controls)
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(statusMessage, modifier = Modifier.padding(bottom = 16.dp))
                
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
                        Button(onClick = {
                            isPaused = !isPaused
                            fetcherService?.isPaused = isPaused
                            statusMessage = if (isPaused) "In Pausa..." else "Estrazione in corso..."
                        }) {
                            Text(if (isPaused) "Riprendi" else "Pausa")
                        }

                        Button(onClick = {
                            fetcherService?.isRunning = false
                            isExtracting = false
                            statusMessage = "Interrotto."
                        }) {
                            Text("Ferma")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    statusMessage = "Esportazione in Excel in corso..."
                    coroutineScope.launch {
                        try {
                            ExcelExporterService().exportToExcel()
                            statusMessage = "Esportazione Excel completata sul Desktop!"
                        } catch (e: Exception) {
                            statusMessage = "Errore Export: ${e.message}"
                        }
                    }
                }) {
                    Text("Esporta in Excel")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    statusMessage = "Analisi Gemini in corso..."
                    coroutineScope.launch {
                        try {
                            com.michelelopsdev.gfa.domain.GeminiAnalyzerService().generateRules()
                            statusMessage = "Regole generate con successo! (Fase 2)"
                        } catch (e: Exception) {
                            statusMessage = "Errore Gemini: ${e.message}"
                        }
                    }
                }) {
                    Text("Genera Regole IA (Fase 2)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
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
                }) {
                    Text("Motore di Triage (Fase 3)")
                }
            }

            // Right Panel (Realtime Dashboard)
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Dashboard Estrazione", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Email Elaborate", fontSize = 14.sp)
                        Text("$totalProcessed", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Velocità (msg/s)", fontSize = 14.sp)
                        Text("$currentSpeed", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Grafico Velocità di Acquisizione", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                RealtimeChart(dataPoints = speedHistory, modifier = Modifier.fillMaxWidth().height(200.dp))
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Gmail Filter Advanced (GFA)") {
        App()
    }
}

