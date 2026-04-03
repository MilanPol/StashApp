package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.shared.domain.InventoryRepository
import com.stashapp.shared.domain.InventoryEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    repository: InventoryRepository,
    itemId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToGroup: (type: String, id: String) -> Unit = { _, _ -> }
) {
    var entry by remember { mutableStateOf<InventoryEntry?>(null) }
    val categories by repository.getCategories().collectAsState(initial = emptyList())
    val locations by repository.getStorageLocations().collectAsState(initial = emptyList())

    LaunchedEffect(itemId) {
        if (itemId != null) {
            entry = repository.getEntryById(itemId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_item_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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

                DetailRow(
                    stringResource(R.string.label_quantity),
                    "${currentEntry.quantity.amount} ${currentEntry.quantity.unit.name.lowercase()}"
                )
                DetailRow(
                    stringResource(R.string.label_status),
                    if (currentEntry.isOpen) stringResource(R.string.status_opened) else stringResource(R.string.status_sealed)
                )
                
                val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

                val isExpired = currentEntry.expirationDate?.isExpired(LocalDate.now()) == true
                DetailRow(
                    stringResource(R.string.label_expiration_date),
                    currentEntry.expirationDate?.date?.format(dateFormatter) ?: stringResource(R.string.no_expiration_set),
                    valueColor = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (currentEntry.openedAt != null) {
                    DetailRow(stringResource(R.string.label_opened_on), dateTimeFormatter.format(currentEntry.openedAt))
                }
                
                if (currentEntry.storageLocationId != null) {
                    val loc = locations.find { it.id == currentEntry.storageLocationId }
                    if (loc != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(R.string.label_location), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onNavigateToGroup("LOCATION", loc.id) }) {
                                Text(text = "${loc.icon} ${loc.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                
                if (currentEntry.categoryId != null) {
                    val cat = categories.find { it.id == currentEntry.categoryId }
                    if (cat != null) {
                        DetailRow(stringResource(R.string.label_category), "${cat.icon} ${cat.name}")
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
