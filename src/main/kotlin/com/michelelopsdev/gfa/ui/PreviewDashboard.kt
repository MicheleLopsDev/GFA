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
    val topSenders = remember(emails) {
        emails.groupingBy { 
            val emailPart = if (it.da.contains("<")) it.da.substringAfter("<").substringBefore(">") else it.da
            emailPart.trim().lowercase()
        }.eachCount().entries.sortedByDescending { it.value }.take(10)
    }

    val sortedEmails = remember(emails, sortColumn, sortAscending) {
        val comparator = when (sortColumn) {
            "ID" -> compareBy<EmailData> { it.id }
            "Data" -> compareBy { it.data }
            "Mittente" -> compareBy { it.da }
            "Titolo" -> compareBy { it.titolo }
            else -> compareBy { it.data }
        }
        if (sortAscending) emails.sortedWith(comparator) else emails.sortedWith(comparator.reversed())
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
            Text("${emails.size} email da eliminare", color = Color(0xFFE91E63), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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
                    checked = selectedEmails.size == emails.size,
                    onCheckedChange = { checked ->
                        if (checked) onSelectionChanged(emails.map { it.id }.toSet())
                        else onSelectionChanged(emptySet())
                    }
                )
                Text("Seleziona Tutte", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
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
                Button(onClick = { onPageChanged(safeCurrentPage - 1) }, enabled = safeCurrentPage > 0) { Text("Precedente") }
                Text("Pagina ${safeCurrentPage + 1} di $totalPages", color = MaterialTheme.colorScheme.onBackground)
                Button(onClick = { onPageChanged(safeCurrentPage + 1) }, enabled = safeCurrentPage < totalPages - 1) { Text("Successiva") }
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
                    Text(email.id, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email.data, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email.da, modifier = Modifier.weight(2f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email.titolo, modifier = Modifier.weight(3f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
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
