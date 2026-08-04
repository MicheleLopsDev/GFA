package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelelopsdev.gfa.data.model.EmailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

fun parseEmailDate(dateStr: String): LocalDate? {
    try {
        return java.time.ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate()
    } catch(e: Exception) {}
    try {
        return java.time.LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate()
    } catch(e: Exception) {}
    return null
}

fun parseFilterDate(dateStr: String): LocalDate? {
    try {
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch(e: Exception) { return null }
}

@Composable
fun PreviewDashboard(
    emails: List<EmailData>,
    selectedEmails: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    sortColumn: String,
    onSortChanged: (String) -> Unit,
    sortAscending: Boolean,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    pageSize: Int,
    onPageSizeChanged: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    isDarkTheme: Boolean,
    isExecuting: Boolean,
    progressMsg: String,
    coroutineScope: CoroutineScope
) {
    var filterTitolo by remember { mutableStateOf("") }
    var filterMittente by remember { mutableStateOf("") }
    var filterDataDa by remember { mutableStateOf("") }
    var filterDataA by remember { mutableStateOf("") }
    var selectedEmailBody by remember { mutableStateOf<EmailData?>(null) }

    val filteredEmails = remember(emails, filterTitolo, filterMittente, filterDataDa, filterDataA) {
        val daDate = parseFilterDate(filterDataDa)
        val aDate = parseFilterDate(filterDataA)
        
        emails.filter { email ->
            val matchesTitolo = if (filterTitolo.isBlank()) true else email.titolo.contains(filterTitolo, ignoreCase = true)
            val matchesMittente = if (filterMittente.isBlank()) true else email.da.contains(filterMittente, ignoreCase = true)
            var matchesDate = true
            if (daDate != null || aDate != null) {
                val emailDate = parseEmailDate(email.data)
                if (emailDate != null) {
                    if (daDate != null && emailDate.isBefore(daDate)) matchesDate = false
                    if (aDate != null && emailDate.isAfter(aDate)) matchesDate = false
                }
            }
            matchesTitolo && matchesMittente && matchesDate
        }
    }

    val topSenders = remember(filteredEmails) {
        filteredEmails.groupingBy { 
            val emailPart = if (it.da.contains("<")) it.da.substringAfter("<").substringBefore(">") else it.da
            emailPart.trim().lowercase()
        }.eachCount().entries.sortedByDescending { it.value }.take(10)
    }

    val sortedEmails = remember(filteredEmails, sortColumn, sortAscending) {
        val comparator = when (sortColumn) {
            "ID" -> compareBy<EmailData> { it.id }
            "Data" -> compareBy { it.data }
            "Mittente" -> compareBy { it.da }
            "Titolo" -> compareBy { it.titolo }
            else -> compareBy { it.data }
        }
        if (sortAscending) filteredEmails.sortedWith(comparator) else filteredEmails.sortedWith(comparator.reversed())
    }

    val totalPages = (sortedEmails.size + pageSize - 1) / pageSize
    val safeCurrentPage = currentPage.coerceIn(0, maxOf(0, totalPages - 1))
    
    val pagedEmails = sortedEmails.drop(safeCurrentPage * pageSize).take(pageSize)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Anteprima Pulizia (Trash)", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${filteredEmails.size} email trovate", color = Color(0xFFE91E63), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Barra di Ricerca
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = filterMittente, onValueChange = { filterMittente = it; onPageChanged(0) }, label = { Text("Cerca Mittente") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = filterTitolo, onValueChange = { filterTitolo = it; onPageChanged(0) }, label = { Text("Cerca Titolo") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = filterDataDa, onValueChange = { filterDataDa = it; onPageChanged(0) }, label = { Text("Data Da (gg/mm/aaaa)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = filterDataA, onValueChange = { filterDataA = it; onPageChanged(0) }, label = { Text("Data A (gg/mm/aaaa)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Top 10 Spammers
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Top 10 Spammer (Indirizzi con più email)", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val half = (topSenders.size + 1) / 2
                    Column(modifier = Modifier.weight(1f)) {
                        topSenders.take(half).forEach { (sender, count) ->
                            Text("$sender : $count", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        topSenders.drop(half).forEach { (sender, count) ->
                            Text("$sender : $count", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onConfirm,
                    enabled = !isExecuting && selectedEmails.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text(if (isExecuting) "Eliminazione..." else "Conferma Pulizia Selezionate (${selectedEmails.size})", color = Color.White)
                }
                Button(
                    onClick = onCancel,
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))
                ) {
                    Text("Annulla", color = Color.White)
                }
                
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFE91E63))
                    Text(progressMsg, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                }
            }
            
            // Seleziona tutto / Deseleziona tutto
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedEmails.containsAll(filteredEmails.map { it.id }),
                    onCheckedChange = { checked ->
                        if (checked) onSelectionChanged(selectedEmails + filteredEmails.map { it.id }.toSet())
                        else onSelectionChanged(selectedEmails - filteredEmails.map { it.id }.toSet())
                    }
                )
                Text("Seleziona Tutte (Viste)", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pagination Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Email per pagina:", color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { onPageSizeChanged(50) }, colors = ButtonDefaults.buttonColors(containerColor = if (pageSize == 50) Color(0xFF00E5FF) else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (pageSize == 50) Color.Black else MaterialTheme.colorScheme.onBackground)) { Text("50") }
                Button(onClick = { onPageSizeChanged(100) }, colors = ButtonDefaults.buttonColors(containerColor = if (pageSize == 100) Color(0xFF00E5FF) else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (pageSize == 100) Color.Black else MaterialTheme.colorScheme.onBackground)) { Text("100") }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPageChanged(0) }, enabled = safeCurrentPage > 0) { Text("|<<") }
                Button(onClick = { onPageChanged(safeCurrentPage - 10) }, enabled = safeCurrentPage > 0) { Text("<< -10") }
                Button(onClick = { onPageChanged(safeCurrentPage - 1) }, enabled = safeCurrentPage > 0) { Text("< Prec") }
                Text("Pag ${safeCurrentPage + 1} di ${maxOf(1, totalPages)}", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Button(onClick = { onPageChanged(safeCurrentPage + 1) }, enabled = safeCurrentPage < totalPages - 1) { Text("Succ >") }
                Button(onClick = { onPageChanged(safeCurrentPage + 10) }, enabled = safeCurrentPage < totalPages - 1) { Text("+10 >>") }
                Button(onClick = { onPageChanged(totalPages - 1) }, enabled = safeCurrentPage < totalPages - 1) { Text(">>|") }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Table Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(48.dp)) // Checkbox space
                Spacer(modifier = Modifier.width(32.dp)) // Attach icon space
                TableHeader("ID", sortColumn, sortAscending, onSortChanged, Modifier.weight(1f))
                TableHeader("Data", sortColumn, sortAscending, onSortChanged, Modifier.weight(1.5f))
                TableHeader("Mittente", sortColumn, sortAscending, onSortChanged, Modifier.weight(2f))
                TableHeader("Titolo", sortColumn, sortAscending, onSortChanged, Modifier.weight(3f))
            }
        }

        // Table Body
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface)
        ) {
            items(pagedEmails) { email ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedEmails.contains(email.id),
                        onCheckedChange = { checked ->
                            val newSet = if (checked) selectedEmails + email.id else selectedEmails - email.id
                            onSelectionChanged(newSet)
                        }
                    )
                    if (email.haAllegati) {
                        Text("📎", modifier = Modifier.width(32.dp))
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                    Text(email.id, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email.data, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email.da, modifier = Modifier.weight(2f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email.titolo, modifier = Modifier.weight(3f).clickable { selectedEmailBody = email }, color = Color(0xFF00E5FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
    
    if (selectedEmailBody != null) {
        AlertDialog(
            onDismissRequest = { selectedEmailBody = null },
            title = { Text(selectedEmailBody!!.titolo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(modifier = Modifier.widthIn(max = 600.dp)) {
                    Text("Da: ${selectedEmailBody!!.da}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Data: ${selectedEmailBody!!.data}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    if (selectedEmailBody!!.haAllegati) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Allegati: ${selectedEmailBody!!.nomiAllegati.joinToString()}", color = Color(0xFFE91E63))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        Text(selectedEmailBody!!.testo, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedEmailBody = null }) {
                    Text("Chiudi")
                }
            }
        )
    }
}

@Composable
fun TableHeader(
    title: String,
    currentSort: String,
    sortAscending: Boolean,
    onSortChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSorted = title == currentSort
    val sortIcon = if (isSorted) {
        if (sortAscending) " ▲" else " ▼"
    } else ""
    
    Text(
        text = title + sortIcon,
        modifier = modifier.clickable { onSortChanged(title) }.padding(4.dp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}
