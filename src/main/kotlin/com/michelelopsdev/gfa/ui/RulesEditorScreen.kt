package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelelopsdev.gfa.data.model.Rule
import com.michelelopsdev.gfa.data.model.RuleConfig
import com.michelelopsdev.gfa.data.model.TriageAction
import com.michelelopsdev.gfa.auth.GmailAuthManager
import com.michelelopsdev.gfa.domain.GmailFilterExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesEditorScreen(
    isDarkTheme: Boolean,
    onSetStatusMessage: (String) -> Unit,
    coroutineScope: CoroutineScope
) {
    var rules by remember { mutableStateOf<List<Rule>>(emptyList()) }
    val rulesFile = File(System.getProperty("user.home"), ".gfa/rules.json")
    val json = Json { ignoreUnknownKeys = true }

    var selectedType by remember { mutableStateOf("Dominio") }
    val types = listOf("Dominio", "Mittente Esatto", "Parola Chiave")
    var expanded by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf("") }

    var isExporting by remember { mutableStateOf(false) }

    fun loadRules() {
        if (rulesFile.exists()) {
            try {
                val text = rulesFile.readText()
                val config = json.decodeFromString(RuleConfig.serializer(), text)
                rules = config.rules
            } catch (e: Exception) {
                onSetStatusMessage("Errore caricamento regole: ${e.message}")
            }
        } else {
            rules = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        loadRules()
    }

    fun saveRules(newRules: List<Rule>) {
        try {
            val config = RuleConfig(newRules)
            rulesFile.writeText(json.encodeToString(RuleConfig.serializer(), config))
            rules = newRules
            onSetStatusMessage("Regole aggiornate e salvate!")
        } catch (e: Exception) {
            onSetStatusMessage("Errore salvataggio regole: ${e.message}")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fase 3: Editor Regole Manuali", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        AiRequestBanner()
        
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aggiungi nuova regola (TRASH)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedType,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().width(180.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            types.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        label = { Text("Valore da bloccare") },
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (inputValue.isBlank()) {
                                onSetStatusMessage("Inserisci un valore valido.")
                                return@Button
                            }
                            
                            val id = "manual_${UUID.randomUUID().toString().take(8)}"
                            val newRule = when (selectedType) {
                                "Dominio" -> Rule(id = id, patternMittente = ".*@${Regex.escape(inputValue)}.*", action = TriageAction.TRASH)
                                "Mittente Esatto" -> Rule(id = id, patternMittente = Regex.escape(inputValue), action = TriageAction.TRASH)
                                "Parola Chiave" -> Rule(id = id, patternOggetto = ".*${Regex.escape(inputValue)}.*", action = TriageAction.TRASH)
                                else -> return@Button
                            }
                            
                            val updatedRules = rules + newRule
                            saveRules(updatedRules)
                            inputValue = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Aggiungi", color = Color.White)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Regole Attive/Inattive (${rules.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { 
                    val updated = rules.map { it.copy(isActive = true) }
                    saveRules(updated) 
                }) {
                    Text("Seleziona Tutte")
                }
                
                Button(onClick = { 
                    val updated = rules.map { it.copy(isActive = false) }
                    saveRules(updated) 
                }) {
                    Text("Deseleziona Tutte")
                }
                
                Button(onClick = { loadRules() }) {
                    Text("Ricarica da File")
                }
                
                Button(
                    onClick = {
                        if (isExporting) return@Button
                        isExporting = true
                        onSetStatusMessage("Esportazione filtri su Gmail in corso...")
                        
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val authManager = GmailAuthManager()
                                    val gmailService = authManager.getGmailService()
                                    val exporter = GmailFilterExporter(gmailService)
                                    exporter.exportRulesToFilters(rules)
                                }
                                
                                val msg = "Esportazione completata: ${result.success} creati, ${result.errors} falliti o saltati."
                                onSetStatusMessage(msg)
                            } catch (e: Exception) {
                                onSetStatusMessage("Errore critico esportazione: ${e.message}")
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting && rules.any { it.isActive },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)) // Viola
                ) {
                    Text(if (isExporting) "Esportazione..." else "Esporta su Gmail (Definitivo)", color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(modifier = Modifier.fillMaxSize()) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
            androidx.compose.foundation.lazy.LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 16.dp)) {
                items(rules) { rule ->
                    val alpha = if (rule.isActive) 1f else 0.5f
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox Attivazione
                            Checkbox(
                                checked = rule.isActive,
                                onCheckedChange = { checked ->
                                    val updatedRules = rules.map { 
                                        if (it.id == rule.id) it.copy(isActive = checked) else it 
                                    }
                                    saveRules(updatedRules)
                                },
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.id, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                                if (rule.patternMittente != null) {
                                    Text("Mittente: ${rule.patternMittente}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), fontSize = 12.sp)
                                }
                                if (rule.patternOggetto != null) {
                                    Text("Oggetto: ${rule.patternOggetto}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), fontSize = 12.sp)
                                }
                                Text(
                                    text = "Azione: ${rule.action.name}" + if (!rule.isActive) " (Disattivata)" else "", 
                                    color = if (!rule.isActive) Color.Gray else if (rule.action == TriageAction.TRASH) Color.Red else Color(0xFF4CAF50), 
                                    fontSize = 12.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    val updatedRules = rules.filter { it.id != rule.id }
                                    saveRules(updatedRules)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Elimina", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }
            }
            
            VerticalScrollbarWithArrows(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}
