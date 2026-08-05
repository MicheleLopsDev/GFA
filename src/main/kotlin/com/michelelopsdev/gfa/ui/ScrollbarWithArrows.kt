package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun VerticalScrollbarWithArrows(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = modifier.width(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { 
                coroutineScope.launch { 
                    val currentIdx = listState.firstVisibleItemIndex
                    if (currentIdx > 0) {
                        listState.animateScrollToItem(currentIdx - 1)
                    }
                } 
            },
            modifier = Modifier.size(16.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground)
        ) {
            Text("▲", fontSize = 8.sp)
        }
        
        androidx.compose.foundation.VerticalScrollbar(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            adapter = androidx.compose.foundation.rememberScrollbarAdapter(scrollState = listState)
        )
        
        Button(
            onClick = { 
                coroutineScope.launch { 
                    val currentIdx = listState.firstVisibleItemIndex
                    listState.animateScrollToItem(currentIdx + 1)
                } 
            },
            modifier = Modifier.size(16.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground)
        ) {
            Text("▼", fontSize = 8.sp)
        }
    }
}
