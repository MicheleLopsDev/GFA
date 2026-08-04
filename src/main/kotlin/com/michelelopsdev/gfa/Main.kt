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

enum class Screen { EXTRACTION, CLEANUP, BACKUP }

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF333333),
            contentColor = if (isSelected) Color.Black else Color.White
        )
    ) {
        Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
@Preview
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.EXTRACTION) }
    
    // Shared Status Message
    var statusMessage by remember { mutableStateOf("Pronto.") }
    val coroutineScope = rememberCoroutineScope()

    // Extraction State
    var isExtracting by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val database = DatabaseFactory.createDatabase()
                val totalProcessedInit = database.emailDao().getProcessedCount()
                totalProcessed = totalProcessedInit
                
                // Leggi ultima data
                val outputDir = java.io.File(System.getProperty("user.home"), ".gfa/output")
                val existingFiles = outputDir.listFiles { _, name -> name.startsWith("emails_part_") && name.endsWith(".json") }
                if (existingFiles != null && existingFiles.isNotEmpty()) {
                    val maxPart = existingFiles.mapNotNull { 
                        it.name.substringAfter("emails_part_").substringBefore(".json").toIntOrNull() 
                    }.maxOrNull()
                    if (maxPart != null) {
                        val lastFile = java.io.File(outputDir, "emails_part_$maxPart.json")
                        if (lastFile.exists()) {
                            val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            val emailsInFile = jsonParser.decodeFromString<List<com.michelelopsdev.gfa.data.model.EmailData>>(lastFile.readText())
                            if (emailsInFile.isNotEmpty()) {
                                lastSessionDate = emailsInFile.last().data
                            }
                        }
                    }
                }
                
                // Auth & get total
                val authManager = GmailAuthManager()
                val gmailService = authManager.getGmailService()
                val profile = gmailService.users().getProfile("me").execute()
                if (profile != null) {
                    totalInbox = profile.messagesTotal ?: 0
                    if (totalInbox > 0) {
                        progressPercent = totalProcessedInit.toFloat() / totalInbox
                    }
                }
            } catch (e: Exception) {
                // error loading initial stats ignorato
            }
        }
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Gmail Filter Advanced (GFA)",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TabButton("1. Mission Control", currentScreen == Screen.EXTRACTION) { currentScreen = Screen.EXTRACTION }
                            TabButton("2. Pulizia (Trash)", currentScreen == Screen.CLEANUP) { currentScreen = Screen.CLEANUP }
                            TabButton("3. Backup (Allegati)", currentScreen == Screen.BACKUP) { currentScreen = Screen.BACKUP }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        statusMessage,
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    Screen.EXTRACTION -> ExtractionScreen(
                        isExtracting = isExtracting,
                        isPaused = isPaused,
                        totalProcessed = totalProcessed,
                        totalInbox = totalInbox,
                        currentSpeed = currentSpeed,
                        currentSubject = currentSubject,
                        currentDate = currentDate,
                        lastSessionDate = lastSessionDate,
                        progressPercent = progressPercent,
                        speedHistory = speedHistory,
                        onSetExtracting = { isExtracting = it },
                        onSetPaused = { isPaused = it },
                        onSetStatusMessage = { statusMessage = it },
                        onSetFetcherService = { fetcherService = it },
                        fetcherService = fetcherService,
                        coroutineScope = coroutineScope,
                        onClearData = {
                            totalProcessed = 0
                            currentSpeed = 0
                            currentSubject = ""
                            currentDate = ""
                            lastSessionDate = ""
                            progressPercent = 0f
                        }
                    )
                    Screen.CLEANUP -> CleanupScreen(
                        onSetStatusMessage = { statusMessage = it },
                        coroutineScope = coroutineScope
                    )
                    Screen.BACKUP -> BackupScreen(
                        onSetStatusMessage = { statusMessage = it },
                        coroutineScope = coroutineScope
                    )
                }
            }
        }
    }
}

@Composable
fun ExtractionScreen(
    isExtracting: Boolean,
    isPaused: Boolean,
    totalProcessed: Int,
    totalInbox: Int,
    currentSpeed: Int,
    currentSubject: String,
    currentDate: String,
    lastSessionDate: String,
    progressPercent: Float,
    speedHistory: List<Int>,
    onSetExtracting: (Boolean) -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onSetStatusMessage: (String) -> Unit,
    onSetFetcherService: (GmailFetcherService?) -> Unit,
    fetcherService: GmailFetcherService?,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onClearData: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gruppo Estrazione (Fase 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (isExtracting) return@Button
                    onSetExtracting(true)
                    onSetPaused(false)
                    onSetStatusMessage("Estrazione in corso...")
                    
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val database = DatabaseFactory.createDatabase()
                                val authManager = GmailAuthManager()
                                val gmailService = authManager.getGmailService()
                                
                                val fetcher = GmailFetcherService(gmailService, database.emailDao())
                                onSetFetcherService(fetcher)
                                fetcher.extractData()
                            }
                            onSetStatusMessage("Estrazione completata!")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore: ${e.localizedMessage}")
                            e.printStackTrace()
                        } finally {
                            onSetExtracting(false)
                        }
                    }
                }, enabled = !isExtracting) {
                    Text("Avvia Estrazione (Fase 1)")
                }

                if (isExtracting) {
                    Button(
                        onClick = {
                            val newPaused = !isPaused
                            onSetPaused(newPaused)
                            fetcherService?.isPaused = newPaused
                            onSetStatusMessage(if (newPaused) "In Pausa..." else "Estrazione in corso...")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text(if (isPaused) "Riprendi" else "Pausa", color = Color.White)
                    }

                    Button(
                        onClick = {
                            fetcherService?.isRunning = false
                            onSetExtracting(false)
                            onSetStatusMessage("Interrotto.")
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
                    onSetStatusMessage("Esportazione in Excel in corso...")
                    coroutineScope.launch {
                        try {
                            val savedPath = ExcelExporterService().exportToExcel()
                            onSetStatusMessage(if (savedPath != null) "Export completato in: $savedPath" else "Errore: Nessun dato da esportare.")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore Export: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Esporta in Excel", color = Color.White)
            }

            // Pulsante Reset/Pulizia Dati
            Button(
                onClick = {
                    onSetStatusMessage("Pulizia dati in corso...")
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            fetcherService?.isRunning = false
                            onSetExtracting(false)
                            
                            val outputDir = java.io.File(System.getProperty("user.home"), ".gfa/output")
                            if (outputDir.exists()) outputDir.deleteRecursively()
                            
                            val dao = DatabaseFactory.createDatabase().emailDao()
                            dao.clearProcessedEmails()
                            dao.clearTriagedEmails()
                            
                            val exportFile = java.io.File(System.getProperty("user.dir"), "GFA_Export_Email.xlsx")
                            if (exportFile.exists()) exportFile.delete()
                            
                            onClearData()
                            onSetStatusMessage("Dati eliminati con successo! Pronto a ripartire da zero.")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore durante la pulizia: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
            ) {
                Text("Pulisci Dati", color = Color.White)
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
                LinearProgressIndicator(
                    progress = { 1.0f - progressPercent },
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

@Composable
fun CleanupScreen(
    onSetStatusMessage: (String) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fase 2: Generazione Regole di Pulizia (Trash)", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    onSetStatusMessage("Analisi Gemini in corso (Fase 2)...")
                    coroutineScope.launch {
                        try {
                            com.michelelopsdev.gfa.domain.GeminiAnalyzerService().generateRules()
                            onSetStatusMessage("Regole generate con successo! (Fase 2)")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore Gemini: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text("Genera Regole IA (Trash)", color = Color.White)
            }
            
            Button(
                onClick = {
                    onSetStatusMessage("Pulizia Triage in corso (Fase 3)...")
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val database = DatabaseFactory.createDatabase()
                                val authManager = GmailAuthManager()
                                val gmailService = authManager.getGmailService()
                                val triageService = com.michelelopsdev.gfa.domain.GmailTriageService(gmailService, database.emailDao())
                                triageService.startTriage()
                            }
                            onSetStatusMessage("Pulizia completata con successo!")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore Pulizia: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
                Text("Avvia Pulizia (Trash)", color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Anteprima Regole Generali", color = Color.LightGray, fontSize = 16.sp)
        // TODO: Aggiungere box con TextField per modificare rules.json
    }
}

@Composable
fun BackupScreen(
    onSetStatusMessage: (String) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fase 4: Backup Allegati e Etichette", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Le funzioni di Backup e analisi delle rimanenze saranno implementate qui.", color = Color.LightGray)
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
