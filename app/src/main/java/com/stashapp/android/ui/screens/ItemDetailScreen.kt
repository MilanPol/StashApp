package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stashapp.shared.data.InMemoryInventoryRepository
import com.stashapp.shared.domain.InventoryEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    repository: InMemoryInventoryRepository,
    itemId: String?,
    onNavigateBack: () -> Unit
) {
    var entry by remember { mutableStateOf<InventoryEntry?>(null) }
    val categories by repository.getCategories().collectAsState(initial = emptyList())

    LaunchedEffect(itemId) {
        if (itemId != null) {
            entry = repository.getEntryById(itemId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        val currentEntry = entry
        if (currentEntry == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = currentEntry.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))

                DetailRow("Quantity", "${currentEntry.quantity.amount} ${currentEntry.quantity.unit.name.lowercase()}")
                DetailRow("Status", if (currentEntry.isOpen) "Opened" else "Sealed")
                
                
                val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

                val isExpired = currentEntry.expirationDate?.isExpired(LocalDate.now()) == true
                DetailRow(
                    "Expiration Date", 
                    currentEntry.expirationDate?.date?.format(dateFormatter) ?: "No Expiration Set",
                    valueColor = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (currentEntry.openedAt != null) {
                    DetailRow("Opened On", dateTimeFormatter.format(currentEntry.openedAt))
                }
                
                if (currentEntry.categoryId != null) {
                    val cat = categories.find { it.id == currentEntry.categoryId }
                    if (cat != null) {
                        DetailRow("Category", "${cat.icon} ${cat.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
