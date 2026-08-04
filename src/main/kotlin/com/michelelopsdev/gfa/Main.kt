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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            containerColor = if (isSelected) Color(0xFF00E5FF) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
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
    
    // Globale: Account Utente
    var userEmail by remember { mutableStateOf("Caricamento...") }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val authManager = GmailAuthManager()
                val profile = authManager.getGmailService().users().getProfile("me").execute()
                userEmail = profile.emailAddress ?: "Sconosciuta"
            } catch (e: Exception) {
                userEmail = "Non connesso"
            }
        }
    }

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

    var isDarkTheme by remember { mutableStateOf(true) }

    val colors = if (isDarkTheme) {
        darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF333333),
            onBackground = Color.White,
            onSurface = Color.LightGray
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF5F5F5),
            surface = Color.White,
            surfaceVariant = Color(0xFFE0E0E0),
            onBackground = Color.Black,
            onSurface = Color.DarkGray
        )
    }

    MaterialTheme(colorScheme = colors) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            
            // HEADER - Controlli orizzontali in alto
            Surface(
                color = MaterialTheme.colorScheme.surface,
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
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Account: $userEmail", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                            Button(onClick = { isDarkTheme = !isDarkTheme }) {
                                Text(if (isDarkTheme) "☀️ Chiaro" else "🌙 Scuro")
                            }
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
                        isDarkTheme = isDarkTheme,
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.width(220.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Email Elaborate / Totali", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        val totalStr = if (totalInbox > 0) " / $totalInbox" else ""
                        Text("$totalProcessed$totalStr", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.width(180.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Velocità (msg/s)", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
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
                    color = MaterialTheme.colorScheme.onSurface, 
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
    isDarkTheme: Boolean,
    onSetStatusMessage: (String) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    var requestLog by remember { mutableStateOf("") }
    var responseLog by remember { mutableStateOf("") }
    var successfulModel by remember { mutableStateOf<String?>(null) }
    var failedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val allAvailableModels = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.6-flash", "gemini-2.0-flash")
    var selectedModels by remember { mutableStateOf(allAvailableModels) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentProgressMsg by remember { mutableStateOf("") }

    // Preview Mode States
    var previewEmails by remember { mutableStateOf<List<com.michelelopsdev.gfa.data.model.EmailData>?>(null) }
    var selectedEmails by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortColumn by remember { mutableStateOf("Data") }
    var sortAscending by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(50) }
    var isExecutingTrash by remember { mutableStateOf(false) }
    var trashProgressMsg by remember { mutableStateOf("") }

    if (previewEmails != null) {
        com.michelelopsdev.gfa.ui.PreviewDashboard(
            emails = previewEmails!!,
            selectedEmails = selectedEmails,
            onSelectionChanged = { selectedEmails = it },
            sortColumn = sortColumn,
            onSortChanged = { col ->
                if (sortColumn == col) sortAscending = !sortAscending
                else { sortColumn = col; sortAscending = true }
            },
            sortAscending = sortAscending,
            currentPage = currentPage,
            onPageChanged = { currentPage = it },
            pageSize = pageSize,
            onPageSizeChanged = { pageSize = it; currentPage = 0 },
            onConfirm = {
                isExecutingTrash = true
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val database = DatabaseFactory.createDatabase()
                            val authManager = GmailAuthManager()
                            val gmailService = authManager.getGmailService()
                            val triageService = com.michelelopsdev.gfa.domain.GmailTriageService(gmailService, database.emailDao())
                            
                            triageService.executeTrash(selectedEmails.toList()) { current, total ->
                                trashProgressMsg = "$current / $total rimosse"
                            }
                        }
                        onSetStatusMessage("Pulizia completata con successo! ${selectedEmails.size} rimosse.")
                        previewEmails = null
                        selectedEmails = emptySet()
                    } catch (e: Exception) {
                        onSetStatusMessage("Errore Pulizia: ${e.message}")
                    } finally {
                        isExecutingTrash = false
                    }
                }
            },
            onCancel = {
                previewEmails = null
                selectedEmails = emptySet()
            },
            isDarkTheme = isDarkTheme,
            isExecuting = isExecutingTrash,
            progressMsg = trashProgressMsg,
            coroutineScope = coroutineScope
        )
        return // Esce e renderizza solo l'anteprima
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fase 2: Generazione Regole di Pulizia", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Selezione Modelli
        Text("Modelli di Fallback (ordine di esecuzione):", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            allAvailableModels.forEach { model ->
                val isSelected = selectedModels.contains(model)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedModels = selectedModels + model
                            } else {
                                if (selectedModels.size > 1) {
                                    selectedModels = selectedModels - model
                                }
                            }
                        }
                    )
                    Text(model, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (isGenerating) return@Button
                    isGenerating = true
                    currentProgressMsg = "Inizializzazione..."
                    onSetStatusMessage("Analisi Gemini in corso (Fase 2)...")
                    coroutineScope.launch {
                        try {
                            com.michelelopsdev.gfa.domain.GeminiAnalyzerService().generateRules(
                                modelsToTry = selectedModels,
                                onProgress = { currentProgressMsg = it },
                                onLog = { req, res, sModel, fModels ->
                                    requestLog = req
                                    responseLog = res
                                    successfulModel = sModel
                                    failedModels = fModels
                                }
                            )
                            onSetStatusMessage("Regole generate con successo! (Fase 2)")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore Gemini: ${e.message}")
                            currentProgressMsg = "Errore!"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text(if (isGenerating) "Generazione in corso..." else "Genera Regole IA (Trash)", color = Color.White)
            }
            
            Button(
                onClick = {
                    onSetStatusMessage("Calcolo anteprima in corso...")
                    coroutineScope.launch {
                        try {
                            val emailsToTrash = withContext(Dispatchers.IO) {
                                val database = DatabaseFactory.createDatabase()
                                val authManager = GmailAuthManager()
                                val gmailService = authManager.getGmailService()
                                val triageService = com.michelelopsdev.gfa.domain.GmailTriageService(gmailService, database.emailDao())
                                triageService.simulateTriage()
                            }
                            previewEmails = emailsToTrash
                            selectedEmails = emailsToTrash.map { it.id }.toSet()
                            currentPage = 0
                            onSetStatusMessage("Anteprima caricata: ${emailsToTrash.size} email individuate.")
                        } catch (e: Exception) {
                            onSetStatusMessage("Errore anteprima: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
                Text("Avvia Pulizia (Trash)", color = Color.White)
            }

            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF00E5FF))
                Text(currentProgressMsg, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Pannello Info Modelli Fallback
        if (successfulModel != null || failedModels.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (failedModels.isNotEmpty()) {
                        Text("Modelli congestionati (scartati): ${failedModels.joinToString(", ")}", color = Color(0xFFE91E63), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (successfulModel != null) {
                        Text("Modello che ha risposto con successo: $successfulModel", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Request Log
            Column(modifier = Modifier.weight(1f)) {
                Text("Dati Inviati a Gemini (Request)", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = requestLog.ifEmpty { "Nessuna richiesta inviata ancora..." },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
            }

            // Response Log
            Column(modifier = Modifier.weight(1f)) {
                Text("Risposta da Gemini (Response)", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = responseLog.ifEmpty { "In attesa di risposta..." },
                        color = if (isDarkTheme) Color(0xFF00E5FF) else Color(0xFF00838F),
                        fontSize = 12.sp,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
            }
        }
    }
}

@Composable
fun BackupScreen(
    onSetStatusMessage: (String) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fase 4: Backup Allegati e Etichette", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Le funzioni di Backup e analisi delle rimanenze saranno implementate qui.", color = MaterialTheme.colorScheme.onSurface)
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
