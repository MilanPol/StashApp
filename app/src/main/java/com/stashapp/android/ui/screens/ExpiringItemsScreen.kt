package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.InventoryItemCard
import com.stashapp.shared.domain.InventoryEntryRepository
import com.stashapp.shared.domain.InventoryEntry
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiringItemsScreen(
    entryRepository: InventoryEntryRepository,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val entries by entryRepository.getAllEntries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    val expiringSoon = remember(entries) {
        val now = Instant.now()
        entries.filter { entry -> 
            val alertAt = entry.alertAt
            alertAt != null && alertAt.isBefore(now) 
        }.sortedBy { it.expirationDate?.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_expiring_soon)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { padding ->
        if (expiringSoon.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(stringResource(R.string.no_items_found))
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(expiringSoon, key = { it.id }) { entry ->
                    InventoryItemCard(
                        entry = entry,
                        onUpdate = { scope.launch { entryRepository.updateEntry(it) } },
                        onDelete = { scope.launch { entryRepository.removeEntry(entry.id) } },
                        onDetailsClick = { onNavigateToDetails(entry.id) }
                    )
                }
            }
        }
    }
}
